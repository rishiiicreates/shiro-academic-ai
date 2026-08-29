package com.thehelper.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thehelper.rag.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

@Service
public class OllamaStreamService {
    private static final Logger log = LoggerFactory.getLogger(OllamaStreamService.class);

    private final WebClient webClient;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public OllamaStreamService(WebClient webClient, AppProperties properties, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Flux<String> streamGenerateContent(String systemInstruction, String userPrompt) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemInstruction.trim()));
        }
        messages.add(Map.of("role", "user", "content", userPrompt == null ? "" : userPrompt));
        return streamMessages(messages);
    }

    /**
     * Accepts the old controller message shape (role + parts[{text: ...}]) so the
     * controller can keep its existing conversation-building logic without any
     * Gemini-specific HTTP call.
     */
    public Flux<String> streamGenerateContent(String systemInstruction, List<Map<String, Object>> contents) {
        List<Map<String, Object>> messages = new ArrayList<>();

        if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemInstruction.trim()));
        }

        if (contents != null) {
            for (Map<String, Object> content : contents) {
                if (content == null) continue;
                String role = "user".equalsIgnoreCase(String.valueOf(content.get("role")))
                        ? "user" : "assistant";

                StringBuilder text = new StringBuilder();
                Object partsObj = content.get("parts");
                if (partsObj instanceof List<?> parts) {
                    for (Object partObj : parts) {
                        if (!(partObj instanceof Map<?, ?> part)) continue;
                        Object textObj = part.get("text");
                        if (textObj != null) {
                            if (!text.isEmpty()) text.append("\n");
                            text.append(textObj);
                        }
                    }
                }

                if (!text.isEmpty()) {
                    messages.add(Map.of("role", role, "content", text.toString()));
                }
            }
        }

        return streamMessages(messages);
    }

    private Flux<String> streamMessages(List<Map<String, Object>> messages) {
        return Flux.defer(() -> {
            String baseUrl = stripTrailingSlash(properties.getOllamaBaseUrl());
            String model = properties.getOllamaModel();
            String url = baseUrl + "/api/chat";

            Map<String, Object> options = new HashMap<>();
            options.put("temperature", properties.getOllamaTemperature());
            options.put("num_predict", 4096);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("stream", true);
            requestBody.put("options", options);

            log.info("Initiating Ollama stream: {} model={} turns={}", url, model, messages.size());

            return webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("application/x-ndjson"))
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<String>() {})
                    .flatMap(this::extractTextFromJson);
        })
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                .filter(this::isTransientError)
                .doBeforeRetry(signal -> log.warn(
                        "Retrying Ollama request (attempt {}): {}",
                        signal.totalRetries() + 1,
                        signal.failure().getMessage())))
        .doOnError(err -> log.error("Ollama streaming call failed: {}", err.getMessage()));
    }

    private Flux<String> extractTextFromJson(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return Flux.empty();
        }

        String raw = jsonData.trim();
        if (raw.startsWith("data:")) {
            raw = raw.substring(5).trim();
        }
        if (raw.isEmpty() || "[DONE]".equals(raw)) {
            return Flux.empty();
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("message").path("content");
            if (content.isTextual() && !content.asText().isEmpty()) {
                return Flux.just(content.asText());
            }
        } catch (Exception e) {
            log.warn("Failed to parse Ollama JSON payload: {} | raw: {}", e.getMessage(), raw);
        }

        return Flux.empty();
    }

    private boolean isTransientError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException wce) {
            int status = wce.getStatusCode().value();
            return status == 429 || status == 502 || status == 503 || status == 504;
        }
        String msg = throwable.getMessage();
        return msg != null && (
                msg.contains("Connection refused") ||
                msg.contains("503") ||
                msg.contains("502") ||
                msg.contains("504")
        );
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) return "http://127.0.0.1:11434";
        return value.replaceAll("/+$", "");
    }
}
