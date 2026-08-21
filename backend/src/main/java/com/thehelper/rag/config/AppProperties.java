package com.thehelper.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppProperties {
    @Value("${sidecar.url:http://127.0.0.1:8001}")
    private String sidecarUrl;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model:gemini-3.6-flash}")
    private String geminiModel;

    @Value("${gemini.temperature:0.2}")
    private double geminiTemperature;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${storage.data-dir:/Users/rishii/the-helper-rag-app/data}")
    private String dataDir;

    public String getSidecarUrl() { return sidecarUrl; }
    public String getGeminiApiKey() { return geminiApiKey; }
    public String getGeminiModel() { return geminiModel; }
    public double getGeminiTemperature() { return geminiTemperature; }
    public String getGeminiBaseUrl() { return geminiBaseUrl; }
    public String getDataDir() { return dataDir; }
}
