package com.thehelper.rag.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageRecord {
    private String id;
    private String role; // "user" or "assistant"
    private String content;
    private String timestamp;
    private List<RetrievedChunk> sources = new ArrayList<>();
    private List<AttachmentRecord> attachments = new ArrayList<>();

    public MessageRecord() {
        this.timestamp = Instant.now().toString();
    }

    public MessageRecord(String id, String role, String content) {
        this();
        this.id = id;
        this.role = role;
        this.content = content;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public List<RetrievedChunk> getSources() { return sources; }
    public void setSources(List<RetrievedChunk> sources) { this.sources = sources != null ? sources : new ArrayList<>(); }

    public List<AttachmentRecord> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentRecord> attachments) { this.attachments = attachments != null ? attachments : new ArrayList<>(); }
}
