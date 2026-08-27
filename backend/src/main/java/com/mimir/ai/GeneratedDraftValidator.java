package com.mimir.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

import com.mimir.ai.TextGenerationGateway.DraftGenerationRequest;
import com.mimir.ai.TextGenerationGateway.GeneratedDraft;

@Component
public class GeneratedDraftValidator {

    private static final Pattern IMAGE_PLACEHOLDER = Pattern.compile(
            "\\{\\{IMAGE:(\\d+)\\}\\}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRICE = Pattern.compile("(?:₩\\s*(\\d[\\d,]*)|(\\d[\\d,]*)\\s*원)");
    private static final Pattern WAIT_DURATION = Pattern.compile("(\\d+)\\s*(분|시간)");
    private static final Pattern KOREAN_FULL_DATE = Pattern.compile(
            "(\\d{4})년\\s*(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");
    private static final Pattern ISO_DATE = Pattern.compile("(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})");
    private static final Pattern FRIENDLY_SERVICE = Pattern.compile("(?<!불)친절");
    private static final List<String> EXACT_CONTEXT_ONLY_CLAIMS = List.of(
            "맛있", "맛없", "달콤", "고소", "산미", "풍미", "식감",
            "서비스", "불친절", "추천", "만족", "아쉬", "좋", "훌륭", "아름답",
            "현대적", "고급", "편안", "따뜻", "분위기", "독특", "매력", "생기", "시선을 끌", "눈을 즐겁",
            "월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일",
            "오늘", "어제", "그제", "지난주", "이번 주");
    private static final List<List<String>> CONTEXT_ONLY_CLAIMS = List.of(
            List.of("웨이팅", "대기 시간", "기다렸", "기다린"),
            List.of("주문", "시켰"),
            List.of("재방문", "다시 방문", "또 가고"));

    public GeneratedDraft validate(DraftGenerationRequest request, GeneratedDraft draft) {
        String title = required(draft.title(), "Generated title is required.");
        String body = required(draft.body(), "Generated body is required.");
        if (title.length() > 200 || body.length() > 100_000) {
            throw new TextGenerationException("Generated draft exceeds the supported length.");
        }
        List<String> tags = draft.tags().stream()
                .map(this::normalizeTag)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        if (tags.size() > 30 || tags.stream().anyMatch(tag -> tag.length() > 50)) {
            throw new TextGenerationException("Generated tags exceed the supported boundary.");
        }
        requireOrderedPlaceholders(body, request.imageFacts().size());
        requireGroundedClaims(title + "\n" + body, request.visitContext());
        return new GeneratedDraft(title, body, tags);
    }

    private void requireOrderedPlaceholders(String body, int imageCount) {
        Matcher matcher = IMAGE_PLACEHOLDER.matcher(body);
        List<Integer> actual = new ArrayList<>();
        while (matcher.find()) {
            actual.add(Integer.parseInt(matcher.group(1)));
        }
        List<Integer> expected = IntStream.rangeClosed(1, imageCount).boxed().toList();
        if (!actual.equals(expected)) {
            throw new TextGenerationException("Generated image placeholders must appear exactly once in display order.");
        }
    }

    private void requireGroundedClaims(String output, String visitContext) {
        String normalizedOutput = output.toLowerCase(Locale.ROOT);
        String normalizedContext = visitContext == null ? "" : visitContext.toLowerCase(Locale.ROOT);
        requireTokensGrounded(
                normalizedOutput,
                normalizedContext,
                PRICE,
                matcher -> (matcher.group(1) != null ? matcher.group(1) : matcher.group(2)).replace(",", ""),
                "Generated price is not grounded in user context.");
        requireDatesGrounded(normalizedOutput, normalizedContext);
        requireTokensGrounded(
                normalizedOutput,
                normalizedContext,
                FRIENDLY_SERVICE,
                Matcher::group,
                "Generated personal experience is not grounded in user context.");
        if (containsAny(normalizedOutput, CONTEXT_ONLY_CLAIMS.getFirst())) {
            requireTokensGrounded(
                    normalizedOutput,
                    normalizedContext,
                    WAIT_DURATION,
                    matcher -> Integer.parseInt(matcher.group(1)) + matcher.group(2),
                    "Generated wait time is not grounded in user context.");
        }
        for (String indicator : EXACT_CONTEXT_ONLY_CLAIMS) {
            if (normalizedOutput.contains(indicator) && !normalizedContext.contains(indicator)) {
                throw new TextGenerationException("Generated personal experience is not grounded in user context.");
            }
        }
        for (List<String> claimFamily : CONTEXT_ONLY_CLAIMS) {
            if (containsAny(normalizedOutput, claimFamily) && !containsAny(normalizedContext, claimFamily)) {
                throw new TextGenerationException("Generated personal experience is not grounded in user context.");
            }
        }
    }

    private boolean containsAny(String value, List<String> indicators) {
        return indicators.stream().anyMatch(value::contains);
    }

    private void requireTokensGrounded(
            String output,
            String context,
            Pattern pattern,
            Function<Matcher, String> normalizer,
            String message) {
        Set<String> outputTokens = tokens(pattern, output, normalizer);
        Set<String> contextTokens = tokens(pattern, context, normalizer);
        if (!contextTokens.containsAll(outputTokens)) {
            throw new TextGenerationException(message);
        }
    }

    private Set<String> tokens(Pattern pattern, String value, Function<Matcher, String> normalizer) {
        Matcher matcher = pattern.matcher(value);
        Set<String> tokens = new HashSet<>();
        while (matcher.find()) {
            tokens.add(normalizer.apply(matcher));
        }
        return tokens;
    }

    private void requireDatesGrounded(String output, String context) {
        if (!dateTokens(context).containsAll(dateTokens(output))) {
            throw new TextGenerationException("Generated date is not grounded in user context.");
        }
    }

    private Set<String> dateTokens(String value) {
        Set<String> dates = new HashSet<>(tokens(
                KOREAN_FULL_DATE,
                value,
                matcher -> dateToken(matcher.group(1), matcher.group(2), matcher.group(3))));
        dates.addAll(tokens(
                ISO_DATE,
                value,
                matcher -> dateToken(matcher.group(1), matcher.group(2), matcher.group(3))));
        dates.addAll(tokens(
                MONTH_DAY,
                value,
                matcher -> monthDayToken(matcher.group(1), matcher.group(2))));
        Matcher isoMatcher = ISO_DATE.matcher(value);
        while (isoMatcher.find()) {
            dates.add(monthDayToken(isoMatcher.group(2), isoMatcher.group(3)));
        }
        return dates;
    }

    private String dateToken(String year, String month, String day) {
        return year + "-" + monthDayToken(month, day);
    }

    private String monthDayToken(String month, String day) {
        return Integer.parseInt(month) + "-" + Integer.parseInt(day);
    }

    private String normalizeTag(String value) {
        String tag = value == null ? "" : value.strip();
        if (tag.startsWith("#")) {
            tag = tag.substring(1);
        }
        return tag.toLowerCase(Locale.ROOT);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new TextGenerationException(message);
        }
        return value.strip();
    }
}
