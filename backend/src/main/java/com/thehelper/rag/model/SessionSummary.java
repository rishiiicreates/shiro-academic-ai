package com.thehelper.rag.model;

import java.util.ArrayList;
import java.util.List;

public class SessionSummary {
    private String id;
    private String title;
    private String subject;
    private List<String> questions = new ArrayList<>();

    public SessionSummary() {}

    public SessionSummary(String id, String title, String subject, List<String> questions) {
        this.id = id;
        this.title = title;
        this.subject = subject;
        this.questions = questions != null ? questions : new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public List<String> getQuestions() { return questions; }
    public void setQuestions(List<String> questions) { this.questions = questions != null ? questions : new ArrayList<>(); }
}
