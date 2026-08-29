# 🎓 ChiroShiro (Shiro) — Comprehensive Codebase Defense & Architecture Guide

This living document serves as the master defense and technical blueprint for **ChiroShiro**, a domain-specific RAG system for university engineering curricula.

---

## 🗺️ Phase 1 — Map the Territory

### 1. Repository Inventory

```
ChrioShiro/
├── Dockerfile                         # Multi-stage container build (Maven stage 1 + Python 3.11 JRE stage 2)
├── docker-entrypoint.sh               # Container init: decompresses archives & starts sidecar (:8001) + backend (:8080)
├── start.sh                           # Local multi-service launcher script for sidecar, backend, and Vite frontend
├── render.yaml                        # Deployment blueprint for Render cloud hosting
├── vercel.json                        # Vercel configuration for SPA static routing & backend proxy rewrites
├── .env.example                       # Reference environment variables template
├── README.md                          # Full project overview and documentation
├── backend/                           # Spring Boot 3.4.3 Reactive WebFlux Application (Java 21)
│   ├── pom.xml                        # Maven configuration: Spring WebFlux, Project Reactor, Jackson
│   └── src/main/
│       ├── resources/
│       │   └── application.yml        # Reactive web server config, sidecar URL, Gemini model parameters
│       └── java/com/shiro/rag/
│           ├── ShiroRagApplication.java     # Spring Boot application entry point
│           ├── config/
│           │   ├── AppProperties.java       # Typed configuration bean binding application.yml & env vars
│           │   ├── CorsConfig.java          # Reactive WebFilter configuring global permissive CORS
│           │   └── WebClientConfig.java     # Non-blocking Netty WebClient bean with 16MB in-memory buffer
│           ├── model/
│           │   ├── AttachmentRecord.java    # POJO representing uploaded Gemini multimodal file references
│           │   ├── ChatEvent.java           # SSE envelope model with static factories ('sources', 'token', 'done', 'error')
│           │   ├── ChatRequest.java         # Primary incoming chat request DTO (message, thread, modes, history)
│           │   ├── ChunkMetadata.java       # Source citation metadata (subject, semester, file_name, unit, page)
│           │   ├── MessageRecord.java       # Individual chat message record (role, content, timestamp, citations)
│           │   ├── RetrieveRequest.java     # DTO for dispatching queries to Python sidecar
│           │   ├── RetrieveResponse.java    # DTO capturing list of retrieved chunks from sidecar
│           │   ├── RetrievedChunk.java      # Model for a retrieved text chunk with distance and similarity scores
│           │   ├── SessionSummary.java      # Model for past study session summary (id, title, subject, questions)
│           │   └── ThreadRecord.java        # Full conversation thread entity containing message list
│           ├── service/
│           │   ├── FileUploadService.java   # 2-step resumable binary streaming upload to Google Gemini Files API
│           │   ├── GeminiStreamService.java # Reactive SSE client streaming tokens from Gemini with backoff retry
│           │   ├── RetrievalService.java    # WebClient reactive proxy calling sidecar /retrieve and /metadata
│           │   └── ThreadStorageService.java# Local JSON thread persistence service (threads.json) with 20-session pruning
│           └── controller/
│               ├── ChatController.java      # Core orchestration controller: context enrichment, RAG prompt synth, SSE stream
│               ├── MetadataController.java  # Exposes /api/metadata (proxied from sidecar) and /api/health
│               ├── ThreadController.java    # REST endpoints (/api/threads) for server-side thread management
│               └── UploadController.java    # Multipart reactive endpoint (/api/upload) bridging files to Gemini
├── sidecar/                           # Python 3.11 FastAPI Retrieval Microservice
│   ├── requirements.txt               # Dependencies: fastapi, uvicorn, fastembed, chromadb, pydantic, pymupdf, python-pptx
│   ├── sidecar_app.py                 # Core FastAPI app: ONNX BGE embedding, ChromaDB vector query, SQLite FTS5 PYQ engine
│   └── extract_images.py              # CLI batch extractor: parses PDF/PPTX slide figures into data/images/
├── frontend/                          # React 18 + Vite Single Page Application (Blackboard / Claude Theme)
│   ├── package.json                   # Dependencies: react, react-markdown, remark-math, rehype-katex, mermaid, lucide-react
│   ├── vite.config.js                 # Vite bundler build config and dev server proxy
│   ├── index.html                     # HTML5 shell loading KaTeX stylesheets and Google Fonts
│   ├── src/
│   │   ├── main.jsx                   # React root bootstrap
│   │   ├── App.jsx                    # Top-level state coordinator: active thread, study modes, streaming lifecycle
│   │   ├── App.css                    # Warm-light & dark theme stylesheets, KaTeX blackboard typography, bubble layouts
│   │   ├── services/
│   │   │   └── api.js                 # Client-side LocalStorage thread manager and SSE stream consumer (fetch + ReadableStream)
│   │   ├── data/
│   │   │   └── srm_curriculum.json    # Static 8-semester course catalog hierarchy with 68 subjects
│   │   └── components/
│   │       ├── Sidebar.jsx            # Collapsible navigation drawer listing past study threads & new chat trigger
│   │       ├── ChatArea.jsx           # Main message scroll viewport, mode pills, welcome empty state, and prompt suggestions
│   │       ├── InputBox.jsx           # Expanding textarea, study mode switchers, and drag-and-drop file upload tray
│   │       ├── MessageItem.jsx        # Markdown renderer with remark-math/KaTeX preprocessing, copy buttons, and attachments
│   │       ├── MermaidDiagram.jsx     # Dynamic client-side SVG renderer for ```mermaid flowcharts and mindmaps
│   │       ├── SubjectSelectorModal.jsx# Filter modal for selecting semester and focus subject
│   │       └── ImageModal.jsx         # Fullscreen lightbox overlay for inspecting extracted diagrams and attachments
├── data/                              # Offline Academic Data Assets & Indexes
│   ├── manifest.json                  # Corpus index manifest: 68 subjects, 95,672 chunks, BGE model specs
│   ├── images_manifest.json           # Extracted figure mapping linking documents/pages to image URLs
│   ├── the_helper_rag.db              # SQLite database (WAL mode) with `chunks`, `pyq_questions` (35.9k), `exam_papers` (353) & FTS5 indexes
│   ├── the_helper_rag.db.gz           # Compressed database artifact for container and distribution builds
│   ├── chroma_db.tar.gz.part_a[a-e]   # Split multi-part archive containing pre-computed ChromaDB vector index (95k chunks)
│   ├── chroma_db/                     # Unpacked ChromaDB vector store directory (used at runtime)
│   └── images/                        # Extracted slide and textbook PNG diagrams served via sidecar
└── eval/                              # Automated Benchmark & Quality Evaluation Suites
    ├── eval_suite.py                  # Syllabus grounding test suite checking retrieval keywords & out-of-scope refusals
    ├── professor_eval_suite.py        # End-to-end integration test validating file uploads, PYQ formatting, and multi-turn context
    └── eval_results.json              # Recorded test benchmark outputs and metrics
