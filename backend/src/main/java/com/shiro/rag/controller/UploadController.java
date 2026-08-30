package com.shiro.rag.controller;

import com.shiro.rag.model.AttachmentRecord;
import com.shiro.rag.service.FileUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UploadController {
    private static final Logger log = LoggerFactory.getLogger(UploadController.class);
    private static final int MAX_FILE_SIZE = 15 * 1024 * 1024; // 15MB cap

    private final FileUploadService fileUploadService;

    public UploadController(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<AttachmentRecord>> uploadFile(@RequestPart("file") FilePart filePart) {
        String filename = filePart.filename();
        String contentType = filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";

        long contentLength = filePart.headers().getContentLength();
        if (contentLength > MAX_FILE_SIZE) {
            log.warn("Upload rejected upfront - Content-Length {} exceeds 15MB limit for {}", contentLength, filename);
            return Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build());
        }

        log.info("Received file upload request: filename={}, contentType={}, declaredSize={}", filename, contentType, contentLength);

        return DataBufferUtils.join(filePart.content(), MAX_FILE_SIZE)
                .flatMap(dataBuffer -> {
                    try {
                        int count = dataBuffer.readableByteCount();
                        if (count == 0) {
                            return Mono.error(new IllegalArgumentException("Uploaded file is empty"));
                        }
                        if (count > MAX_FILE_SIZE) {
                            return Mono.error(new DataBufferLimitException("File size exceeds 15MB"));
                        }
                        byte[] bytes = new byte[count];
                        dataBuffer.read(bytes);
                        return fileUploadService.uploadToGeminiFilesApi(bytes, filename, contentType);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException("File content was empty")))
                .map(ResponseEntity::ok)
                .onErrorResume(DataBufferLimitException.class, err -> {
                    log.warn("Upload rejected - file size exceeded 15MB limit: {}", err.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build());
                })
                .onErrorResume(IllegalArgumentException.class, err -> {
                    log.warn("Upload rejected - invalid request: {}", err.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                })
                .onErrorResume(err -> {
                    log.error("Upload controller failure: {}", err.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
}
