package com.thehelper.rag.controller;

import com.thehelper.rag.model.ThreadRecord;
import com.thehelper.rag.service.ThreadStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/threads")
@CrossOrigin(origins = "*")
public class ThreadController {

    private final ThreadStorageService threadStorageService;

    public ThreadController(ThreadStorageService threadStorageService) {
        this.threadStorageService = threadStorageService;
    }

    @GetMapping
    public List<ThreadRecord> listThreads() {
        return threadStorageService.listThreads();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ThreadRecord> getThread(@PathVariable String id) {
        return threadStorageService.getThread(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ThreadRecord createThread(@RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : "New Chat";
        String semester = body != null ? body.get("semester") : null;
        String subject = body != null ? body.get("subject") : null;
        return threadStorageService.createThread(title, semester, subject);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteThread(@PathVariable String id) {
        boolean deleted = threadStorageService.deleteThread(id);
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true, "id", id));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
