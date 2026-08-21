package com.thehelper.rag.controller;

import com.thehelper.rag.service.RetrievalService;
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

    public MetadataController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/metadata")
    public Mono<Map<String, Object>> getMetadata() {
        return retrievalService.getMetadata();
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "chiroshiro-backend",
                "framework", "Spring WebFlux Reactive",
                "geminiModel", "gemini-3.1-flash-lite"
        );
    }
}
