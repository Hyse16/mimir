package com.mimir.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mimir.ai.TextGenerationGateway.DraftGenerationRequest;
import com.mimir.ai.TextGenerationGateway.DraftTarget;
import com.mimir.ai.TextGenerationGateway.GeneratedDraft;
import com.mimir.ai.TextGenerationGateway.ImageFact;

class GeneratedDraftValidatorTest {

    private final GeneratedDraftValidator validator = new GeneratedDraftValidator();

    @Test
    void acceptsOrderedPlaceholdersAndContextGroundedExperience() {
        DraftGenerationRequest request = request("치즈케이크가 맛있었음");

        GeneratedDraft validated = validator.validate(request, new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:1}}\n치즈케이크가 맛있었어요.\n{{IMAGE:2}}\n실내에는 테이블이 있어요.",
                List.of("카페", "카페", "성수")));

        assertThat(validated.tags()).containsExactly("카페", "성수");
    }

    @Test
    void rejectsPersonalExperienceMissingFromUserContext() {
        assertThatThrownBy(() -> validator.validate(request(""), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:1}}\n웨이팅은 20분이었어요.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("not grounded");
    }

    @Test
    void rejectsPriceMissingFromUserContext() {
        assertThatThrownBy(() -> validator.validate(request(""), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:1}}\n케이크는 8,000원이었어요.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("price");
    }

    @Test
    void rejectsDifferentPriceThanUserContext() {
        assertThatThrownBy(() -> validator.validate(request("케이크는 8,000원이었음"), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:1}}\n케이크는 9,000원이었어요.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("price");
    }

    @Test
    void rejectsDifferentWeekdayThanUserContext() {
        assertThatThrownBy(() -> validator.validate(request("지난 토요일 방문"), new GeneratedDraft(
                "일요일 성수 기록",
                "{{IMAGE:1}}\n일요일에 방문했어요.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("experience");
    }

    @Test
    void rejectsInventedDateWhenContextHasAnotherDate() {
        assertThatThrownBy(() -> validator.validate(request("2026년 8월 20일 방문"), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:1}}\n2026년 8월 21일에 방문했어요.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("date");
    }

    @Test
    void rejectsInventedWaitDurationWhenOnlyWaitingWasMentioned() {
        assertThatThrownBy(() -> validator.validate(request("입장 전에 웨이팅했음"), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:1}}\n20분 웨이팅했어요.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("wait time");
    }

    @Test
    void rejectsUngroundedTasteOrRecommendationEvenWhenAnotherTasteIsGrounded() {
        assertThatThrownBy(() -> validator.validate(request("커피의 산미가 강했음"), new GeneratedDraft(
                "추천하는 성수 카페",
                "{{IMAGE:1}}\n케이크는 고소해서 맛있었어요.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("experience");
    }

    @Test
    void rejectsChangedServiceOpinion() {
        assertThatThrownBy(() -> validator.validate(request("서비스가 불친절했음"), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:1}}\n직원분이 친절했어요.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("experience");
    }

    @Test
    void rejectsOpinionInferredFromVisibleObjects() {
        assertThatThrownBy(() -> validator.validate(request(""), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:1}}\n따뜻하고 편안한 분위기예요.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("experience");
    }

    @Test
    void acceptsExactGroundedPriceDateWaitAndTasteClaims() {
        DraftGenerationRequest request = request(
                "2026년 8월 20일 목요일 방문. 15분 웨이팅 후 8,000원 케이크를 주문했고 맛있었음");

        GeneratedDraft validated = validator.validate(request, new GeneratedDraft(
                "목요일 성수 카페 기록",
                "{{IMAGE:1}}\n2026년 8월 20일에 15분 기다린 뒤 8,000원 케이크를 주문했고 맛있었어요."
                        + "\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페")));

        assertThat(validated.title()).isEqualTo("목요일 성수 카페 기록");
    }

    @Test
    void acceptsTheSameGroundedDateInIsoFormat() {
        GeneratedDraft validated = validator.validate(request("2026년 8월 20일 방문"), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:1}}\n2026-08-20 방문 기록입니다.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("카페")));

        assertThat(validated.body()).contains("2026-08-20");
    }

    @Test
    void rejectsMissingOrReorderedImagePlaceholders() {
        assertThatThrownBy(() -> validator.validate(request(""), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:2}}\n실내 사진입니다.\n{{IMAGE:1}}\n케이크 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("display order");
    }

    @Test
    void titleTargetPreservesBodyAndTagsAndIgnoresDiscardedClaims() {
        GeneratedDraft validated = validator.validate(request("", DraftTarget.TITLE), new GeneratedDraft(
                "간결한 새 제목",
                "8,000원이고 맛있었어요.",
                List.of("추천")));

        assertThat(validated.title()).isEqualTo("간결한 새 제목");
        assertThat(validated.body()).isEqualTo("기존 본문");
        assertThat(validated.tags()).containsExactly("기존태그");
    }

    @Test
    void bodyTargetPreservesTitleAndTagsWhileRequiringImageOrder() {
        GeneratedDraft validated = validator.validate(request("", DraftTarget.BODY), new GeneratedDraft(
                "무시할 제목",
                "{{IMAGE:1}}\n케이크 사진입니다.\n{{IMAGE:2}}\n실내 사진입니다.",
                List.of("무시할태그")));

        assertThat(validated.title()).isEqualTo("기존 제목");
        assertThat(validated.body()).contains("{{IMAGE:1}}", "{{IMAGE:2}}");
        assertThat(validated.tags()).containsExactly("기존태그");
    }

    @Test
    void tagsTargetPreservesTitleAndBodyAndGroundsGeneratedTags() {
        GeneratedDraft validated = validator.validate(request("성수 방문", DraftTarget.TAGS), new GeneratedDraft(
                "무시할 제목",
                "8,000원이고 맛있었어요.",
                List.of("#성수", "성수")));

        assertThat(validated.title()).isEqualTo("기존 제목");
        assertThat(validated.body()).isEqualTo("기존 본문");
        assertThat(validated.tags()).containsExactly("성수");

        assertThatThrownBy(() -> validator.validate(request("", DraftTarget.TAGS), new GeneratedDraft(
                "무시할 제목",
                "무시할 본문",
                List.of("추천"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("experience");
    }

    private DraftGenerationRequest request(String context) {
        return request(context, DraftTarget.FULL);
    }

    private DraftGenerationRequest request(String context, DraftTarget target) {
        return new DraftGenerationRequest(
                "기존 제목",
                "기존 본문",
                List.of("기존태그"),
                context,
                List.of(
                        new ImageFact(0, "음식", "접시 위 케이크", List.of("케이크", "접시"), null),
                        new ImageFact(1, "실내", "카페 실내", List.of("테이블", "의자"), null)),
                "편안한 존댓말로 수정",
                target);
    }
}
