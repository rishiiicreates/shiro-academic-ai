package com.thehelper.rag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RetrievedChunk {
    private String id;
    private String text;
    private double similarity;
    private Double distance;
    private ChunkMetadata metadata = new ChunkMetadata();
    private List<String> images = new ArrayList<>();

    public RetrievedChunk() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public double getSimilarity() { return similarity; }
    public void setSimilarity(double similarity) { this.similarity = similarity; }

    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }

    public ChunkMetadata getMetadata() { return metadata; }
    public void setMetadata(ChunkMetadata metadata) { this.metadata = metadata; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images != null ? images : new ArrayList<>(); }
}
