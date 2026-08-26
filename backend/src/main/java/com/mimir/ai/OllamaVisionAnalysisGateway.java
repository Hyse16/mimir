package com.mimir.ai;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class OllamaVisionAnalysisGateway implements VisionAnalysisGateway {

    private static final String PROMPT = """
            Analyze each image in order and return only grounded, visible facts.
            Never infer prices, wait times, taste, service quality, visit dates, orders, opinions, or personal experience.
            Use the least-specific visible label when an exact food, drink, product, or recipe subtype is uncertain.
            Do not use speculative phrases to introduce details that are not directly visible.
            Include every clearly visible salient object, including tableware and utensils.
            Use the zero-based ordinal from the supplied image order. Keep descriptions concise and factual.
            Write category, description, and object names in Korean. Preserve visible text exactly as shown.
            """;

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String model;

    @Autowired
    OllamaVisionAnalysisGateway(
            ObjectMapper objectMapper,
            @Value("${mimir.ai.ollama.base-url:http://127.0.0.1:11434}") String baseUrl,
            @Value("${mimir.ai.ollama.vision-model:gemma4:latest}") String model) {
        this.objectMapper = objectMapper;
        this.model = localModel(model);
        URI uri = localOnlyUri(baseUrl);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofMinutes(3));
        this.client = RestClient.builder().baseUrl(uri.toString()).requestFactory(requestFactory).build();
    }

    OllamaVisionAnalysisGateway(RestClient client, ObjectMapper objectMapper, String model) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.model = localModel(model);
    }

    @Override
    public List<VisionAnalysis> analyze(List<VisionImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<String> encodedImages = images.stream()
                .map(VisionImage::content)
                .map(Base64.getEncoder()::encodeToString)
                .toList();
        ChatRequest request = new ChatRequest(
                model,
                List.of(new ChatMessage("user", PROMPT, encodedImages)),
                false,
                schema(),
                Map.of("temperature", 0));
        try {
            ChatResponse response = client.post()
                    .uri("/api/chat")
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
            if (response == null || response.message() == null || response.message().content() == null) {
                throw new VisionGatewayException("Ollama returned an empty Vision response.");
            }
            StructuredResponse structured = objectMapper.readValue(
                    response.message().content(),
                    StructuredResponse.class);
            return mapResults(images, structured.analyses());
        } catch (JacksonException error) {
            throw new VisionGatewayException("Ollama returned invalid structured Vision output.", error);
        } catch (VisionGatewayException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new VisionGatewayException("Local Ollama Vision request failed.", error);
        }
    }

    private List<VisionAnalysis> mapResults(List<VisionImage> images, List<StructuredAnalysis> results) {
        if (results == null || results.size() != images.size()) {
            throw new VisionGatewayException("Vision output must contain one result per image.");
        }
        Set<Integer> ordinals = new HashSet<>();
        List<VisionAnalysis> mapped = new ArrayList<>();
        for (StructuredAnalysis result : results) {
            if (result == null || result.ordinal() < 0 || result.ordinal() >= images.size()
                    || !ordinals.add(result.ordinal()) || isBlank(result.category()) || isBlank(result.description())
                    || result.objects() == null) {
                throw new VisionGatewayException("Vision output contains an invalid or duplicate ordinal.");
            }
            VisionImage image = images.get(result.ordinal());
            mapped.add(new VisionAnalysis(
                    image.assetId(),
                    image.displayOrder(),
                    limited(result.category(), 64),
                    limited(result.description(), 4_000),
                    result.objects().stream().filter(value -> !isBlank(value)).map(value -> limited(value, 100)).limit(50).toList(),
                    result.visibleText() == null ? null : limited(result.visibleText(), 4_000)));
        }
        return mapped;
    }

    private Map<String, Object> schema() {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("type", "object");
        analysis.put("additionalProperties", false);
        analysis.put("properties", Map.of(
                "ordinal", Map.of("type", "integer", "minimum", 0),
                "category", Map.of("type", "string"),
                "description", Map.of("type", "string"),
                "objects", Map.of("type", "array", "items", Map.of("type", "string")),
                "visibleText", Map.of("type", List.of("string", "null"))));
        analysis.put("required", List.of("ordinal", "category", "description", "objects", "visibleText"));
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of("analyses", Map.of(
                        "type", "array",
                        "items", analysis,
                        "minItems", 1,
                        "maxItems", 4)),
                "required", List.of("analyses"));
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String limited(String value, int maxLength) {
        String stripped = value.strip();
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }

    private record ChatRequest(
            String model,
            List<ChatMessage> messages,
            boolean stream,
            Map<String, Object> format,
            Map<String, Object> options) {
    }

    private record ChatMessage(String role, String content, List<String> images) {
    }

    private record ChatResponse(ChatResponseMessage message) {
    }

    private record ChatResponseMessage(String content) {
    }

    private record StructuredResponse(List<StructuredAnalysis> analyses) {
    }

    private record StructuredAnalysis(
            int ordinal,
            String category,
            String description,
            List<String> objects,
            String visibleText) {
    }
}
