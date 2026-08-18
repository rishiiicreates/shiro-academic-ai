package com.thehelper.rag.service;

import com.thehelper.rag.config.AppProperties;
import com.thehelper.rag.model.RetrieveRequest;
import com.thehelper.rag.model.RetrieveResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class RetrievalService {
    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final WebClient webClient;
    private final AppProperties properties;

    public RetrievalService(WebClient webClient, AppProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public Mono<RetrieveResponse> retrieve(RetrieveRequest request) {
        String url = properties.getSidecarUrl() + "/retrieve";
        log.info("Calling retrieval sidecar at {} for query: '{}' (k={}, subject={}, semester={}, category={})",
                url, request.getQuestion(), request.getK(), request.getSubject(), request.getSemester(), request.getCategory());

        return webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RetrieveResponse.class)
                .doOnSuccess(resp -> log.info("Retrieved {} chunks for query", resp != null ? resp.getChunks().size() : 0))
                .doOnError(err -> log.error("Error retrieving chunks from sidecar: {}", err.getMessage()))
                .onErrorResume(err -> {
                    RetrieveResponse fallback = new RetrieveResponse();
                    fallback.setQuery(request.getQuestion());
                    fallback.setCount(0);
                    return Mono.just(fallback);
                });
    }

    public Mono<Map<String, Object>> getMetadata() {
        String url = properties.getSidecarUrl() + "/metadata";
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .map(m -> (Map<String, Object>) m)
                .doOnError(err -> log.error("Error fetching metadata from sidecar: {}", err.getMessage()));
    }
}
