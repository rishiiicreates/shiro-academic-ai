package com.thehelper.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thehelper.rag.config.AppProperties;
import com.thehelper.rag.model.AttachmentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
public class FileUploadService {
    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final WebClient webClient;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public FileUploadService(WebClient webClient, AppProperties properties, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Mono<AttachmentRecord> uploadToGeminiFilesApi(byte[] fileBytes, String originalFilename, String contentType) {
        String apiKey = properties.getGeminiApiKey();
        String safeContentType = (contentType != null && !contentType.trim().isEmpty())
                ? contentType.trim()
                : "application/octet-stream";
        String safeDisplayName = (originalFilename != null && !originalFilename.trim().isEmpty())
                ? originalFilename.trim()
                : "upload_" + System.currentTimeMillis();

        long contentLength = fileBytes.length;
        String initUrl = String.format("https://generativelanguage.googleapis.com/upload/v1beta/files?key=%s", apiKey);

        log.info("Starting Gemini Files API upload for '{}' ({} bytes, mime: {})", safeDisplayName, contentLength, safeContentType);

        Map<String, Object> metadataBody = new HashMap<>();
        Map<String, Object> fileObj = new HashMap<>();
        fileObj.put("display_name", safeDisplayName);
        metadataBody.put("file", fileObj);

        // Step 1: Initiate resumable upload session
        return webClient.post()
                .uri(initUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Goog-Upload-Protocol", "resumable")
                .header("X-Goog-Upload-Command", "start")
                .header("X-Goog-Upload-Header-Content-Length", String.valueOf(contentLength))
                .header("X-Goog-Upload-Header-Content-Type", safeContentType)
                .bodyValue(metadataBody)
                .exchangeToMono(clientResponse -> {
                    HttpHeaders headers = clientResponse.headers().asHttpHeaders();
                    String uploadUrl = headers.getFirst("X-Goog-Upload-URL");
                    if (uploadUrl == null) {
                        uploadUrl = headers.getFirst("x-goog-upload-url");
                    }
                    if (uploadUrl == null || clientResponse.statusCode().isError()) {
                        return clientResponse.bodyToMono(String.class)
                                .flatMap(errBody -> Mono.error(new RuntimeException("Failed to init Gemini upload: " + errBody)));
                    }
                    return Mono.just(uploadUrl);
                })
                // Step 2: Upload file binary bytes to the received upload URL
                .flatMap(uploadUrl -> {
                    log.info("Got upload URL, transmitting bytes to Gemini...");
                    return webClient.post()
                            .uri(uploadUrl)
                            .header("X-Goog-Upload-Offset", "0")
                            .header("X-Goog-Upload-Command", "upload, finalize")
                            .header("Content-Length", String.valueOf(contentLength))
                            .contentType(MediaType.parseMediaType(safeContentType))
                            .bodyValue(fileBytes)
                            .retrieve()
                            .bodyToMono(String.class)
                            .flatMap(responseJson -> parseUploadResponse(responseJson, safeDisplayName, safeContentType, contentLength));
                })
                .doOnError(err -> log.error("Gemini file upload failed: {}", err.getMessage()));
    }

    private Mono<AttachmentRecord> parseUploadResponse(String responseJson, String displayName, String contentType, long sizeBytes) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode fileNode = root.path("file");
            String uri = fileNode.path("uri").asText();
            String name = fileNode.path("name").asText();
            String mimeType = fileNode.has("mimeType") ? fileNode.path("mimeType").asText() : contentType;

            log.info("Gemini Files API upload success: uri={}, name={}", uri, name);

            AttachmentRecord record = new AttachmentRecord(uri, name, mimeType, displayName, sizeBytes);
            return Mono.just(record);
        } catch (Exception e) {
            log.error("Failed to parse Gemini upload response: {}", e.getMessage());
            return Mono.error(new RuntimeException("JSON parse error on upload response: " + e.getMessage()));
        }
    }
}
