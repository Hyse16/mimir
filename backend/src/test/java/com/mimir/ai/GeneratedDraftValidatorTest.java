package com.mimir.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mimir.ai.TextGenerationGateway.DraftGenerationRequest;
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
    void rejectsMissingOrReorderedImagePlaceholders() {
        assertThatThrownBy(() -> validator.validate(request(""), new GeneratedDraft(
                "성수 카페 기록",
                "{{IMAGE:2}}\n실내 사진입니다.\n{{IMAGE:1}}\n케이크 사진입니다.",
                List.of("카페"))))
                .isInstanceOf(TextGenerationException.class)
                .hasMessageContaining("display order");
    }

    private DraftGenerationRequest request(String context) {
        return new DraftGenerationRequest(
                "기존 제목",
                "기존 본문",
                context,
                List.of(
                        new ImageFact(0, "음식", "접시 위 케이크", List.of("케이크", "접시"), null),
                        new ImageFact(1, "실내", "카페 실내", List.of("테이블", "의자"), null)),
                "편안한 존댓말로 수정");
    }
}
