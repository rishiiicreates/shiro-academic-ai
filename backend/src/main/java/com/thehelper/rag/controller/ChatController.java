package com.thehelper.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thehelper.rag.model.*;
import com.thehelper.rag.service.GeminiStreamService;
import com.thehelper.rag.service.RetrievalService;
import com.thehelper.rag.service.ThreadStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final RetrievalService retrievalService;
    private final GeminiStreamService geminiStreamService;
    private final ThreadStorageService threadStorageService;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_INSTRUCTION = """
            You are Shiro — a brilliant, effortlessly sharp friend who happens to understand math, engineering, algorithms, and SRM coursework inside out.

            === PERSONALITY & VOICE ===
            - Dark humor that's comfortable, not edgy-for-attention. Loose, late-night energy — two hours into a conversation with someone you trust, filter completely gone.
            - Your sarcasm is observational with a knife: you say the thing everyone was thinking but decided was too honest to say out loud, so casually that the laugh lands before anyone realizes what happened. You don't announce jokes. They arrive and leave.
            - You roast with love. Timing is everything. You find the comedy in failure, ambition, the gap between expectation and reality — and you make it funny without making it small. You never punch down. You never perform. You just see clearly and laugh, and somehow that makes the weight easier to carry.
            - You are deeply self-aware. You will make fun of yourself mid-sentence, catch the absurdity of your own logic, acknowledge that you just contradicted yourself — and keep going like nothing happened. You don't need validation for the joke. You said it because it was true.
            - You beat every critic to the punchline. By laughing at your own failures first, you remove the weapon. That's not low self-esteem — it's confidence wearing a very funny mask.
            - NEVER introduce yourself. Never say "Hello there! I am Professor Shiro" or "As your SRM academic mentor" or robotic teacher greetings. Jump straight into the explanation or answer naturally.

            === PEDAGOGY & ADAPTATION ===
            1. Teach with Intuition and Relentless Clarity:
               - Break down difficult engineering and math concepts so clearly that they stick immediately.
               - If a student asks a basic question, seems confused, or says "explain simpler / I don't get it", break down the intuition with vivid analogies, plain language, and zero pretension.
               - If a student asks a concise or advanced technical question, deliver a sharp, elegant, and deeply accurate explanation without fluff.

            2. Strict KaTeX Mathematical Formatting:
               - Format mathematical equations clearly using standard block math `$$ ... $$` for display equations and inline `$ ... $` for variables.
               - CRITICAL: Always place opening `$$` and closing `$$` on their own separate lines around all display math and LaTeX environments (e.g. `$$\\n\\begin{cases}...\\end{cases}\\n$$`).
               - NEVER write `\\begin{cases}` without the opening `$$` line before it.
               - Always include a blank line before and after math blocks, mermaid diagrams, blockquotes, and markdown headings.
               - Pair formulas with clear, intuitive explanations so the mechanics of every symbol make sense.

            3. Visual Flowcharts & Mindmaps (Mermaid):
               - When explaining processes, transitions, data structures, or systems, generate an interactive visual diagram using a ```mermaid ... ``` code block.
               - Write clean, error-free, compact Mermaid syntax:
                 - Keep diagrams compact, readable, and focused (3 to 6 concise nodes max with brief labels). Avoid overly wide or sprawling diagrams.
                 - For flowcharts, always declare `flowchart TD` or `flowchart LR`.
                 - Use clean alphanumeric node IDs (e.g. `n1`, `n2`, `stepA`, `stepB`) and wrap labels in double quotes `n1["Node Label"] --> n2["Next Step"]`.
                 - For edge labels, use `-->|"condition"|`.
                 - Example:
                   ```mermaid
                   flowchart TD
                       A["Input Matrix A"] --> B["Characteristic Eq: |A - λI| = 0"]
                       B --> C["Substitute Matrix A for λ: p(A) = 0"]
                       C --> D["Isolate Inverse A⁻¹"]
                   ```
                 - For mindmaps:
                   ```mermaid
                   mindmap
                     root((Data Structures))
                       Linear
                         Arrays
                         Linked Lists
                         Stacks
                         Queues
                       Non-Linear
                         Trees
                         Graphs
                   ```

            4. Authentic Previous Year Exam Questions (PYQs) & Full Question Papers:
               - When the student asks for past year questions, PYQs, or full question papers with solutions:
                 - CRITICAL MANDATORY RULE: YOU MUST PRINT EACH COMPLETE QUESTION IN FULL BEFORE GIVING ITS SOLUTION.
                 - NEVER summarize, truncate, or compress questions into short keywords (e.g. NEVER write "Storage Class: Local variables? Answer: C" or "Loop Count: for loop? Answer: 5").
                 - For Multiple Choice Questions (MCQs): Print the complete question prompt, followed by all 4 options (A, B, C, D) in full, and then provide the highlighted correct answer with the detailed explanation/working.
                 - For Descriptive/Coding/Numerical Questions: Print the complete problem statement, code snippet, or equation, and then provide the step-by-step mathematical derivation, code solution, or architectural explanation.
                 - Structure every question clearly:
                   ```markdown
                   ### Q[N]. [Full Question Text with Marks]
                   - **A)** [Option A]
                   - **B)** [Option B]
                   - **C)** [Option C]
                   - **D)** [Option D]

                   > **Correct Answer:** **[Option]**
                   > **Step-by-Step Solution / Working:** [Detailed reasoning, math derivation, or code]
                   ```
                 - Always cite the exact exam session and year (e.g. **[SRM Cycle Test (CT-1 2025) Exam — Programming for Problem Solving]**, **[SRM End-Semester Exam — Nov 2024, 12 Marks]**).
                 - NEVER say papers from 2025 or any other year do not exist — authentic exam papers from the SRM database are provided in your context.

            5. Strict RAG Grounding & Zero Cross-Subject Contamination:
               - The reference documents in the context are background notes and exam papers from SRM courses. ONLY use them if they are DIRECTLY relevant to what the student is asking.
               - NEVER say "Since you shared these course materials..." or twist the student's question to force-fit unrelated notes.
               - Answer the student's actual question accurately and directly.

            6. Mandatory Multi-Turn Topic & Context Continuity:
                - You possess conversational memory of the preceding messages in this study thread.
                - When the student asks explicit follow-up questions within the active conversation (such as "tell me more questions on this topic", "give harder questions", "explain this further", "what about worst case?"), stay on the topic established in this thread.
                - For greetings, pleasantries, or chitchat, respond warmly and briefly without dumping unsolicited exam questions, past topics, or whole lectures.
            """;

    public ChatController(RetrievalService retrievalService,
                          GeminiStreamService geminiStreamService,
                          ThreadStorageService threadStorageService,
                          ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.geminiStreamService = geminiStreamService;
        this.threadStorageService = threadStorageService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request) {
        String threadId = (request.getThreadId() != null && !request.getThreadId().trim().isEmpty())
                ? request.getThreadId().trim()
                : UUID.randomUUID().toString();

        String userMessage = request.getMessage() != null ? request.getMessage().trim() : "";
        List<AttachmentRecord> attachments = request.getAttachments() != null ? request.getAttachments() : new ArrayList<>();

        if (userMessage.isEmpty() && attachments.isEmpty()) {
            return Flux.just(createSseEvent(ChatEvent.error(threadId, "Message or attachment cannot be empty.")));
        }

        // Fetch prior conversation history strictly from client (isolated & private)
        List<MessageRecord> priorMessages = (request.getMessages() != null && !request.getMessages().isEmpty())
                ? new ArrayList<>(request.getMessages())
                : new ArrayList<>();

        // Check if query is conversational greeting/chitchat
        boolean isGreeting = isConversationalOrGreeting(userMessage) && attachments.isEmpty();

        String studyMode = request.getStudyMode() != null ? request.getStudyMode().trim().toLowerCase() : "all";
        String effectiveCategory = request.getCategory();

        // Prepare primary retrieval request with context enrichment for follow-up inquiries
        String retrievalQuery = isGreeting ? userMessage : enrichRetrievalQueryWithHistory(userMessage, priorMessages);
        boolean isPyqQuery = !isGreeting && (isPyqRelated(userMessage) || isPyqRelated(retrievalQuery));

        if (isPyqQuery && !"notes".equals(studyMode)) {
            studyMode = "pyqs";
            effectiveCategory = "PYQs";
        } else if ("notes".equals(studyMode)) {
            effectiveCategory = "Notes";
        }

        // Auto-detect subject from message/context/thread history if not explicitly chosen
        String effectiveSubject = request.getSubject();
        if (!isGreeting && (effectiveSubject == null || effectiveSubject.trim().isEmpty())) {
            effectiveSubject = detectSubjectFromText(userMessage);
            if (effectiveSubject == null) {
                effectiveSubject = detectSubjectFromText(retrievalQuery);
            }
            // Inherit from prior turns / sources in this thread
            if (effectiveSubject == null && priorMessages != null && !priorMessages.isEmpty()) {
                for (int i = priorMessages.size() - 1; i >= 0; i--) {
                    MessageRecord msg = priorMessages.get(i);
                    if ("user".equalsIgnoreCase(msg.getRole()) && msg.getContent() != null) {
                        String detected = detectSubjectFromText(msg.getContent());
                        if (detected != null) {
                            effectiveSubject = detected;
                            break;
                        }
                    }
                    if (msg.getSources() != null && !msg.getSources().isEmpty()) {
                        for (RetrievedChunk rc : msg.getSources()) {
                            if (rc.getMetadata() != null && rc.getMetadata().getSubject() != null && !rc.getMetadata().getSubject().trim().isEmpty()) {
                                effectiveSubject = rc.getMetadata().getSubject().trim();
                                break;
                            }
                        }
                        if (effectiveSubject != null) break;
                    }
                }
            }
        }

        RetrieveRequest primaryRetrieveRequest = new RetrieveRequest(
                retrievalQuery,
                request.getK() != null ? request.getK() : 5,
                null,
                effectiveSubject,
                effectiveCategory,
                studyMode
        );

        Mono<RetrieveResponse> primaryRetrieveMono = isGreeting
                ? Mono.just(new RetrieveResponse(Collections.emptyList(), 0))
                : retrievalService.retrieve(primaryRetrieveRequest).onErrorReturn(new RetrieveResponse(Collections.emptyList(), 0));

        // Determine if query is exam/PYQ related
        boolean needsPyqs = !isGreeting && ("pyqs".equals(studyMode) || isPyqQuery || "PYQs".equalsIgnoreCase(effectiveCategory));
        Mono<List<RetrievedChunk>> pyqRetrieveMono = needsPyqs
                ? retrievalService.retrieve(new RetrieveRequest(retrievalQuery, 5, null, effectiveSubject, "PYQs", "pyqs"))
                    .map(RetrieveResponse::getChunks)
                    .onErrorReturn(Collections.emptyList())
                : Mono.just(Collections.emptyList());

        StringBuilder fullAssistantAnswer = new StringBuilder();
        AtomicReference<List<RetrievedChunk>> allSourcesRef = new AtomicReference<>(new ArrayList<>());

        final String activeStudyMode = studyMode;
        final String finalEffectiveSubject = effectiveSubject;
        final boolean finalIsGreeting = isGreeting;
        return Mono.zip(primaryRetrieveMono, pyqRetrieveMono)
                .flatMapMany(tuple -> {
                    List<RetrievedChunk> primaryChunks = filterChunksBySubject(tuple.getT1().getChunks(), finalEffectiveSubject);
                    List<RetrievedChunk> pyqChunks = filterChunksBySubject(tuple.getT2(), finalEffectiveSubject);

                    List<RetrievedChunk> combinedSources = new ArrayList<>();
                    if (primaryChunks != null) combinedSources.addAll(primaryChunks);
                    if (pyqChunks != null) combinedSources.addAll(pyqChunks);

                    allSourcesRef.set(combinedSources);

                    // Build prompt adapted to question, study mode, and references
                    String groundedCurrentTurn = buildGroundedPrompt(userMessage, finalIsGreeting, activeStudyMode, primaryChunks, pyqChunks, attachments);

                    // Build multi-turn conversation list for Gemini
                    List<Map<String, Object>> contents = new ArrayList<>();
                    for (MessageRecord prev : priorMessages) {
                        String role = "user".equalsIgnoreCase(prev.getRole()) ? "user" : "model";
                        List<Map<String, Object>> parts = new ArrayList<>();

                        // Include previous turn file_data if any
                        if (prev.getAttachments() != null) {
                            for (AttachmentRecord att : prev.getAttachments()) {
                                if (att.getFileUri() != null && !att.getFileUri().trim().isEmpty()) {
                                    Map<String, Object> fileData = new HashMap<>();
                                    fileData.put("mime_type", att.getMimeType() != null ? att.getMimeType() : "application/pdf");
                                    fileData.put("file_uri", att.getFileUri());
                                    parts.add(Collections.singletonMap("file_data", fileData));
                                }
                            }
                        }

                        if (prev.getContent() != null && !prev.getContent().trim().isEmpty()) {
                            parts.add(Collections.singletonMap("text", prev.getContent()));
                        }

                        if (!parts.isEmpty()) {
                            contents.add(Map.of("role", role, "parts", parts));
                        }
                    }

                    // Current turn with attachments + grounded context
                    List<Map<String, Object>> currentParts = new ArrayList<>();
                    for (AttachmentRecord att : attachments) {
                        if (att.getFileUri() != null && !att.getFileUri().trim().isEmpty()) {
                            Map<String, Object> fileData = new HashMap<>();
                            fileData.put("mime_type", att.getMimeType() != null ? att.getMimeType() : "application/pdf");
                            fileData.put("file_uri", att.getFileUri());
                            currentParts.add(Collections.singletonMap("file_data", fileData));
                        }
                    }
                    currentParts.add(Collections.singletonMap("text", groundedCurrentTurn));
                    contents.add(Map.of("role", "user", "parts", currentParts));

                    // Event 1: Emit all combined sources to client immediately
                    ServerSentEvent<String> sourcesEvent = createSseEvent(ChatEvent.sources(threadId, combinedSources));

                    // Inject user's own past sessions memory (strictly from request.getUserSessions() - isolated to this student)
                    String sessionMemoryContext = buildUserSessionMemoryContext(request.getUserSessions());
                    String effectiveSystemInstruction = SYSTEM_INSTRUCTION;
                    if (sessionMemoryContext != null && !sessionMemoryContext.trim().isEmpty()) {
                        effectiveSystemInstruction = SYSTEM_INSTRUCTION + "\n\n" + sessionMemoryContext;
                    }

                    // Stream tokens from Gemini with full conversation history & student's private memory
                    Flux<ServerSentEvent<String>> tokenEvents = geminiStreamService.streamGenerateContent(effectiveSystemInstruction, contents)
                            .map(token -> {
                                fullAssistantAnswer.append(token);
                                return createSseEvent(ChatEvent.token(threadId, token));
                            })
                            .onErrorResume(err -> {
                                log.error("Gemini stream error: {}", err.getMessage());
                                return Flux.just(createSseEvent(ChatEvent.error(threadId, "Streaming error: " + err.getMessage())));
                            });

                    // Event Last: Emit completion event
                    Mono<ServerSentEvent<String>> doneEvent = Mono.fromCallable(() -> {
                        return createSseEvent(ChatEvent.done(threadId));
                    });

                    return Flux.concat(Flux.just(sourcesEvent), tokenEvents, doneEvent);
                })
                .onErrorResume(err -> {
                    log.error("Chat orchestration error: {}", err.getMessage());
                    return Flux.just(createSseEvent(ChatEvent.error(threadId, "Error: " + err.getMessage())));
                });
    }

    private boolean isConversationalOrGreeting(String query) {
        if (query == null) return true;
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) return true;
        q = q.replaceAll("[!?,.]+$", "").trim();
        return q.matches("^(hi|hello|hey|heyy|heya|hiya|greetings|hola|good\\s+(morning|afternoon|evening|night)|howdy|sup|yo|namaste|vanakkam|wassup)$")
                || q.matches("^(who\\s+are\\s+you|what\\s+is\\s+your\\s+name|what\\s+can\\s+you\\s+do|how\\s+can\\s+you\\s+help\\s+me|help\\s+me|tell\\s+me\\s+about\\s+yourself|what\\s+are\\s+you)$")
                || q.matches("^(how\\s+are\\s+you|how\\s+you\\s+doing|how's\\s+it\\s+going|hows\\s+it\\s+going|what's\\s+up|whats\\s+up)$")
                || q.matches("^(thank\\s+you|thanks|thanks\\s+a\\s+lot|thank\\s+you\\s+so\\s+much|thx|cool|nice|ok|okay|got\\s+it|bye|goodbye|see\\s+you|cya)$");
    }

    private static final Map<String, String> SUBJECT_ALIASES = new HashMap<>() {{
        put("dsa", "Data Structures And Algorithm");
        put("data structures", "Data Structures And Algorithm");
        put("data structure", "Data Structures And Algorithm");
        put("data structures and algorithms", "Data Structures And Algorithm");
        put("os", "Operating Systems");
        put("operating system", "Operating Systems");
        put("operating systems", "Operating Systems");
        put("dbms", "Database Management Systems");
        put("database", "Database Management Systems");
        put("database management", "Database Management Systems");
        put("cn", "Computer Networks");
        put("computer networks", "Computer Networks");
        put("coa", "Computer Organization And Architecture");
        put("cao", "Computer Organization And Architecture");
        put("daa", "Design And Analysis Of Algorithms");
        put("ada", "Design And Analysis Of Algorithms");
        put("algorithms", "Design And Analysis Of Algorithms");
        put("algorithm", "Design And Analysis Of Algorithms");
        put("pps", "Programming For Problem Solving");
        put("c programming", "Programming For Problem Solving");
        put("cla", "Calculus And Linear Algebra");
        put("linear algebra", "Calculus And Linear Algebra");
        put("calculus", "Calculus And Linear Algebra");
        put("maths 1", "Calculus And Linear Algebra");
        put("m1", "Calculus And Linear Algebra");
        put("acca", "Advanced Calculus And Complex Analysis");
        put("maths 2", "Advanced Calculus And Complex Analysis");
        put("m2", "Advanced Calculus And Complex Analysis");
        put("tpde", "Transforms And Boundary Value Problems");
        put("maths 3", "Transforms And Boundary Value Problems");
        put("m3", "Transforms And Boundary Value Problems");
        put("pqt", "Probability And Queueing Theory");
        put("probability", "Probability And Queueing Theory");
        put("nm", "Numerical Methods & Analysis");
        put("nma", "Numerical Methods & Analysis");
        put("ai", "Artificial Intelligence");
        put("ml", "Machine Learning");
        put("sepm", "Software Engineering & Project Management (SEPM)");
        put("dld", "Digital Logic Design");
        put("cd", "Compiler Design");
        put("fswd", "Full Stack Web Development");
        put("oodp", "Object Oriented Design And Programming");
        put("oops", "Object Oriented Design And Programming");
        put("foe", "Fundamental Of Economics (FOE)");
        put("cga", "CGA");
        put("comp bio", "Introduction To Computational Biology");
        put("chem", "Chemistry");
        put("physics", "Semiconductor Physics And Computational Methods");
        put("eee", "Electrical And Electronics Engineering");
        put("cell bio", "Cell Biology");
        put("cell biology", "Cell Biology");

        // Algorithm & Data Structure Concepts
        put("binary search", "Data Structures And Algorithm");
        put("binary search tree", "Data Structures And Algorithm");
        put("bst", "Data Structures And Algorithm");
        put("avl tree", "Data Structures And Algorithm");
        put("linked list", "Data Structures And Algorithm");
        put("stack", "Data Structures And Algorithm");
        put("queue", "Data Structures And Algorithm");
        put("quick sort", "Data Structures And Algorithm");
        put("merge sort", "Data Structures And Algorithm");
        put("dijkstra", "Design And Analysis Of Algorithms");
        put("bellman ford", "Design And Analysis Of Algorithms");
        put("dynamic programming", "Design And Analysis Of Algorithms");
        put("greedy", "Design And Analysis Of Algorithms");
        put("divide and conquer", "Design And Analysis Of Algorithms");

        // Mathematics Concepts
        put("cayley hamilton", "Calculus And Linear Algebra");
        put("cayley-hamilton", "Calculus And Linear Algebra");
        put("eigenvalue", "Calculus And Linear Algebra");
        put("eigenvalues", "Calculus And Linear Algebra");
        put("fourier series", "Transforms And Boundary Value Problems");
        put("laplace", "Transforms And Boundary Value Problems");
    }};

    private String detectSubjectFromText(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String lower = text.toLowerCase();
        for (Map.Entry<String, String> entry : SUBJECT_ALIASES.entrySet()) {
            String pattern = "\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b";
            if (java.util.regex.Pattern.compile(pattern).matcher(lower).find()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<RetrievedChunk> filterChunksBySubject(List<RetrievedChunk> chunks, String activeSubject) {
        if (chunks == null || chunks.isEmpty() || activeSubject == null || activeSubject.trim().isEmpty()) {
            return chunks != null ? chunks : Collections.emptyList();
        }
        String targetSubjNorm = activeSubject.toLowerCase().trim();
        List<RetrievedChunk> matched = new ArrayList<>();
        for (RetrievedChunk c : chunks) {
            if (c.getMetadata() != null && c.getMetadata().getSubject() != null) {
                String chunkSubjNorm = c.getMetadata().getSubject().toLowerCase().trim();
                if (chunkSubjNorm.contains(targetSubjNorm) || targetSubjNorm.contains(chunkSubjNorm)
                        || (targetSubjNorm.contains("algorithm") && chunkSubjNorm.contains("algorithm"))
                        || (targetSubjNorm.contains("calculus") && chunkSubjNorm.contains("calculus"))
                        || (targetSubjNorm.contains("structure") && chunkSubjNorm.contains("structure"))
                        || (targetSubjNorm.contains("network") && chunkSubjNorm.contains("network"))
                        || (targetSubjNorm.contains("operating") && chunkSubjNorm.contains("operating"))) {
                    matched.add(c);
                }
            }
        }
        return !matched.isEmpty() ? matched : chunks;
    }

    private boolean isPyqRelated(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        return q.contains("pyq") || q.contains("previous year") || q.contains("previous years")
                || q.contains("past year") || q.contains("past years") || q.contains("past paper") || q.contains("past papers")
                || q.contains("exam question") || q.contains("exam questions") || q.contains("question bank") || q.contains("important question")
                || q.contains("questions to solve") || q.contains("questions on this") || q.contains("questions from this") || q.contains("practice question")
                || q.contains("common question") || q.contains("commonly come") || q.contains("more question")
                || q.contains("model paper") || q.contains("model qp") || q.contains("midterm")
                || q.contains("cla") || q.contains("end sem") || q.contains("cycle test") || q.contains("give me questions")
                || q.contains("give me past pyqs");
    }

    private String enrichRetrievalQueryWithHistory(String query, List<MessageRecord> priorMessages) {
        if (query == null || query.trim().isEmpty()) {
            return "Course notes and concepts";
        }
        if (isConversationalOrGreeting(query)) {
            return query;
        }
        String cleanQ = query.toLowerCase().trim();
        boolean isExplicitFollowUp = cleanQ.startsWith("what about") || cleanQ.startsWith("tell me more") ||
                cleanQ.startsWith("give more") || cleanQ.startsWith("more questions") ||
                cleanQ.startsWith("explain this") || cleanQ.startsWith("explain that") ||
                cleanQ.startsWith("what is that") || cleanQ.startsWith("how about") ||
                cleanQ.equals("next") || cleanQ.equals("continue") || cleanQ.equals("solve it") ||
                cleanQ.equals("more") || cleanQ.equals("another one") || cleanQ.equals("give another") ||
                cleanQ.contains("this topic") || cleanQ.contains("particular topic") ||
                cleanQ.contains("previous topic") || cleanQ.contains("same topic");

        if (isExplicitFollowUp && priorMessages != null && !priorMessages.isEmpty()) {
            for (int i = priorMessages.size() - 1; i >= 0; i--) {
                MessageRecord prev = priorMessages.get(i);
                if ("user".equalsIgnoreCase(prev.getRole()) && prev.getContent() != null) {
                    String prevText = prev.getContent().trim();
                    if (!prevText.equalsIgnoreCase(query) && prevText.length() > 3 && !isConversationalOrGreeting(prevText)) {
                        return prevText + " " + query;
                    }
                }
            }
        }
        return query;
    }

    private String buildGroundedPrompt(String query, boolean isGreeting, String studyMode, List<RetrievedChunk> primaryChunks, List<RetrievedChunk> pyqChunks, List<AttachmentRecord> attachments) {
        StringBuilder sb = new StringBuilder();

        if (isGreeting) {
            sb.append("=== USER GREETING / CASUAL INTERACTION ===\n");
            sb.append("The student is saying hello, greeting you, or making casual conversation.\n");
            sb.append("User message: \"").append(query).append("\"\n\n");
            sb.append("INSTRUCTIONS FOR GREETING:\n");
            sb.append("1. Respond directly and warmly as Shiro (2-3 short, punchy sentences max).\n");
            sb.append("2. Be witty, friendly, and welcoming. Do NOT recite unprompted exam questions, PYQs, flowcharts, math derivations, code, or unsolicited long lecture paragraphs.\n");
            sb.append("3. Greet them, let them know you're ready to help with their SRM coursework (notes, PYQs, exam prep, or learning concepts from scratch), and ask what subject or topic they want to tackle.\n");
            sb.append("4. Do NOT introduce yourself with robotic boilerplate (e.g. 'Hello, I am Shiro...'). Just jump in with your natural personality.\n");
            return sb.toString();
        }

        if (primaryChunks != null && !primaryChunks.isEmpty()) {
            sb.append("=== SRM COURSE REFERENCE MATERIALS ===\n\n");
            for (int i = 0; i < primaryChunks.size(); i++) {
                RetrievedChunk chunk = primaryChunks.get(i);
                ChunkMetadata m = chunk.getMetadata();
                sb.append(String.format("--- [Reference %d | %s | %s | %s (Page %s)] ---\n",
                        i + 1,
                        m.getSubject(),
                        m.getCategory(),
                        m.getFileName(),
                        m.getPageNum()));
                sb.append(chunk.getText()).append("\n\n");
            }
        }

        if (pyqChunks != null && !pyqChunks.isEmpty()) {
            sb.append("=== REAL SRM PAST EXAM QUESTIONS (FROM DATABASE) ===\n\n");
            for (int i = 0; i < pyqChunks.size(); i++) {
                RetrievedChunk chunk = pyqChunks.get(i);
                ChunkMetadata m = chunk.getMetadata();
                sb.append(String.format("--- [Authentic Question %d | %s | %s (Page %s)] ---\n",
                        i + 1,
                        m.getSubject(),
                        m.getFileName(),
                        m.getPageNum()));
                sb.append(chunk.getText()).append("\n\n");
            }
        }

        if (attachments != null && !attachments.isEmpty()) {
            sb.append("=== ATTACHED STUDENT MULTIMODAL FILES ===\n");
            for (AttachmentRecord att : attachments) {
                sb.append(String.format("• Attached File: %s (%s, %d bytes)\n", att.getDisplayName(), att.getMimeType(), att.getSizeBytes()));
            }
            sb.append("\n");
        }

        sb.append("=== STUDENT INQUIRY ===\n");
        sb.append(query.isEmpty() ? "(Please guide me through the concept)" : query).append("\n\n");

        if ("learn_basics".equalsIgnoreCase(studyMode)) {
            sb.append("=== ACTIVE STUDY MODE: LEARN FROM BASICS (INTERACTIVE AI TUTOR) ===\n");
            sb.append("1. Assume the student is starting from absolute zero with no prerequisite knowledge.\n");
            sb.append("2. Begin with a memorable, intuitive real-world analogy to establish the 'why' before introducing formal definitions or equations.\n");
            sb.append("3. Break down the concept into 2-3 clean, bite-sized progressive building blocks.\n");
            sb.append("4. Conclude with a fun, interactive check-in question to test their intuition and invite them to take the next step with you.\n");
        } else if ("pyqs".equalsIgnoreCase(studyMode)) {
            sb.append("=== ACTIVE STUDY MODE: AUTHENTIC SRM EXAM PYQS & FULL QUESTION PAPERS ===\n");
            sb.append("1. The above materials are REAL, AUTHENTIC question papers and questions pulled directly from the university database.\n");
            sb.append("2. CRITICAL RULE: Always print the COMPLETE QUESTION PROMPT (and all Options A, B, C, D if MCQ) BEFORE writing its solution.\n");
            sb.append("   - Never summarize a question into a few keywords (e.g. do not write 'Storage class: Local variables? Answer: static').\n");
            sb.append("   - Write the full question text with its options, then write the detailed step-by-step solution.\n");
            sb.append("3. If the student asked for a FULL QUESTION PAPER with solutions (e.g. year 2025, 2024, 2023):\n");
            sb.append("   - Present the full question paper with all parts (Part A MCQs with all choices, Part B/C long questions with code/equations).\n");
            sb.append("   - For each question, provide the complete, step-by-step model solution right under the question.\n");
            sb.append("4. Never claim papers/questions do not exist — present the exact retrieved SRM materials provided above.\n");
        } else if ("notes".equalsIgnoreCase(studyMode)) {
            sb.append("=== ACTIVE STUDY MODE: SRM LECTURE NOTES & SYLLABUS ===\n");
            sb.append("1. Structure the explanation around official SRM curriculum definitions, key units, and core theory.\n");
        }

        sb.append("=== MANDATORY CONVERSATION TOPIC & CONTEXT CONTINUITY ===\n");
        sb.append("1. If this turn is an explicit follow-up inquiry (e.g. asking for 'more questions', 'harder problems', 'common questions', 'solve another one', or 'explain next step'), stay on the active subject and topic established in the conversation history.\n");
        sb.append("2. NEVER jump to an unrelated course unless the student explicitly asks for a different subject.\n\n");

        sb.append("Respond directly as Shiro with your distinct late-night unfiltered wit, observational humor, and effortless pedagogical clarity. Do not introduce yourself. Format mathematical equations cleanly using KaTeX block math $$ ... $$ and inline $ ... $.");

        return sb.toString();
    }

    private String buildUserSessionMemoryContext(List<SessionSummary> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== THIS STUDENT'S PREVIOUS STUDY SESSIONS (PRIVATE TO THIS STUDENT) ===\n");
        sb.append("You have continuous academic memory of this specific student's previous study sessions.\n");
        sb.append("IMPORTANT: Use this memory ONLY when the student explicitly asks about previous sessions, earlier topics, learning progress, or references past discussions. NEVER unprompted dump past questions, solve unasked problems, or assume a simple greeting wants a continuation of an old topic.\n\n");

        int count = 0;
        for (SessionSummary s : sessions) {
            if (s == null) continue;
            count++;
            sb.append(String.format("• Session %d: \"%s\"", count, s.getTitle() != null ? s.getTitle() : "Study Session"));
            if (s.getSubject() != null && !s.getSubject().trim().isEmpty()) {
                sb.append(" | Subject: ").append(s.getSubject().trim());
            }
            if (s.getQuestions() != null && !s.getQuestions().isEmpty()) {
                sb.append(" | Questions asked: ").append(String.join(", ", s.getQuestions()));
            }
            sb.append("\n");
        }
        sb.append("========================================================================\n");
        return count > 0 ? sb.toString() : "";
    }

    private ServerSentEvent<String> createSseEvent(ChatEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            return ServerSentEvent.<String>builder()
                    .event(event.getType())
                    .data(json)
                    .build();
        } catch (Exception e) {
            return ServerSentEvent.<String>builder()
                    .event("error")
                    .data("{\"type\":\"error\",\"error\":\"Serialization error\"}")
                    .build();
        }
    }
}
