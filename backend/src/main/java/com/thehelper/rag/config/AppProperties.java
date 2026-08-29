package com.thehelper.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppProperties {
    @Value("${sidecar.url:http://127.0.0.1:8001}")
    private String sidecarUrl;

    @Value("${ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3.1:8b}")
    private String ollamaModel;

    @Value("${ollama.temperature:0.2}")
    private double ollamaTemperature;

    @Value("${storage.data-dir:./data}")
    private String dataDir;

    public String getSidecarUrl() { return sidecarUrl; }
    public String getOllamaBaseUrl() { return ollamaBaseUrl; }
    public String getOllamaModel() { return ollamaModel; }
    public double getOllamaTemperature() { return ollamaTemperature; }
    public String getDataDir() { return dataDir; }
}
