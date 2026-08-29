package com.thehelper.rag.controller;

import com.thehelper.rag.config.AppProperties;
import com.thehelper.rag.service.RetrievalService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MetadataController {

    private final RetrievalService retrievalService;
    private final AppProperties properties;

    public MetadataController(RetrievalService retrievalService, AppProperties properties) {
        this.retrievalService = retrievalService;
        this.properties = properties;
    }

    @GetMapping("/metadata")
    public Mono<Map<String, Object>> getMetadata() {
        return retrievalService.getMetadata();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("service", "chiroshiro-backend");
        result.put("framework", "Spring WebFlux Reactive");
        result.put("llmProvider", "ollama");
        result.put("ollamaModel", properties.getOllamaModel());
        result.put("ollamaBaseUrl", properties.getOllamaBaseUrl());
        return result;
    }
}