```

---

### 2. Request & Data-Flow Story

#### Step 1: User Action & Client Packaging (Frontend)
1. The student navigates the React SPA (`App.jsx`). They can select an active **Study Mode** (`notes`, `pyqs`, `learn_basics`, or `all`), focus on a specific university course (e.g., *Operating Systems*), or attach a slide/diagram/PDF.
2. If attaching a file, `InputBox.jsx` immediately calls `uploadFile()` in `api.js` $\rightarrow$ `POST /api/upload` on Spring Boot. `UploadController` reads reactive byte streams via `DataBufferUtils.join()` and forwards the payload to `FileUploadService.java`.
3. `FileUploadService` initiates a 2-step resumable upload to the **Google Gemini Files API** (`generativelanguage.googleapis.com/upload/v1beta/files`), obtaining a `fileUri` (e.g. `https://generativelanguage.googleapis.com/v1beta/files/...`).
4. When the user sends a message, `api.js` executes `streamChat()` via `POST /api/chat`, packaging:
   - Current user prompt.
   - Active thread history (array of `{role, content}` objects from `localStorage`).
   - Private past session summaries (`buildLocalUserSessions()`).
   - Attached file records.
   - Selected subject & study mode metadata.

#### Step 2: Ingestion, Normalization & Parallel Retrieval (Spring Boot Backend)
5. `ChatController.java` (`POST /api/chat`) acts as the reactive orchestration core:
   - **Greeting Interception**: If the prompt is a casual greeting (`isConversationalOrGreeting`), it bypasses RAG retrieval to avoid polluting the prompt with unsolicited lecture dumps.
   - **Subject Normalization**: If no subject is explicitly selected, `detectSubjectFromText()` evaluates regex patterns over 40+ academic aliases (e.g. `"dsa"` $\rightarrow$ `"Data Structures And Algorithm"`, `"os"` $\rightarrow$ `"Operating Systems"`), or falls back to prior turns in `priorMessages`.
   - **Context Enrichment**: If the query is an ambiguous follow-up (e.g., `"explain next"`, `"more questions on this"`), `enrichRetrievalQueryWithHistory()` prepends context from the previous user turn.
6. `ChatController` issues asynchronous reactive `WebClient` requests to the Python Retrieval Sidecar (`:8001`):
   - **Primary Retrieval**: `Mono<RetrieveResponse>` targeting ChromaDB vector search or notes.
   - **PYQ Retrieval**: If the query or study mode is exam/PYQ related, a parallel `Mono<List<RetrievedChunk>>` is dispatched.
   - `Mono.zip(primaryRetrieveMono, pyqRetrieveMono)` awaits both in a non-blocking Reactor pipeline.

