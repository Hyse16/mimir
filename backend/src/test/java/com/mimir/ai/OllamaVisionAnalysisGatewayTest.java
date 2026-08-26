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
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.json.JsonMapper;

class OllamaVisionAnalysisGatewayTest {

    @Test
    void sendsBase64ImagesAndMapsStructuredResultsByOrdinal() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:11434");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var gateway = new OllamaVisionAnalysisGateway(builder.build(), JsonMapper.builder().build(), "vision-model");
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        server.expect(once(), requestTo("http://127.0.0.1:11434/api/chat"))
                .andExpect(method(POST))
                .andExpect(content().string(containsString("\"stream\":false")))
                .andExpect(content().string(containsString("AQID")))
                .andExpect(content().string(containsString("BAUG")))
                .andExpect(content().string(containsString("\"format\"")))
                .andRespond(withSuccess(
                        """
                                {"message":{"content":"{\\"analyses\\":[{\\"ordinal\\":1,\\"category\\":\\"food\\",\\"description\\":\\"cake on a plate\\",\\"objects\\":[\\"cake\\",\\"plate\\"],\\"visibleText\\":null},{\\"ordinal\\":0,\\"category\\":\\"interior\\",\\"description\\":\\"wooden table\\",\\"objects\\":[\\"table\\"],\\"visibleText\\":\\"MENU\\"}]}"}}
                                """,
                        MediaType.APPLICATION_JSON));

        var results = gateway.analyze(List.of(
                new VisionAnalysisGateway.VisionImage(firstId, 4, new byte[] {1, 2, 3}),
                new VisionAnalysisGateway.VisionImage(secondId, 7, new byte[] {4, 5, 6})));

        assertThat(results).extracting(VisionAnalysisGateway.VisionAnalysis::assetId)
                .containsExactly(secondId, firstId);
        assertThat(results.getFirst().displayOrder()).isEqualTo(7);
        assertThat(results.getLast().visibleText()).isEqualTo("MENU");
        server.verify();
    }

    @Test
    void rejectsNonLoopbackOllamaUrlsForLocalOnlyMode() {
        assertThatThrownBy(() -> new OllamaVisionAnalysisGateway(
                JsonMapper.builder().build(),
                "https://example.com",
                "vision-model"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Local Only");
    }

    @Test
    void rejectsCloudModelIdentifiersForLocalOnlyMode() {
        assertThatThrownBy(() -> new OllamaVisionAnalysisGateway(
                JsonMapper.builder().build(),
                "http://127.0.0.1:11434",
                "vision:cloud"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Local Only");
    }
}
