package com.thehelper.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.thehelper.rag.config.AppProperties;
import com.thehelper.rag.model.MessageRecord;
import com.thehelper.rag.model.ThreadRecord;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ThreadStorageService {
    private static final Logger log = LoggerFactory.getLogger(ThreadStorageService.class);
    public static final int MAX_SESSIONS = 20;

    private final AppProperties properties;
    private final ObjectMapper objectMapper;
    private final Map<String, ThreadRecord> threadMap = new ConcurrentHashMap<>();
    private File storageFile;

    public ThreadStorageService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void init() {
        File dir = new File(properties.getDataDir());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        storageFile = new File(dir, "threads.json");
        loadFromDisk();
    }

    private synchronized void loadFromDisk() {
        if (storageFile != null && storageFile.exists() && storageFile.length() > 0) {
            try {
                List<ThreadRecord> list = objectMapper.readValue(storageFile, new TypeReference<List<ThreadRecord>>() {});
                for (ThreadRecord tr : list) {
                    if (tr.getId() != null) {
                        threadMap.put(tr.getId(), tr);
                    }
                }
                pruneExcessSessions();
                saveToDisk();
                log.info("Loaded {} chat sessions from disk (capped at past {} sessions).", threadMap.size(), MAX_SESSIONS);
            } catch (Exception e) {
                log.error("Failed to load threads from disk: {}", e.getMessage());
            }
        }
    }

    private synchronized void pruneExcessSessions() {
        if (threadMap.size() <= MAX_SESSIONS) return;
        List<ThreadRecord> list = new ArrayList<>(threadMap.values());
        list.sort((a, b) -> {
            String u1 = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
            String u2 = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
            if (u1 == null && u2 == null) return 0;
            if (u1 == null) return 1;
            if (u2 == null) return -1;
            return u2.compareTo(u1);
        });

        Set<String> keepIds = new HashSet<>();
        for (int i = 0; i < Math.min(MAX_SESSIONS, list.size()); i++) {
            keepIds.add(list.get(i).getId());
        }
        threadMap.keySet().removeIf(id -> !keepIds.contains(id));
    }

    private synchronized void saveToDisk() {
        if (storageFile == null) return;
        try {
            pruneExcessSessions();
            List<ThreadRecord> list = new ArrayList<>(threadMap.values());
            list.sort((a, b) -> {
                String u1 = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
                String u2 = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
                if (u1 == null && u2 == null) return 0;
                if (u1 == null) return 1;
                if (u2 == null) return -1;
                return u2.compareTo(u1);
            });
            objectMapper.writeValue(storageFile, list);
        } catch (IOException e) {
            log.error("Failed to save threads to disk: {}", e.getMessage());
        }
    }

    public List<ThreadRecord> listThreads() {
        List<ThreadRecord> list = new ArrayList<>(threadMap.values());
        list.sort((a, b) -> {
            String u1 = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
            String u2 = b.getUpdatedAt() != null ? b.getUpdatedAt() : b.getCreatedAt();
            if (u1 == null && u2 == null) return 0;
            if (u1 == null) return 1;
            if (u2 == null) return -1;
            return u2.compareTo(u1);
        });
        if (list.size() > MAX_SESSIONS) {
            return new ArrayList<>(list.subList(0, MAX_SESSIONS));
        }
        return list;
    }

    public Optional<ThreadRecord> getThread(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(threadMap.get(id));
    }

    public ThreadRecord createThread(String title, String semester, String subject) {
        String id = UUID.randomUUID().toString();
        String safeTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "New Syllabus Chat";
        ThreadRecord tr = new ThreadRecord(id, safeTitle);
        tr.setSemester(semester);
        tr.setSubject(subject);
        threadMap.put(id, tr);
        saveToDisk();
        return tr;
    }

    public synchronized void addMessage(String threadId, MessageRecord message) {
        ThreadRecord tr = threadMap.get(threadId);
        if (tr == null) {
            tr = new ThreadRecord(threadId, "Syllabus Chat");
            threadMap.put(threadId, tr);
        }
        if (message.getId() == null) {
            message.setId(UUID.randomUUID().toString());
        }
        tr.getMessages().add(message);
        tr.setUpdatedAt(Instant.now().toString());

        // Set title from first user message if default
        if (tr.getMessages().size() <= 2 && "user".equals(message.getRole())) {
            String content = message.getContent();
            if (content != null && !content.isEmpty()) {
                String snippet = content.length() > 40 ? content.substring(0, 40) + "..." : content;
                tr.setTitle(snippet);
            }
        }
        saveToDisk();
    }

    public synchronized boolean deleteThread(String id) {
        if (id != null && threadMap.remove(id) != null) {
            saveToDisk();
            return true;
        }
        return false;
    }

    /**
     * Builds structured session memory summarizing up to the past 20 study sessions.
     */
    public String buildSessionMemoryContext(String currentThreadId) {
        List<ThreadRecord> threads = listThreads();
        if (threads == null || threads.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== STUDENT'S RECENT STUDY SESSION MEMORY (LAST 20 SESSIONS) ===\n");
        sb.append("You have continuous academic memory of the student's past study sessions (strictly tracking up to the past 20 sessions).\n");
        sb.append("Use this memory naturally when the student asks about previous sessions, earlier topics, learning progress, or references past discussions.\n\n");

        int count = 0;
        for (ThreadRecord tr : threads) {
            if (tr.getMessages() == null || tr.getMessages().isEmpty()) continue;
            count++;
            if (count > MAX_SESSIONS) break;

            boolean isCurrent = tr.getId() != null && tr.getId().equals(currentThreadId);
            String label = isCurrent ? "Current Session" : "Past Session " + count;

            sb.append(String.format("• [%s] Title: \"%s\"", label, tr.getTitle() != null ? tr.getTitle() : "Study Session"));
            if (tr.getSubject() != null && !tr.getSubject().isEmpty()) {
                sb.append(" | Subject: ").append(tr.getSubject());
            }

            // Collect key user inquiries in this session
            List<String> userQueries = new ArrayList<>();
            for (MessageRecord m : tr.getMessages()) {
                if ("user".equalsIgnoreCase(m.getRole()) && m.getContent() != null && !m.getContent().trim().isEmpty()) {
                    String q = m.getContent().trim().replaceAll("\\s+", " ");
                    if (q.length() > 80) q = q.substring(0, 80) + "...";
                    userQueries.add("\"" + q + "\"");
                }
            }

            if (!userQueries.isEmpty()) {
                sb.append(" | Questions Asked: ").append(String.join(", ", userQueries.subList(0, Math.min(3, userQueries.size()))));
            }
            sb.append("\n");
        }

        sb.append("================================================================");
        return count > 0 ? sb.toString() : "";
    }
}
