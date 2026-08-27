package com.mimir.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final Pattern PRICE = Pattern.compile("(?:₩\\s*\\d[\\d,.]*|\\d[\\d,.]*\\s*원)");
    private static final List<List<String>> CONTEXT_ONLY_CLAIMS = List.of(
            List.of("웨이팅", "대기 시간", "기다렸", "기다린"),
            List.of("맛있", "맛없", "달콤", "고소", "산미", "풍미", "식감"),
            List.of("서비스", "친절", "불친절"),
            List.of("주문", "시켰"),
            List.of("재방문", "다시 방문", "또 가고"),
            List.of("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"));

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
        if (PRICE.matcher(normalizedOutput).find() && !PRICE.matcher(normalizedContext).find()) {
            throw new TextGenerationException("Generated price is not grounded in user context.");
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
