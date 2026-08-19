package com.thehelper.rag.model;

import java.util.ArrayList;
import java.util.List;

public class ChatRequest {
    private String message;
    private String threadId;
    private String semester;
    private String subject;
    private String category;
    private String studyMode; // "notes", "pyqs", "learn_basics", "all"
    private Integer k = 5;
    private List<AttachmentRecord> attachments = new ArrayList<>();
    private List<MessageRecord> messages = new ArrayList<>();
    private List<SessionSummary> userSessions = new ArrayList<>();

    public ChatRequest() {}

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStudyMode() { return studyMode; }
    public void setStudyMode(String studyMode) { this.studyMode = studyMode; }

    public Integer getK() { return k != null ? k : 5; }
    public void setK(Integer k) { this.k = k; }

    public List<AttachmentRecord> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentRecord> attachments) { this.attachments = attachments != null ? attachments : new ArrayList<>(); }

    public List<MessageRecord> getMessages() { return messages; }
    public void setMessages(List<MessageRecord> messages) { this.messages = messages != null ? messages : new ArrayList<>(); }

    public List<SessionSummary> getUserSessions() { return userSessions; }
    public void setUserSessions(List<SessionSummary> userSessions) { this.userSessions = userSessions != null ? userSessions : new ArrayList<>(); }
}
