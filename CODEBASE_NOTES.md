# 🎓 Shiro — Comprehensive Codebase Defense & Architecture Guide

This living document serves as the master defense and technical blueprint for **Shiro**, a domain-specific RAG system for university engineering curricula.

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
│       └── java/com/thehelper/rag/
│           ├── TheHelperRagApplication.java # Spring Boot application entry point
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

### 2. Deep-Dive End-to-End Data Flow Story

The data flow spans four interconnected environments:
1. **Client Browser (React 18 SPA)**
2. **Spring Boot Reactive Orchestrator (Netty Engine on :8080)**
3. **Python FastAPI Retrieval Sidecar (Port :8001)**
4. **Google Gemini Foundation Models (Cloud AI API)**

```
+---------------------------------------------------------------------------------------------------------+
|                                        1. BROWSER CLIENT (React SPA)                                    |
|                                                                                                         |
|  [User Query] + [Active Study Mode] + [Attached PDF/Img] + [Thread Messages] + [User Session Summaries] |
+---------------------------------------------------+-----------------------------------------------------+
                                                    |
                         (A) Multipart Upload       |       (B) POST /api/chat (SSE Request)
                             POST /api/upload       |           Accept: text/event-stream
                                                    |
+---------------------------------------------------v-----------------------------------------------------+
|                                 2. SPRING BOOT WEBFLUX BACKEND (:8080)                                  |
|                                                                                                         |
|  UploadController (FilePart) ──► FileUploadService ──► Google Gemini Files API (Returns file_uri)       |
|                                                                                                         |
|  ChatController.chat(ChatRequest):                                                                      |
|   1. Intent Guard: isConversationalOrGreeting() -> Short-circuits RAG on greetings                     |
|   2. Subject Normalizer: detectSubjectFromText() regex over 40+ academic aliases (e.g. 'os' -> OS)     |
|   3. Query Enrichment: enrichRetrievalQueryWithHistory() prepends prior turn to ambiguous follow-ups    |
|   4. Reactive Fan-out: Mono.zip(PrimaryRetrievalMono, PyqRetrievalMono)                                 |
+---------------------------------------------------+-----------------------------------------------------+
                                                    |
                                                    | HTTP POST http://127.0.0.1:8001/retrieve
                                                    v
+---------------------------------------------------------------------------------------------------------+
|                                3. PYTHON FASTAPI RETRIEVAL SIDECAR (:8001)                              |
|                                                                                                         |
|  sidecar_app.py /retrieve:                                                                              |
|   ├─► IF is_full_paper_query: SQL regex query on `exam_papers` table                                    |
|   ├─► IF study_mode == 'pyqs': SQL BM25 match on `pyq_questions_fts` (35,909 exam questions)            |
|   └─► ELSE: FastEmbed ONNX (BAAI/bge-small-en-v1.5) -> 384-d vector -> ChromaDB `the_helper_docs` (95k) |
+---------------------------------------------------+-----------------------------------------------------+
                                                    |
                                                    | RetrieveResponse (JSON Chunks + Metadata)
                                                    v
+---------------------------------------------------------------------------------------------------------+
|                               4. PROMPT SYNTHESIS & LLM STREAMING (Backend)                             |
|                                                                                                         |
|  ChatController:                                                                                        |
|   1. Cross-Subject Noise Filter: filterChunksBySubject()                                                |
|   2. Grounding Prompt Builder: buildGroundedPrompt() binds units, PYQ formats, file_uris                |
|   3. System Instruction Synthesis: Injects student private session memory into SYSTEM_INSTRUCTION        |
|   4. Immediate SSE Emission: createSseEvent(ChatEvent.sources(...)) sent to client                      |
|                                                                                                         |
|  GeminiStreamService:                                                                                   |
|   - Reactive WebClient opens SSE stream to Google Gemini (gemini-3.6-flash)                             |
|   - Resilient Retry.backoff(4, 2s) catches HTTP 429 rate limits & 5xx errors                            |
|   - Jackson extracts text fragments -> Emitted in real-time as `event: token`                           |
|   - On completion -> Emits `event: done`                                                                |
+---------------------------------------------------+-----------------------------------------------------+
                                                    |
                                                    | Real-Time SSE Stream (sources -> tokens -> done)
                                                    v
+---------------------------------------------------------------------------------------------------------+
|                                  5. CLIENT-SIDE STREAM CONSUMPTION (React)                              |
|                                                                                                         |
|  api.js: ReadableStream lines parsed -> Dispatches onSources, onToken, onDone                           |
|  MessageItem.jsx: preprocessMarkdown() cleans LaTeX delimiters ($$), \begin{cases}, blockquotes (>)     |
|  Renderer: rehype-katex (Math formulas) + MermaidDiagram.jsx (Dynamic SVG graphs/mindmaps)              |
|  State: saveThreadMessages() syncs full thread state to LocalStorage ('shiro_user_threads_v2')          |
+---------------------------------------------------------------------------------------------------------+
```

