package com.thehelper.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thehelper.rag.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

@Service
public class GeminiStreamService {
    private static final Logger log = LoggerFactory.getLogger(GeminiStreamService.class);

    private final WebClient webClient;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiStreamService(WebClient webClient, AppProperties properties, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Flux<String> streamGenerateContent(String systemInstruction, String userPrompt) {
        Map<String, Object> userContent = new HashMap<>();
        userContent.put("role", "user");
        userContent.put("parts", Collections.singletonList(Collections.singletonMap("text", userPrompt)));
        return streamGenerateContent(systemInstruction, Collections.singletonList(userContent));
    }

    public Flux<String> streamGenerateContent(String systemInstruction, List<Map<String, Object>> contents) {
        return Flux.defer(() -> {
            String apiKey = properties.getGeminiApiKey();
            String model = properties.getGeminiModel();
            String baseUrl = properties.getGeminiBaseUrl();

            String url = String.format("%s/models/%s:streamGenerateContent?alt=sse&key=%s",
                    baseUrl, model, apiKey);

            log.info("Initiating Gemini stream request to model: {} with {} turns", model, contents.size());

            Map<String, Object> requestBody = new HashMap<>();

            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                Map<String, Object> sysParts = new HashMap<>();
                sysParts.put("parts", Collections.singletonList(Collections.singletonMap("text", systemInstruction.trim())));
                requestBody.put("system_instruction", sysParts);
            }

            requestBody.put("contents", contents);

            Map<String, Object> genConfig = new HashMap<>();
            genConfig.put("temperature", properties.getGeminiTemperature());
            genConfig.put("maxOutputTokens", 4096);
            requestBody.put("generationConfig", genConfig);

            return webClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                    .mapNotNull(ServerSentEvent::data)
                    .flatMap(this::extractTextFromJson);
        })
        .retryWhen(Retry.backoff(4, Duration.ofSeconds(2))
                .filter(this::isRateLimitOrTransientError)
                .doBeforeRetry(retrySignal -> log.warn("Gemini 429 rate limit hit, backing off (attempt {}): {}",
                        retrySignal.totalRetries() + 1, retrySignal.failure().getMessage())))
        .doOnError(err -> log.error("Final error in Gemini streaming call: {}", err.getMessage()));
    }

    private boolean isRateLimitOrTransientError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException.TooManyRequests) {
            return true;
        }
        if (throwable instanceof WebClientResponseException wce) {
            return wce.getStatusCode().value() == 429 || wce.getStatusCode().is5xxServerError();
        }
        String msg = throwable.getMessage();
        return msg != null && (msg.contains("429") || msg.contains("Too Many Requests") || msg.contains("503"));
    }

    private Flux<String> extractTextFromJson(String jsonData) {
        if (jsonData == null || jsonData.trim().isEmpty() || jsonData.trim().equals("[DONE]")) {
            return Flux.empty();
        }

        String raw = jsonData.trim();
        if (raw.startsWith("data:")) {
            raw = raw.substring(5).trim();
        }

        if (raw.isEmpty()) {
            return Flux.empty();
        }

        List<String> tokens = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray()) {
                    for (JsonNode part : parts) {
                        if (part.has("text")) {
                            String text = part.get("text").asText();
                            if (text != null && !text.isEmpty()) {
                                tokens.add(text);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Gemini JSON payload: {} | raw: {}", e.getMessage(), raw);
        }

        return Flux.fromIterable(tokens);
    }
}
