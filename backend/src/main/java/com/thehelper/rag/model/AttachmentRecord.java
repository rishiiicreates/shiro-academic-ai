package com.thehelper.rag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AttachmentRecord {
    private String fileUri;
    private String fileId;
    private String mimeType;
    private String displayName;
    private Long sizeBytes;
    private String localUrl;

    public AttachmentRecord() {}

    public AttachmentRecord(String fileUri, String fileId, String mimeType, String displayName, Long sizeBytes) {
        this.fileUri = fileUri;
        this.fileId = fileId;
        this.mimeType = mimeType;
        this.displayName = displayName;
        this.sizeBytes = sizeBytes;
    }

    public String getFileUri() { return fileUri; }
    public void setFileUri(String fileUri) { this.fileUri = fileUri; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getLocalUrl() { return localUrl; }
    public void setLocalUrl(String localUrl) { this.localUrl = localUrl; }
}
