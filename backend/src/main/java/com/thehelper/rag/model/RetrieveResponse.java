package com.thehelper.rag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RetrieveResponse {
    private String query;
    private int count;
    private List<RetrievedChunk> chunks = new ArrayList<>();

    public RetrieveResponse() {}

    public RetrieveResponse(List<RetrievedChunk> chunks, int count) {
        this.chunks = chunks != null ? chunks : new ArrayList<>();
        this.count = count;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public List<RetrievedChunk> getChunks() { return chunks; }
    public void setChunks(List<RetrievedChunk> chunks) { this.chunks = chunks; }
}
