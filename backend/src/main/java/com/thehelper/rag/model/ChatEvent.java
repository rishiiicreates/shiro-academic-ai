package com.thehelper.rag.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatEvent {
    private String type; // "sources", "token", "done", "error"
    private String token;
    private String threadId;
    private List<RetrievedChunk> sources;
    private String error;

    public ChatEvent() {}

    public static ChatEvent sources(String threadId, List<RetrievedChunk> sources) {
        ChatEvent e = new ChatEvent();
        e.setType("sources");
        e.setThreadId(threadId);
        e.setSources(sources);
        return e;
    }

    public static ChatEvent token(String threadId, String token) {
        ChatEvent e = new ChatEvent();
        e.setType("token");
        e.setThreadId(threadId);
        e.setToken(token);
        return e;
    }

    public static ChatEvent done(String threadId) {
        ChatEvent e = new ChatEvent();
        e.setType("done");
        e.setThreadId(threadId);
        return e;
    }

    public static ChatEvent error(String threadId, String error) {
        ChatEvent e = new ChatEvent();
        e.setType("error");
        e.setThreadId(threadId);
        e.setError(error);
        return e;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }

    public List<RetrievedChunk> getSources() { return sources; }
    public void setSources(List<RetrievedChunk> sources) { this.sources = sources; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
}
