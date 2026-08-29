package com.thehelper.rag.service;

import com.thehelper.rag.config.AppProperties;
import com.thehelper.rag.model.AttachmentRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileUploadService {
    private static final Logger log = LoggerFactory.getLogger(FileUploadService.class);

    private final AppProperties properties;

    public FileUploadService(AppProperties properties) {
        this.properties = properties;
    }

    public Mono<AttachmentRecord> saveLocally(byte[] fileBytes, String originalFilename, String contentType) {
        return Mono.fromCallable(() -> {
            String safeFilename = sanitizeFilename(originalFilename);
            String safeContentType = (contentType != null && !contentType.trim().isEmpty())
                    ? contentType.trim()
                    : "application/octet-stream";

            Path uploadDir = Paths.get(properties.getDataDir(), "uploads").toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String storedName = UUID.randomUUID() + "_" + safeFilename;
            Path destination = uploadDir.resolve(storedName).normalize();
            if (!destination.startsWith(uploadDir)) {
                throw new IOException("Invalid upload path");
            }

            Files.write(destination, fileBytes);

            String localUrl = "/api/uploads/" + storedName;
            AttachmentRecord record = new AttachmentRecord(
                    localUrl,
                    storedName,
                    safeContentType,
                    safeFilename,
                    (long) fileBytes.length
            );
            record.setLocalUrl(localUrl);

            log.info("Saved upload locally: '{}' ({} bytes, {})", destination, fileBytes.length, safeContentType);
            return record;
        });
    }

    public Mono<Resource> load(String storedName) {
        return Mono.fromCallable(() -> {
            if (storedName == null || storedName.isBlank() || storedName.contains("..")
                    || storedName.contains("/") || storedName.contains("\\")) {
                throw new IllegalArgumentException("Invalid file name");
            }

            Path uploadDir = Paths.get(properties.getDataDir(), "uploads").toAbsolutePath().normalize();
            Path file = uploadDir.resolve(storedName).normalize();
            if (!file.startsWith(uploadDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
                throw new IOException("File not found");
            }
            return new FileSystemResource(file);
        });
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "upload";
        }
        String value = Paths.get(filename).getFileName().toString().trim();
        value = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        return value.isEmpty() ? "upload" : value;
    }
}
