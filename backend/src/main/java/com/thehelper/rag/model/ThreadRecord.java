package com.thehelper.rag.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ThreadRecord {
    private String id;
    private String title;
    private String createdAt;
    private String updatedAt;
    private String semester;
    private String subject;
    private List<MessageRecord> messages = new ArrayList<>();

    public ThreadRecord() {
        String now = Instant.now().toString();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public ThreadRecord(String id, String title) {
        this();
        this.id = id;
        this.title = title;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public List<MessageRecord> getMessages() { return messages; }
    public void setMessages(List<MessageRecord> messages) { this.messages = messages; }
}
