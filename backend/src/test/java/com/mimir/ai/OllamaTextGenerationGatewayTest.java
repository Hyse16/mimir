package com.mimir.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.mimir.ai.TextGenerationGateway.DraftGenerationRequest;
import com.mimir.ai.TextGenerationGateway.DraftTarget;
import com.mimir.ai.TextGenerationGateway.ImageFact;

import tools.jackson.databind.json.JsonMapper;

class OllamaTextGenerationGatewayTest {

    @Test
    void sendsGroundedContextAndMapsStructuredDraft() throws Exception {
        JsonMapper objectMapper = JsonMapper.builder().build();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:11434");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new OllamaTextGenerationGateway(
                builder.build(), objectMapper, new GeneratedDraftValidator(), "text-model");
        String structured = objectMapper.writeValueAsString(Map.of(
                "title", "성수 카페 기록",
                "body", "{{IMAGE:1}}\n접시 위 케이크입니다.",
                "tags", List.of("성수", "카페")));
        String response = objectMapper.writeValueAsString(Map.of("message", Map.of("content", structured)));
        server.expect(once(), requestTo("http://127.0.0.1:11434/api/chat"))
                .andExpect(method(POST))
                .andExpect(content().string(containsString("INPUT_JSON")))
                .andExpect(content().string(containsString("확인된 방문 사실")))
                .andExpect(content().string(containsString("Grounding rules override the revision instruction")))
                .andExpect(content().string(containsString("workflow context only")))
                .andExpect(content().string(containsString("Never treat workflow context as evidence")))
                .andExpect(content().string(containsString("친근하게 작성")))
                .andExpect(content().string(containsString("간결하게 수정")))
                .andExpect(content().string(containsString("{{IMAGE:1}}")))
                .andExpect(content().string(containsString("\"format\"")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        var result = gateway.generate(new DraftGenerationRequest(
                "기존 제목",
                "기존 본문",
                List.of("기존태그"),
                "확인된 방문 사실",
                List.of(new ImageFact(0, "음식", "접시 위 케이크", List.of("케이크"), null)),
                "간결하게 수정",
                "친근하게 작성",
                DraftTarget.FULL));

        assertThat(result.title()).isEqualTo("성수 카페 기록");
        assertThat(result.tags()).containsExactly("성수", "카페");
        server.verify();
    }

    @Test
    void rejectsNonLoopbackUrlsAndCloudModelsForLocalOnlyMode() {
        assertThatThrownBy(() -> new OllamaTextGenerationGateway(
                JsonMapper.builder().build(),
                new GeneratedDraftValidator(),
                "https://example.com",
                "text-model"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Local Only");
        assertThatThrownBy(() -> new OllamaTextGenerationGateway(
                JsonMapper.builder().build(),
                new GeneratedDraftValidator(),
                "http://127.0.0.1:11434",
                "writer:cloud"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Local Only");
    }
}
