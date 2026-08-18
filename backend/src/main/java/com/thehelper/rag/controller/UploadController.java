package com.thehelper.rag.controller;

import com.thehelper.rag.model.AttachmentRecord;
import com.thehelper.rag.service.FileUploadService;
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
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return bytes;
                })
                .flatMap(bytes -> fileUploadService.uploadToGeminiFilesApi(bytes, filename, contentType))
                .map(ResponseEntity::ok)
                .onErrorResume(err -> {
                    log.error("Upload controller failure: {}", err.getMessage());
                    return Mono.just(ResponseEntity.status(500).build());
                });
    }
}
