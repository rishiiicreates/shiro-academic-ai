package com.shiro.rag.controller;

import com.shiro.rag.config.AppProperties;
import com.shiro.rag.service.RetrievalService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

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
        return Map.of(
                "status", "ok",
                "service", "shiro-backend",
                "framework", "Spring WebFlux Reactive",
                "geminiModel", properties.getGeminiModel()
        );
    }
}
