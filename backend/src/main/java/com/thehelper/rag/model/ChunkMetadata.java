package com.thehelper.rag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ChunkMetadata {
    @JsonProperty("file_name")
    private String fileName = "";

    @JsonProperty("page_num")
    private String pageNum = "1";

    @JsonProperty("subject")
    private String subject = "";

    @JsonProperty("semester")
    private String semester = "";

    @JsonProperty("category")
    private String category = "";

    @JsonProperty("unit")
    private String unit = "";

    @JsonProperty("rel_path")
    private String relPath = "";

    public ChunkMetadata() {}

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getPageNum() { return pageNum; }
    public void setPageNum(String pageNum) { this.pageNum = pageNum; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getRelPath() { return relPath; }
    public void setRelPath(String relPath) { this.relPath = relPath; }

    public String getFormattedCitation() {
        return String.format("[%s | %s | %s (Page %s)]",
                (subject != null && !subject.isEmpty()) ? subject : "General",
                (category != null && !category.isEmpty()) ? category : "Notes",
                (fileName != null && !fileName.isEmpty()) ? fileName : "Doc",
                (pageNum != null && !pageNum.isEmpty()) ? pageNum : "1");
    }
}