---

#### Detailed Step-by-Step Data Execution Path

##### Phase A: Multimodal Asset Attachment (Optional User File Upload)
1. **User Action**: The student drags-and-drops a class lecture slide, handwritten math problem, or assignment PDF into `InputBox.jsx`.
2. **Client Dispatch**: `InputBox.jsx` immediately calls `uploadFile(file)` in [`api.js:114`](file:///Users/rishii/ChrioShiro/frontend/src/services/api.js#L114). A standard `multipart/form-data` POST request is fired to `/api/upload`.
3. **Reactive Netty Handling**: In [`UploadController.java:27-47`](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/UploadController.java#L27-L47), Spring WebFlux ingests the `FilePart`. Instead of blocking a thread or dumping to a local temp file, `DataBufferUtils.join()` asynchronously accumulates the reactive byte buffers into a single in-memory byte array `byte[]`.
4. **Resumable Google Gemini Upload**: `UploadController` delegates to [`FileUploadService.java:32-95`](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/service/FileUploadService.java#L32-L95):
   - **Step 1 (Session Handshake)**: POST to `https://generativelanguage.googleapis.com/upload/v1beta/files?key=GEMINI_API_KEY` with headers `X-Goog-Upload-Protocol: resumable`, `X-Goog-Upload-Command: start`, and the file's MIME type + content length. Gemini returns an upload session URI via header `X-Goog-Upload-URL`.
   - **Step 2 (Binary Transmission)**: WebClient pushes the raw binary stream to the received `X-Goog-Upload-URL` with `X-Goog-Upload-Command: upload, finalize`.
   - **Step 3 (URI Extraction)**: Gemini returns JSON metadata with a cloud-accessible `file.uri` (e.g., `https://generativelanguage.googleapis.com/v1beta/files/abc123xyz`).
5. **Client Response**: `UploadController` returns an [`AttachmentRecord`](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/model/AttachmentRecord.java) JSON payload containing `fileUri`, `mimeType`, `displayName`, and `sizeBytes`. The frontend UI adds an attachment chip to the input tray.

---

##### Phase B: User Submission & Client Packaging
6. **Trigger**: The student types a question (or clicks a topic starter) and presses Enter.
7. **Client State Assembly**: [`App.jsx:131-239`](file:///Users/rishii/ChrioShiro/frontend/src/App.jsx#L131-L239) and [`api.js:131-168`](file:///Users/rishii/ChrioShiro/frontend/src/services/api.js#L131-L168) bundle:
   - `message`: The raw text query.
   - `threadId`: Persistent UUID identifying this chat thread.
   - `messages`: Chronological array of all previous turns in the active thread (`[{role, content}]`).
   - `userSessions`: Array of up to 10 previous study thread summaries (`[{id, title, subject, questions}]`) retrieved from browser `localStorage` by `buildLocalUserSessions()`.
   - `studyMode`: Active mode (`notes`, `pyqs`, `learn_basics`, or `all`).
   - `subject`: Selected course focus (e.g., `"Operating Systems"`), if any.
   - `attachments`: Array of `AttachmentRecord` objects containing the Gemini file URIs.
8. **SSE Dispatch**: `api.js` executes `fetch('/api/chat', { method: 'POST', headers: { 'Accept': 'text/event-stream' }, body: ... })`.

---

##### Phase C: Spring Boot Orchestration & Request Enrichment
9. **Endpoint Entry**: [`ChatController.java:120`](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L120) receives `ChatRequest`.
10. **Conversational Intent Guard**: `isConversationalOrGreeting()` ([L308-317](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L308-L317)) evaluates regex matchers for pleasantries (e.g., `"hey"`, `"hello"`, `"who are you"`, `"thanks"`). If matched and no attachments exist, RAG vector retrieval is bypassed entirely to avoid hallucinated course dumps.
11. **Subject Alias Normalization**: If the user didn't explicitly select a subject in the modal, `detectSubjectFromText()` ([L418-428](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L418-L428)) matches whole words against `SUBJECT_ALIASES` ([L319-416](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L319-L416)), mapping shorthand abbreviations and concepts (e.g., `"dsa"` $\rightarrow$ `"Data Structures And Algorithm"`, `"paging"` $\rightarrow$ `"Operating Systems"`, `"cayley hamilton"` $\rightarrow$ `"Calculus And Linear Algebra"`). If not in the current message, it scans prior messages backwards ([L168-188](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L168-L188)) to maintain thread subject context.
12. **Multi-Turn Query Enrichment**: If the student enters an ambiguous follow-up (e.g., `"give more questions on this"`, `"what about worst case?"`, `"explain next"`), `enrichRetrievalQueryWithHistory()` ([L465-494](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L465-L494)) searches backwards through `priorMessages` and prepends the antecedent subject/topic into the retrieval query string.
13. **Parallel Reactive Retrieval Fan-Out**:
    - `primaryRetrieveMono`: WebClient POST to Sidecar `:8001/retrieve` with `{question, k: 5, subject, category, studyMode}`.
    - `pyqRetrieveMono`: If `studyMode == 'pyqs'` or `isPyqRelated(query)`, a dedicated second WebClient call queries specifically for authentic exam questions.
    - Both Monos are composed via `Mono.zip(primaryRetrieveMono, pyqRetrieveMono)` ([L218](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L218)), executing non-blockingly and asynchronously.

---

##### Phase D: Python Retrieval Sidecar Execution
14. **Endpoint Entry**: [`sidecar_app.py:560`](file:///Users/rishii/ChrioShiro/sidecar/sidecar_app.py#L560) receives `RetrieveRequest`.
15. **Query Routing**:
    - **Path 1: Full Exam Paper Retrieval**: If `is_full_paper_query()` ([L455-466](file:///Users/rishii/ChrioShiro/sidecar/sidecar_app.py#L455-L466)) detects terms like `"full question paper 2024"`, `retrieve_full_exam_paper_sql()` queries the SQLite `exam_papers` table directly, retrieving complete university question papers.
    - **Path 2: Topic-Wise PYQ Full-Text Search**: If `is_pyq_mode`, `retrieve_topic_pyqs_sql()` ([L315-425](file:///Users/rishii/ChrioShiro/sidecar/sidecar_app.py#L315-L425)) strips stopwords, tokenizes the prompt, and runs an FTS5 BM25 match against `pyq_questions_fts` joined on `pyq_questions` (35,909 records), enforcing strict `q.subject = ?` isolation.
    - **Path 3: Dense Vector Semantic Search**: For conceptual queries:
      - `FastEmbed` ONNX runtime (`BAAI/bge-small-en-v1.5`) embeds the query into a 384-dimensional dense float vector on CPU.
      - ChromaDB queries the `the_helper_docs` collection (95,672 chunks) using cosine similarity.
      - Metadata filters (`where = {"$and": [{"subject": ...}, {"semester": ...}]}`) restrict the candidate search space.
      - Any chunks with cosine similarity $< 0.25$ (`MIN_SIMILARITY_THRESHOLD`) are pruned.
16. **Response Output**: A [`RetrieveResponse`](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/model/RetrieveResponse.java) JSON array containing chunk text, similarity score, document filename, unit, and page numbers is returned to Spring Boot.

---

##### Phase E: Prompt Synthesis & Reactive LLM Streaming
17. **Cross-Subject Post-Filter**: `filterChunksBySubject()` ([L430-450](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L430-L450)) cleans any stray vector results to eliminate syllabus contamination.
18. **Grounded Prompt Construction**: `buildGroundedPrompt()` ([L496-591](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L496-L591)) generates structured context:
    - Binds course reference notes with document titles and page citations.
    - Injects authentic past exam questions with mandatory rules: *print complete questions in full before solutions; never truncate MCQs*.
    - Injects pedagogical guidelines based on the active mode (`learn_basics`: intuition + analogies; `pyqs`: authentic exam structure; `notes`: curriculum theory).
19. **Student Memory Injection**: `buildUserSessionMemoryContext()` ([L593-617](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L593-L617)) formats the student's recent session summaries and appends them to the persona system instruction (`SYSTEM_INSTRUCTION`).
20. **Multimodal Conversation Assembly**: Builds the Gemini payload `contents` list containing all prior turns plus any previous or current `file_data` object references (`{mime_type, file_uri}`).
21. **SSE Stream Initiation**:
    - **Event 1 (`sources`)**: `ChatController` immediately emits `ServerSentEvent.builder().event("sources").data(json).build()` ([L275](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L275)), sending the citation list to the UI before LLM generation starts.
    - **Streaming Tokens (`token`)**: `GeminiStreamService.streamGenerateContent()` ([L41-82](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/service/GeminiStreamService.java#L41-L82)) initiates a streaming HTTP POST with `alt=sse` to `gemini-3.6-flash`.
    - **429 Rate-Limit Interceptor**: Backed by `Retry.backoff(4, Duration.ofSeconds(2)).filter(this::isRateLimitOrTransientError)`, any Google API throttling triggers an automatic exponential backoff retry.
    - **Token Extraction**: Netty streams SSE chunks; `extractTextFromJson()` ([L95-131](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/service/GeminiStreamService.java#L95-L131)) uses Jackson to parse `candidates[0].content.parts[0].text` and emits each token as `event: token`.
    - **Event Last (`done`)**: Upon completion of the upstream Flux, `ChatController` emits `event: done` ([L296-298](file:///Users/rishii/ChrioShiro/backend/src/main/java/com/thehelper/rag/controller/ChatController.java#L296-L298)).

---

##### Phase F: Frontend Stream Processing, Markdown Sanitation & Rendering
22. **SSE Parsing**: In [`api.js:175-231`](file:///Users/rishii/ChrioShiro/frontend/src/services/api.js#L175-L231), the `ReadableStream` reader continuously decodes byte buffers, splits on `\n`, identifies `event:` and `data:`, and invokes callbacks:
    - `onSources(sources)`: Renders citation badges (subject, document name, page number, similarity score) in the assistant message bubble.
    - `onToken(token)`: Progressively concatenates tokens into the active message string in React state.
    - `onDone()`: Signals the completion of streaming.
23. **Markdown & KaTeX AST Sanitation**: In [`MessageItem.jsx:38-141`](file:///Users/rishii/ChrioShiro/frontend/src/components/MessageItem.jsx#L38-L141), `preprocessMarkdown()` runs 10 sequential AST cleanup rules before passing content to `react-markdown`:
    - Fixes glued closing `$$` tags attached to words.
    - Detects un-delimited LaTeX environments (e.g. `\begin{cases}`, `\begin{matrix}`, `\begin{aligned}`) and inserts opening/closing `$$\n...\n$$` blocks.
    - Strips leading Markdown blockquote markers (`>`) from inside math equations to prevent KaTeX syntax crashes.
    - Ensures clean newlines around display equations, Mermaid fences, and Markdown headers (`###`).
24. **Visual & Diagram Rendering**:
    - Mathematical expressions are rendered via `rehype-katex` with HTML/MathML math markup.
    - Fenced blocks tagged ` ```mermaid ` are intercepted by [`MermaidDiagram.jsx`](file:///Users/rishii/ChrioShiro/frontend/src/components/MermaidDiagram.jsx), generating dynamic vector SVG flowcharts and mindmaps.
25. **Persistent Session Storage**: When `onDone()` fires, `saveThreadMessages()` ([`api.js:39-83`](file:///Users/rishii/ChrioShiro/frontend/src/services/api.js#L39-L83)) writes the entire message list, thread title, subject, and timestamp to browser `localStorage` under key `shiro_user_threads_v2`.

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