#### Step 3: Vector & Relational Query Execution (Python Sidecar)
7. Inside `sidecar/sidecar_app.py` (`POST /retrieve`):
   - **Full Exam Paper Match**: If the student asks for an entire paper (`is_full_paper_query`), `retrieve_full_exam_paper_sql()` executes a SQL query on the `exam_papers` table with year/subject filtering.
   - **Topic-Wise PYQ Search**: If in `pyqs` mode, `retrieve_topic_pyqs_sql()` executes high-speed BM25 full-text search against the SQLite FTS5 table `pyq_questions_fts` joined on `pyq_questions`, enforcing strict subject isolation.
   - **Dense Semantic Retrieval**: For concept queries, the local ONNX model `BAAI/bge-small-en-v1.5` (`fastembed`) encodes the query into a 384-dimensional vector. ChromaDB executes cosine similarity search over `the_helper_docs` (95,672 pre-indexed chunks), applying metadata filters (`subject`, `semester`, `category`).

#### Step 4: Prompt Synthesis & Reactive LLM Streaming
8. `ChatController.java` receives the retrieved chunks, filters out cross-subject noise (`filterChunksBySubject`), and calls `buildGroundedPrompt()`:
   - Binds syllabus context, authentic PYQs with strict un-truncated formatting instructions, attached Gemini file URIs, and user past session summaries.
9. Spring Boot immediately emits the first SSE event to the client:
   - `event: sources` containing structured chunk metadata (file name, unit, subject, page number, similarity score).
10. `GeminiStreamService.java` opens a streaming HTTP POST (`alt=sse`) to `gemini-3.6-flash`.
11. As Gemini yields SSE chunks, Netty streams raw token chunks through a Reactor `Flux`. Jackson parses the JSON candidates and pushes `event: token` SSE messages in real-time to the browser.
12. If a transient HTTP 429 rate limit is encountered, `Retry.backoff(4, Duration.ofSeconds(2))` intercepts the error and retries transparently.
13. Upon stream completion, `ChatController` emits `event: done`.

#### Step 5: Progressive Client Rendering (Frontend)
14. In `api.js`, the browser's `ReadableStream` reader parses SSE lines:
   - `sources` updates the active assistant message's reference cards.
   - `token` incrementally appends text to `content`.
15. `MessageItem.jsx` feeds the streaming text through `preprocessMarkdown()`:
   - Corrects LaTeX delimiters (glued `$$`, missing fences around `\begin{cases}`, blockquote `>` prefix stripping).
   - `react-markdown` + `rehype-katex` renders display and inline formulas.
   - `MermaidDiagram.jsx` detects ```mermaid blocks and dynamically renders interactive SVG diagrams and mindmaps.
16. On completion (`done`), `saveThreadMessages()` writes the full conversation back into `localStorage`.

---

### 3. Load-Bearing Logic vs. Scaffolding / Boilerplate

| Component | Status | Classification | Rationale & Code Evidence |
|---|---|---|---|
| **`ChatController.java`** | **Active** | **Load-Bearing** | Central orchestrator. Manages prompt engineering, subject alias auto-detection (L319-428), query history enrichment (L465-494), parallel retrieval zip (L218), and SSE dispatch. |
| **`GeminiStreamService.java`** | **Active** | **Load-Bearing** | Direct SSE streaming from Google Gemini API with exponential backoff retry on HTTP 429/5xx (L77-80). |
| **`FileUploadService.java`** | **Active** | **Load-Bearing** | Multi-part binary streaming bridge to Gemini Files API (L32-95), returning persistent cloud URIs for multimodal reasoning. |
| **`sidecar_app.py`** | **Active** | **Load-Bearing** | Core retrieval engine. Houses FastEmbed ONNX embedding, ChromaDB vector store, and SQLite FTS5 PYQ matching (35.9k questions). |
| **`MessageItem.jsx` (`preprocessMarkdown`)** | **Active** | **Load-Bearing** | 100+ lines of regex AST pre-cleaning (L38-141) essential to prevent KaTeX and Remark parsing crashes on streaming LaTeX/Mermaid. |
| **`api.js` (Client-Side Storage)** | **Active** | **Load-Bearing** | Manages local storage isolation (`shiro_user_threads_v2`), building per-user session summaries without cross-tenant leakage. |
| **`ThreadStorageService.java`** | *Hybrid / Legacy* | *Scaffolding / Partially Dead* | Backend disk persistence (`threads.json`). While fully functional with CRUD and session pruning, the frontend stores threads in `localStorage` for client isolation. Method `buildSessionMemoryContext` (L171) is dead code replaced by `ChatController.buildUserSessionMemoryContext`. |
| **`ThreadController.java`** | *Dormant* | *Scaffolding* | Exposes `/api/threads` REST endpoints. Kept for backend test suites and API completeness, but unused by the production React SPA. |
| **`extract_images.py`** | *Offline Tooling* | *Scaffolding / Ingestion* | Batch extraction script for offline pipeline processing; not part of the live query serving path. |
| **`CorsConfig.java` / `WebClientConfig.java`** | **Active** | *Standard Boilerplate* | Standard Spring WebFlux reactive configuration beans. |

---
