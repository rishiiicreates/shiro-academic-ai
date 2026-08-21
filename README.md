# 🎓 SHIRO (The Helper) — Academic Syllabus RAG System

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4%20WebFlux-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![FastAPI](https://img.shields.io/badge/FastAPI-Python%203.11-009688.svg)](https://fastapi.tiangolo.com)
[![React](https://img.shields.io/badge/React-Vite%20SPA-61DAFB.svg)](https://react.dev)
[![ChromaDB](https://img.shields.io/badge/ChromaDB-95k%2B%20Chunks-orange.svg)](https://www.trychroma.com)
[![Gemini](https://img.shields.io/badge/LLM-Gemini%20Flash%20Lite-4285F4.svg)](https://ai.google.dev)

**SHIRO** is a university coursework and syllabus question-answering system. It couples **CPU-accelerated local ONNX dense retrieval**, **high-concurrency Spring Boot WebFlux reactive orchestration**, and a **Claude-style React interface** to deliver accurate, grounded academic explanations, diagrams, and Previous Year Questions (PYQs) without hallucinations.

---

## 📑 Table of Contents

- [System Architecture](#-system-architecture)
- [Key Features](#-key-features)
- [Repository Structure](#-repository-structure)
- [Component Breakdown](#-component-breakdown)
  - [1. Python Retrieval Sidecar (:8001)](#1-python-retrieval-sidecar-8001)
  - [2. Spring Boot Reactive Backend (:8080)](#2-spring-boot-reactive-backend-8080)
  - [3. React Claude-Style Frontend (:5173)](#3-react-claude-style-frontend-5173)
- [Data & Embeddings Pipeline](#-data--embeddings-pipeline)
- [Environment Configuration](#-environment-configuration)
- [Getting Started](#-getting-started)
  - [Prerequisites](#prerequisites)
  - [Quickstart with Single Command](#quickstart-with-single-command)
  - [Manual Step-by-Step Launch](#manual-step-by-step-launch)
- [Docker & Cloud Deployment](#-docker--cloud-deployment)
- [Evaluation & Grounding Benchmark](#-evaluation--grounding-benchmark)
- [License](#-license)

---

## 🏛️ System Architecture

```
┌───────────────────────────────────────────────────────────────────────────┐
│                        React (Vite) SPA Frontend                          │
│   • Claude-Style Center-Column Chat UI  • Light & Dark Warm Tonal Themes  │
│   • Progressive Markdown SSE Stream      • Interactive Source Citations   │
│   • Mermaid Diagrams & Slide Popups      • Subject & Semester Filtering   │
└─────────────────────────────────────┬─────────────────────────────────────┘
                                      │ HTTP SSE / JSON (Port 5173 ➔ 8080)
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    Spring Boot 3.4 WebFlux Backend                        │
│   • Reactive Non-blocking SSE Pipeline (/api/chat)                        │
│   • Grounded Syllabus Prompt Synthesis & Citation Tracking                │
│   • Direct REST Streaming to Gemini 3.1 Flash Lite                        │
│   • Exponential Backoff & Rate Limit Handling                             │
│   • Persistent Chat Session Storage & Multi-part Uploads                  │
└──────────────────┬────────────────────────────────────────────────────────┘
                   │
                   │ Server-to-Server Internal POST /retrieve (Port 8001)
                   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    Python FastAPI Retrieval Sidecar                       │
│   • FastEmbed ONNX: BAAI/bge-small-en-v1.5 (CPU-Optimized Embeddings)    │
│   • Vector Index: ChromaDB (95,672+ Chunks, 68 Subjects, 8 Semesters)    │
│   • Hybrid Retrieval: Vector Cosine Similarity + SQLite FTS5 Keyword Match│
│   • Topic-wise PYQ (Previous Year Questions) Search                       │
│   • Diagram & Slide Image Extraction & Serving (/images)                  │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## ✨ Key Features

- **Strict Grounding & Zero Hallucination**: Automatically refrains from answering queries outside the university syllabus bounds. Cites exact document names, units, and page numbers.
- **FastEmbed ONNX Retrieval**: Runs dense vector embedding inference locally using ONNX runtime without requiring heavy GPU/PyTorch setups.
- **Hybrid Search & PYQ Matching**: Combines vector cosine similarity with SQLite full-text search (FTS5) to fetch both conceptual lecture chunks and past exam questions.
- **Reactive Streaming (SSE)**: Spring Boot WebFlux pipes token streams from Gemini directly to the React client with minimal latency and high concurrency.
- **Visual Learning Support**: Renders inline Mermaid architectural flowcharts, mathematical formulas (KaTeX/Markdown), and interactive slide/diagram image popups.
- **Subject & Semester Scoping**: Dynamic filtering modal mapping across all 8 university semesters and 68+ engineering subjects.

---

## 📁 Repository Structure

```
.
├── backend/                       # Spring Boot 3.4 WebFlux Orchestration Service
│   ├── pom.xml
│   ├── src/main/java/com/thehelper/rag/
│   │   ├── TheHelperRagApplication.java
│   │   ├── config/                # AppProperties, WebClientConfig, CorsConfig
│   │   ├── controller/            # ChatController, ThreadController, MetadataController, UploadController
│   │   ├── model/                 # ChatRequest, ChatEvent, RetrievedChunk, ThreadRecord, etc.
│   │   └── service/               # RetrievalService, GeminiStreamService, ThreadStorageService, FileUploadService
│   └── src/main/resources/
│       └── application.yml        # Reactive Netty & Service Configuration
├── sidecar/                       # Python FastAPI Vector & PYQ Retrieval Engine
│   ├── sidecar_app.py             # ChromaDB + FastEmbed ONNX + SQLite FTS5 (:8001)
│   ├── extract_images.py          # Slide and textbook diagram extractor
│   └── requirements.txt           # fastapi, uvicorn, chromadb, fastembed, pydantic
├── frontend/                      # React 18 (Vite) Claude-Style UI
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── App.jsx                # Main workspace container
│       ├── App.css                # Warm tonal design tokens, responsive layout
│       ├── components/
│       │   ├── Sidebar.jsx        # Persistent chat history & session switcher
│       │   ├── ChatArea.jsx       # Message stream container
│       │   ├── MessageItem.jsx    # Markdown, citations & diagram renderer
│       │   ├── InputBox.jsx       # Multiline input, subject pill & file upload
│       │   ├── MermaidDiagram.jsx # Dynamic flowchart & diagram renderer
│       │   ├── ImageModal.jsx     # Fullscreen textbook/slide diagram preview
│       │   └── SubjectSelectorModal.jsx # Semester & subject picker modal
│       ├── data/
│       │   └── srm_curriculum.json# Full university curriculum taxonomy
│       └── services/
│           └── api.js             # SSE stream reader and REST endpoints
├── data/                          # Data, Embeddings & Database Artifacts
│   ├── chroma_db/                 # Chroma vector database (95k+ chunks)
│   ├── the_helper_rag.db          # SQLite relational corpus & PYQ database
│   ├── images/                    # Extracted diagrams and visual assets
│   └── manifest.json              # Subject, unit, and document metadata index
├── eval/                          # Grounding & Quality Evaluation Suite
│   ├── eval_suite.py              # Automated 10-point test runner
│   ├── professor_eval_suite.py    # In-depth academic rigor benchmark
│   └── eval_results.json          # Benchmark output reports
├── Dockerfile                     # Multi-stage production container
├── docker-entrypoint.sh           # Container entrypoint with auto-decompression
├── start.sh                       # Unified single-command launcher
├── render.yaml                    # Cloud deployment blueprint
└── README.md
```

---

## 🧩 Component Breakdown

### 1. Python Retrieval Sidecar (`:8001`)
- **FastEmbed ONNX**: Uses `BAAI/bge-small-en-v1.5` to convert user queries into 384-dimensional dense vectors on CPU in milliseconds.
- **ChromaDB Vector Store**: Searches through pre-chunked course materials partitioned by semester and subject.
- **SQLite Hybrid Search**: Evaluates FTS5 tables and exact matches in `the_helper_rag.db` to extract corresponding Previous Year Exam Questions (PYQs).
- **Image Manifest Integration**: Resolves figure references and provides direct image URLs for lecture slides.

### 2. Spring Boot Reactive Backend (`:8080`)
- **Reactive Engine**: Built on Project Reactor (`Flux` and `Mono`) running on Netty for non-blocking I/O.
- **Prompt Engineering**: Injects retrieved context, curriculum units, source citations, and conversation history into a structured academic prompt.
- **Gemini Streaming**: Pushes chunks over Server-Sent Events (`text/event-stream`) to the frontend with rate-limit retries.
- **Persistence**: Manages conversation threads in `data/threads.json` and supports file attachments.

### 3. React Claude-Style Frontend (`:5173`)
- **Center-Column Stream**: Displays progressive token output with smooth auto-scroll.
- **Interactive Citations**: Clickable source badges displaying the document name, unit, and page number.
- **Mermaid & Visuals**: Live-rendered diagrams and expandable slide graphics.
- **Thread Management**: Create, rename, switch, and delete conversation sessions seamlessly.

---

## ⚙️ Environment Configuration

Create a `.env` file in the root directory (or use `.env.example` as a template):

```bash
# Frontend
VITE_API_URL=http://127.0.0.1:8080

# Backend (Spring Boot)
GEMINI_API_KEY=your_google_gemini_api_key_here
GEMINI_MODEL=gemini-3.1-flash-lite
SIDECAR_URL=http://127.0.0.1:8001
DATA_DIR=./data

# Sidecar (Python)
EMBEDDINGS_DIR=./data
```

---

## 🚀 Getting Started

### Prerequisites
- **Python 3.10+** (with `pip`)
- **Java 21 JDK** (OpenJDK 21 recommended) & **Maven**
- **Node.js 18+** & **npm**
- **Google Gemini API Key** ([Get one from Google AI Studio](https://aistudio.google.com/))

### Quickstart with Single Command

Run the unified startup script from the project root:
```bash
./start.sh
```
This script validates dependencies, starts the Python retrieval sidecar on port `8001`, starts the Spring Boot backend on port `8080`, and launches the Vite React frontend on port `5173`.

---

### Manual Step-by-Step Launch

If you prefer running services in separate terminals:

#### Step 1: Start Python Retrieval Sidecar
```bash
cd sidecar
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python sidecar_app.py
```
*Health Check:* `curl http://127.0.0.1:8001/health`

#### Step 2: Build & Start Spring Boot Backend
```bash
cd backend
mvn clean package -DskipTests
java -jar target/chiroshiro-backend-1.0.0.jar
```
*Health Check:* `curl http://127.0.0.1:8080/api/health`

#### Step 3: Start React Frontend
```bash
cd frontend
npm install
npm run dev -- --host 127.0.0.1 --port 5173
```
Open **http://127.0.0.1:5173** in your browser.

---

## 🐳 Docker & Cloud Deployment

### Docker Build & Run
The included `Dockerfile` bundles the Python ONNX sidecar and Spring Boot backend into a single container:

```bash
# Build the Spring Boot JAR first
cd backend && mvn clean package -DskipTests && cd ..

# Build Docker container
docker build -t shiro-academic-ai .

# Run container
docker run -d -p 8080:8080 -e GEMINI_API_KEY="your_api_key" shiro-academic-ai
```

The container automatically unzips split ChromaDB archives (`chroma_db.tar.gz.part_*`) and SQLite databases upon boot via `docker-entrypoint.sh`.

---

## 📊 Evaluation & Grounding Benchmark

The repository includes automated test suites to ensure strict syllabus adherence and zero hallucination.

```bash
python3 eval/eval_suite.py
```

### Benchmark Results
- **In-Scope Concept Accuracy**: **100%** (Correctly grounds operating systems, DBMS, math, calculus, and physics concepts with unit & page citations).
- **Out-of-Scope Refusal Rate**: **100%** (Correctly refuses non-syllabus queries such as cooking recipes or general trivia with appropriate syllabus-boundary notices).

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

