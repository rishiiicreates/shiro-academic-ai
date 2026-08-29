package com.shiro.rag.controller;

import com.shiro.rag.model.AttachmentRecord;
import com.shiro.rag.service.FileUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
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

        log.info("Received file upload request: filename={}, contentType={}", filename, contentType);

        return DataBufferUtils.join(filePart.content())
                .flatMap(dataBuffer -> {
                    int count = dataBuffer.readableByteCount();
                    if (count == 0) {
                        DataBufferUtils.release(dataBuffer);
                        return Mono.error(new IllegalArgumentException("Uploaded file is empty"));
                    }
                    byte[] bytes = new byte[count];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return fileUploadService.uploadToGeminiFilesApi(bytes, filename, contentType);
                })
                .switchIfEmpty(Mono.error(new IllegalArgumentException("File content was empty")))
                .map(ResponseEntity::ok)
                .onErrorResume(err -> {
                    log.error("Upload controller failure: {}", err.getMessage());
                    return Mono.just(ResponseEntity.status(500).build());
                });
    }
}
