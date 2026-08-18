# THE HELPER — Academic Syllabus RAG Application

A university coursework and syllabus question-answering system powered by **Python fastembed ONNX Retrieval Sidecar**, **Spring Boot WebFlux Reactive Backend**, and a **Claude-style React SPA Frontend**.

---

## 🏛️ System Architecture

```
┌───────────────────────────────────────────────────────────────────────────┐
│                        React (Vite) SPA Frontend                          │
│   • Claude-Style Center-Column Chat UI  • Light & Dark Theme Support      │
│   • Progressive Markdown SSE Stream      • Interactive Source Citations   │
│   • Persistent Chat Threads             • Subject & Semester Scoping      │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │ HTTP SSE / JSON (Port 5173 / 8080)
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    Spring Boot 3.4 WebFlux Backend                        │
│   • Reactive Non-blocking SSE (/api/chat)                                 │
│   • Grounded Syllabus Prompt Synthesis                                    │
│   • Direct REST Streaming to Gemini 3.1 Flash Lite                        │
│   • Exponential Backoff Rate Limit Handling                               │
│   • Chat Thread History & Metadata Orchestration                          │
└──────────────────┬────────────────────────────────────────────────────────┘
                   │
                   │ Server-to-Server Internal POST /retrieve (Port 8001)
                   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    Python FastAPI Retrieval Sidecar                       │
│   • Exact Inference Runtime: BAAI/bge-small-en-v1.5 via fastembed ONNX    │
│   • Vector Index: ChromaDB (95,672 chunks, 68 subjects, 8 semesters)     │
│   • Metadata Filtering: Semester, Subject, Unit, Category                 │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 📁 Repository Structure

```
/Users/rishii/the-helper-rag-app/
├── sidecar/                       # Python FastAPI Vector Retrieval Service
│   └── sidecar_app.py             # ChromaDB + FastEmbed ONNX server (:8001)
├── backend/                       # Spring Boot WebFlux Orchestration (:8080)
│   ├── pom.xml
│   ├── src/main/java/com/thehelper/rag/
│   │   ├── TheHelperRagApplication.java
│   │   ├── config/                # AppProperties, WebClientConfig, CorsConfig
│   │   ├── controller/            # ChatController, ThreadController, MetadataController
│   │   ├── model/                 # ChatRequest, ChatEvent, ChunkMetadata, ThreadRecord
│   │   └── service/               # RetrievalService, GeminiStreamService, ThreadStorageService
│   └── src/main/resources/
│       └── application.yml
├── frontend/                      # React (Vite) Claude-Style Chat UI (:5173)
│   ├── package.json
│   ├── index.html
│   ├── src/
│   │   ├── App.jsx
│   │   ├── App.css                # Warm tonal design tokens, Light & Dark themes
│   │   ├── components/            # Sidebar, ChatArea, MessageItem, InputBox, FilterBar, CitationsModal
│   │   └── services/api.js        # SSE streaming reader and REST client
├── eval/                          # 10-Question Grounding & Out-of-Scope Eval Suite
│   ├── eval_suite.py
│   └── eval_results.json          # 100% Pass Rate Benchmark Report
├── start.sh                       # Single-command stack launcher
└── README.md
```

---

## 🚀 Running the Full Stack

Run the unified startup script:
```bash
/Users/rishii/the-helper-rag-app/start.sh
```

Or start the individual components:

### 1. Python Retrieval Sidecar
```bash
/tmp/dl_venv/bin/python /Users/rishii/the-helper-rag-app/sidecar/sidecar_app.py
```
*Health Check:* `curl http://127.0.0.1:8001/health`

### 2. Spring Boot WebFlux Backend
```bash
cd /Users/rishii/the-helper-rag-app/backend
java -jar target/the-helper-rag-backend-1.0.0.jar
```
*Health Check:* `curl http://127.0.0.1:8080/api/health`

### 3. React Frontend
```bash
cd /Users/rishii/the-helper-rag-app/frontend
npm run dev -- --host 127.0.0.1 --port 5173
```
Open **http://127.0.0.1:5173** in your browser.

---

## 📊 Evaluation & Grounding Benchmark

Run the automated evaluation suite:
```bash
python3 /Users/rishii/the-helper-rag-app/eval/eval_suite.py
```

### Benchmark Results (10/10 Passed - 100% Accuracy)
* **In-Scope Syllabus Concepts** (Operating Systems, DBMS, Calculus, Discrete Math, Chemistry): Correctly grounded in course documents, citing exact units, files, and page numbers.
* **Out-of-Scope Concepts** (General world trivia, recipes): Correctly refused with strict syllabus bounding (*"The provided syllabus and course materials do not contain information to answer this question"*).
