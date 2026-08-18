package com.thehelper.rag.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RetrieveRequest {
    private String question;
    private int k = 5;
    private String semester;
    private String subject;
    private String category;
    private String study_mode;

    public RetrieveRequest() {}

    public RetrieveRequest(String question, int k, String semester, String subject, String category, String study_mode) {
        this.question = question;
        this.k = k;
        this.semester = semester;
        this.subject = subject;
        this.category = category;
        this.study_mode = study_mode;
    }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public int getK() { return k; }
    public void setK(int k) { this.k = k; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getStudy_mode() { return study_mode; }
    public void setStudy_mode(String study_mode) { this.study_mode = study_mode; }
}
