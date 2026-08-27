package com.mimir.ai;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.mimir.ai.TextGenerationGateway.DraftGenerationRequest;
import com.mimir.ai.TextGenerationGateway.GeneratedDraft;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class OllamaTextGenerationGateway implements TextGenerationGateway {

    private static final String PROMPT = """
            Write a Korean Naver blog draft using only the supplied user context, existing draft, and visible image facts.
            Never invent prices, wait times, taste, service quality, visit dates, orders, opinions, or personal experiences.
            Grounding rules override the revision instruction. Omit any requested detail that is not explicitly supported.
            Describe visible facts neutrally. Do not infer quality, atmosphere, comfort, emotion, popularity, or recommendations.
            Use descriptive adjectives only when the exact meaning appears in the supplied facts.
            Preserve explicitly requested grounded numbers and dates exactly; never alter or approximate them.
            Use every image exactly once as {{IMAGE:1}}, {{IMAGE:2}}, and so on in display order.
            Keep image placeholders on their own line and connect surrounding prose only to grounded facts.
            Apply the revision instruction while preserving facts. Return only the requested structured output.
            """;

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final GeneratedDraftValidator validator;
    private final String model;

    @Autowired
    OllamaTextGenerationGateway(
            ObjectMapper objectMapper,
            GeneratedDraftValidator validator,
            @Value("${mimir.ai.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${mimir.ai.ollama.text-model:qwen2.5:7b}") String model) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.model = localModel(model);
        URI uri = localOnlyUri(baseUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofMinutes(3));
        this.client = RestClient.builder().baseUrl(uri.toString()).requestFactory(requestFactory).build();
    }

    OllamaTextGenerationGateway(
            RestClient client,
            ObjectMapper objectMapper,
            GeneratedDraftValidator validator,
            String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.model = localModel(model);
    }

    @Override
    public GeneratedDraft generate(DraftGenerationRequest request) {
        try {
            String input = objectMapper.writeValueAsString(request);
            ChatResponse response = client.post()
                    .uri("/api/chat")
                    .body(new ChatRequest(
                            model,
                            List.of(new ChatMessage("user", PROMPT + "\nINPUT_JSON:\n" + input)),
                            false,
                            schema(),
                            Map.of("temperature", 0)))
                    .retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.message() == null || response.message().content() == null) {
                throw new TextGenerationException("Ollama returned an empty text generation response.");
            }
            StructuredDraft result = objectMapper.readValue(response.message().content(), StructuredDraft.class);
            return validator.validate(request, new GeneratedDraft(result.title(), result.body(), result.tags()));
        } catch (JacksonException error) {
            throw new TextGenerationException("Ollama returned invalid structured text output.", error);
        } catch (TextGenerationException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new TextGenerationException("Local Ollama text generation request failed.", error);
        }
    }

    private Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "title", Map.of("type", "string"),
                        "body", Map.of("type", "string"),
                        "tags", Map.of("type", "array", "items", Map.of("type", "string"), "maxItems", 30)),
                "required", List.of("title", "body", "tags"));
    }

    private static URI localOnlyUri(String value) {
        URI uri = URI.create(value);
        String host = uri.getHost();
        if (!"http".equalsIgnoreCase(uri.getScheme())
                || !("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host))) {
            throw new IllegalArgumentException("Local Only requires a loopback Ollama HTTP URL.");
        }
        return uri;
    }

    private static String localModel(String value) {
        String model = value == null ? "" : value.strip();
        if (model.isEmpty() || model.toLowerCase(Locale.ROOT).contains("cloud")) {
            throw new IllegalArgumentException("Local Only requires an explicitly local Ollama model.");
        }
        return model;
    }

    private record ChatRequest(
            String model,
            List<ChatMessage> messages,
            boolean stream,
            Map<String, Object> format,
            Map<String, Object> options) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record ChatResponse(ChatResponseMessage message) {
    }

    private record ChatResponseMessage(String content) {
    }

    private record StructuredDraft(String title, String body, List<String> tags) {
    }
}
