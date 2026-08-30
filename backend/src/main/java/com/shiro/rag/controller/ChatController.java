package com.shiro.rag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiro.rag.model.*;
import com.shiro.rag.service.GeminiStreamService;
import com.shiro.rag.service.RetrievalService;
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

    private final RetrievalService retrievalService;
    private final GeminiStreamService geminiStreamService;
    private final ObjectMapper objectMapper;
    private final com.shiro.rag.config.AppProperties properties;

    public ChatController(RetrievalService retrievalService,
                          GeminiStreamService geminiStreamService,
                          ObjectMapper objectMapper,
                          com.shiro.rag.config.AppProperties properties) {
        this.retrievalService = retrievalService;
        this.geminiStreamService = geminiStreamService;
        this.objectMapper = objectMapper;
        this.properties = properties;
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

        List<MessageRecord> priorMessages = (request.getMessages() != null && !request.getMessages().isEmpty())
                ? new ArrayList<>(request.getMessages())
                : new ArrayList<>();

        boolean isGreeting = isConversationalOrGreeting(userMessage) && attachments.isEmpty();

        String studyMode = request.getStudyMode() != null ? request.getStudyMode().trim().toLowerCase() : "all";
        String effectiveCategory = request.getCategory();

        String effectiveSubject = request.getSubject();
        if (!isGreeting && (effectiveSubject == null || effectiveSubject.trim().isEmpty())) {
            effectiveSubject = detectSubjectFromText(userMessage);
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
                }
                if (effectiveSubject == null) {
                    for (int i = priorMessages.size() - 1; i >= 0; i--) {
                        MessageRecord msg = priorMessages.get(i);
                        if ("assistant".equalsIgnoreCase(msg.getRole()) && msg.getContent() != null) {
                            String detected = detectSubjectFromText(msg.getContent());
                            if (detected != null) {
                                effectiveSubject = detected;
                                break;
                            }
                        }
                    }
                }
                if (effectiveSubject == null) {
                    for (int i = priorMessages.size() - 1; i >= 0; i--) {
                        MessageRecord msg = priorMessages.get(i);
                        if (msg.getSources() != null && !msg.getSources().isEmpty()) {
                            for (RetrievedChunk rc : msg.getSources()) {
                                if (rc.getMetadata() != null && rc.getMetadata().getSubject() != null && !rc.getMetadata().getSubject().trim().isEmpty()) {
                                    String subj = rc.getMetadata().getSubject().trim();
                                    if (!"General".equalsIgnoreCase(subj) && !"All".equalsIgnoreCase(subj)) {
                                        effectiveSubject = subj;
                                        break;
                                    }
                                }
                            }
                        }
                        if (effectiveSubject != null) break;
                    }
                }
            }
        }

        String retrievalQuery = isGreeting ? userMessage : enrichRetrievalQueryWithHistory(userMessage, priorMessages, effectiveSubject);
        if (effectiveSubject == null && !isGreeting) {
            effectiveSubject = detectSubjectFromText(retrievalQuery);
        }

        boolean isPyqQuery = !isGreeting && (isPyqRelated(userMessage) || isPyqRelated(retrievalQuery));

        if ("learn_basics".equals(studyMode)) {
            effectiveCategory = "Notes";
        } else if ("notes".equals(studyMode)) {
            effectiveCategory = "Notes";
        } else if ("pyqs".equals(studyMode)) {
            effectiveCategory = "PYQs";
        } else if (isPyqQuery) {
            studyMode = "pyqs";
            effectiveCategory = "PYQs";
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

        boolean needsPyqs = !isGreeting && ("pyqs".equals(studyMode) || (isPyqQuery && !"notes".equals(studyMode) && !"learn_basics".equals(studyMode)));
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

                    String groundedCurrentTurn = buildGroundedPrompt(userMessage, finalIsGreeting, activeStudyMode, finalEffectiveSubject, primaryChunks, pyqChunks, attachments);

                    List<Map<String, Object>> contents = new ArrayList<>();
                    for (MessageRecord prev : priorMessages) {
                        String role = "user".equalsIgnoreCase(prev.getRole()) ? "user" : "model";
                        List<Map<String, Object>> parts = new ArrayList<>();

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

                    log.info("Constructed Gemini Prompt:\n{}", groundedCurrentTurn);

                    ServerSentEvent<String> sourcesEvent = createSseEvent(ChatEvent.sources(threadId, combinedSources));

                    String sessionMemoryContext = buildUserSessionMemoryContext(request.getUserSessions());
                    String effectiveSystemInstruction = SYSTEM_INSTRUCTION;
                    if (sessionMemoryContext != null && !sessionMemoryContext.trim().isEmpty()) {
                        effectiveSystemInstruction = SYSTEM_INSTRUCTION + "\n\n" + sessionMemoryContext;
                    }

                    Flux<ServerSentEvent<String>> tokenEvents = geminiStreamService.streamGenerateContent(effectiveSystemInstruction, contents)
                            .map(token -> {
                                fullAssistantAnswer.append(token);
                                return createSseEvent(ChatEvent.token(threadId, token));
                            })
                            .onErrorResume(err -> {
                                log.error("Gemini stream error: {}", err.getMessage());
                                String userFriendlyMessage;
                                if (err.getMessage() != null && err.getMessage().contains("GEMINI_API_KEY")) {
                                    userFriendlyMessage = "⚠️ Gemini API key is missing. Please set `GEMINI_API_KEY=your_key` in `/Users/rishii/ChrioShiro/.env` (or in your environment) and restart the application.";
                                } else if (err.getMessage() != null && err.getMessage().contains("403")) {
                                    userFriendlyMessage = "⚠️ Gemini API returned 403 Forbidden. Please verify that your `GEMINI_API_KEY` is valid and has permissions for model `" + properties.getGeminiModel() + "`.";
                                } else {
                                    userFriendlyMessage = "Streaming error: " + err.getMessage();
                                }
                                return Flux.just(createSseEvent(ChatEvent.error(threadId, userFriendlyMessage)));
                            });

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

    private static final Map<String, String> SUBJECT_ALIASES = new LinkedHashMap<>() {{
        // Mathematics
        put("discrete mathematics", "Discrete Mathematics");
        put("discrete math", "Discrete Mathematics");
        put("discrete", "Discrete Mathematics");
        put("dm", "Discrete Mathematics");
        put("maths 5", "Discrete Mathematics");
        put("math 5", "Discrete Mathematics");
        put("m5", "Discrete Mathematics");
        put("pigeonhole principle", "Discrete Mathematics");
        put("pigeonhole", "Discrete Mathematics");
        put("recurrence relation", "Discrete Mathematics");
        put("recurrence relations", "Discrete Mathematics");
        put("generating functions", "Discrete Mathematics");
        put("predicate logic", "Discrete Mathematics");
        put("propositional logic", "Discrete Mathematics");

        put("transforms and boundary value problems", "Transforms And Boundary Value Problems");
        put("transforms and bvp", "Transforms And Boundary Value Problems");
        put("transforms", "Transforms And Boundary Value Problems");
        put("tpde", "Transforms And Boundary Value Problems");
        put("maths 3", "Transforms And Boundary Value Problems");
        put("math 3", "Transforms And Boundary Value Problems");
        put("m3", "Transforms And Boundary Value Problems");
        put("fourier series", "Transforms And Boundary Value Problems");
        put("fourier transform", "Transforms And Boundary Value Problems");
        put("fourier transforms", "Transforms And Boundary Value Problems");
        put("laplace transform", "Transforms And Boundary Value Problems");
        put("laplace transforms", "Transforms And Boundary Value Problems");
        put("laplace", "Transforms And Boundary Value Problems");
        put("z transform", "Transforms And Boundary Value Problems");
        put("boundary value problems", "Transforms And Boundary Value Problems");
        put("partial differential equations", "Transforms And Boundary Value Problems");
        put("pde", "Transforms And Boundary Value Problems");

        put("calculus and linear algebra", "Calculus And Linear Algebra");
        put("linear algebra", "Calculus And Linear Algebra");
        put("calculus", "Calculus And Linear Algebra");
        put("cla", "Calculus And Linear Algebra");
        put("maths 1", "Calculus And Linear Algebra");
        put("math 1", "Calculus And Linear Algebra");
        put("m1", "Calculus And Linear Algebra");
        put("cayley hamilton", "Calculus And Linear Algebra");
        put("cayley-hamilton", "Calculus And Linear Algebra");
        put("eigenvalue", "Calculus And Linear Algebra");
        put("eigenvalues", "Calculus And Linear Algebra");
        put("matrices", "Calculus And Linear Algebra");

        put("advanced calculus and complex analysis", "Advanced Calculus And Complex Analysis");
        put("advanced calculus", "Advanced Calculus And Complex Analysis");
        put("complex analysis", "Advanced Calculus And Complex Analysis");
        put("acca", "Advanced Calculus And Complex Analysis");
        put("maths 2", "Advanced Calculus And Complex Analysis");
        put("math 2", "Advanced Calculus And Complex Analysis");
        put("m2", "Advanced Calculus And Complex Analysis");
        put("cauchy riemann", "Advanced Calculus And Complex Analysis");
        put("contour integration", "Advanced Calculus And Complex Analysis");
        put("residue theorem", "Advanced Calculus And Complex Analysis");
        put("laurent series", "Advanced Calculus And Complex Analysis");

        put("probability and queueing theory", "Probability And Queueing Theory");
        put("probability & applied statistics", "Probability And Queueing Theory");
        put("probability and statistics", "Probability And Queueing Theory");
        put("probability", "Probability And Queueing Theory");
        put("queueing theory", "Probability And Queueing Theory");
        put("pqt", "Probability And Queueing Theory");
        put("maths 4", "Probability And Queueing Theory");
        put("math 4", "Probability And Queueing Theory");
        put("m4", "Probability And Queueing Theory");
        put("markov chain", "Probability And Queueing Theory");
        put("poisson process", "Probability And Queueing Theory");

        put("numerical methods & analysis", "Numerical Methods & Analysis");
        put("numerical methods and analysis", "Numerical Methods & Analysis");
        put("numerical methods", "Numerical Methods & Analysis");
        put("nm", "Numerical Methods & Analysis");
        put("nma", "Numerical Methods & Analysis");
        put("newton raphson", "Numerical Methods & Analysis");
        put("gauss elimination", "Numerical Methods & Analysis");
        put("runge kutta", "Numerical Methods & Analysis");

        // Data Structures & Algorithms
        put("data structures and algorithm", "Data Structures And Algorithm");
        put("data structures and algorithms", "Data Structures And Algorithm");
        put("data structures", "Data Structures And Algorithm");
        put("data structure", "Data Structures And Algorithm");
        put("dsa", "Data Structures And Algorithm");
        put("binary search", "Data Structures And Algorithm");
        put("binary search tree", "Data Structures And Algorithm");
        put("bst", "Data Structures And Algorithm");
        put("avl tree", "Data Structures And Algorithm");
        put("linked list", "Data Structures And Algorithm");
        put("stack", "Data Structures And Algorithm");
        put("queue", "Data Structures And Algorithm");
        put("quick sort", "Data Structures And Algorithm");
        put("merge sort", "Data Structures And Algorithm");

        put("design and analysis of algorithms", "Design And Analysis Of Algorithms");
        put("algorithm analysis", "Design And Analysis Of Algorithms");
        put("algorithms", "Design And Analysis Of Algorithms");
        put("algorithm", "Design And Analysis Of Algorithms");
        put("daa", "Design And Analysis Of Algorithms");
        put("ada", "Design And Analysis Of Algorithms");
        put("dijkstra", "Design And Analysis Of Algorithms");
        put("bellman ford", "Design And Analysis Of Algorithms");
        put("dynamic programming", "Design And Analysis Of Algorithms");
        put("greedy", "Design And Analysis Of Algorithms");
        put("divide and conquer", "Design And Analysis Of Algorithms");

        // Systems & Core CS
        put("operating systems", "Operating Systems");
        put("operating system", "Operating Systems");
        put("os", "Operating Systems");
        put("file system", "Operating Systems");
        put("file systems", "Operating Systems");
        put("file allocation", "Operating Systems");
        put("file management", "Operating Systems");
        put("priority scheduling", "Operating Systems");
        put("process scheduling", "Operating Systems");
        put("process states", "Operating Systems");
        put("cpu scheduling", "Operating Systems");
        put("deadlock", "Operating Systems");
        put("deadlocks", "Operating Systems");
        put("paging", "Operating Systems");
        put("virtual memory", "Operating Systems");
        put("semaphores", "Operating Systems");
        put("bankers algorithm", "Operating Systems");

        put("database management systems", "Database Management Systems");
        put("database management system", "Database Management Systems");
        put("database management", "Database Management Systems");
        put("database", "Database Management Systems");
        put("dbms", "Database Management Systems");
        put("file processing system", "Database Management Systems");
        put("file processing systems", "Database Management Systems");
        put("acid properties", "Database Management Systems");
        put("acid property", "Database Management Systems");
        put("relational algebra", "Database Management Systems");
        put("normalization", "Database Management Systems");
        put("er model", "Database Management Systems");

        put("computer networks", "Computer Networks");
        put("computer network", "Computer Networks");
        put("cn", "Computer Networks");
        put("osi model", "Computer Networks");
        put("tcp ip", "Computer Networks");

        put("computer organization and architecture", "Computer Organization And Architecture");
        put("computer organization", "Computer Organization And Architecture");
        put("computer architecture", "Computer Organization And Architecture");
        put("coa", "Computer Organization And Architecture");
        put("cao", "Computer Organization And Architecture");

        put("digital logic design", "Digital Logic Design");
        put("digital logic", "Digital Logic Design");
        put("dld", "Digital Logic Design");

        put("compiler design", "Compiler Design");
        put("compiler", "Compiler Design");
        put("cd", "Compiler Design");

        put("formal language and automata", "Formal Language And Automata");
        put("theory of computation", "Formal Language And Automata");
        put("automata", "Formal Language And Automata");
        put("toc", "Formal Language And Automata");
        put("flata", "Formal Language And Automata");

        // Programming & Web
        put("programming for problem solving", "Programming For Problem Solving");
        put("c programming", "Programming For Problem Solving");
        put("c language", "Programming For Problem Solving");
        put("pps", "Programming For Problem Solving");
        put("21csc101t", "Programming For Problem Solving");

        put("object oriented design and programming", "Object Oriented Design And Programming");
        put("object oriented programming", "Object Oriented Design And Programming");
        put("oodp", "Object Oriented Design And Programming");
        put("oops", "Object Oriented Design And Programming");
        put("oop", "Object Oriented Design And Programming");
        put("java", "Object Oriented Design And Programming");
        put("21csc202j", "Object Oriented Design And Programming");

        put("full stack web development", "Full Stack Web Development");
        put("full stack", "Full Stack Web Development");
        put("web dev", "Full Stack Web Development");
        put("fswd", "Full Stack Web Development");
        put("21csc305p", "Full Stack Web Development");

        put("advanced programming practice", "Advanced Programming Practice");
        put("advanced programming", "Advanced Programming Practice");
        put("advance programming", "Advanced Programming Practice");
        put("advance programing", "Advanced Programming Practice");
        put("advanced programing", "Advanced Programming Practice");
        put("adv programming", "Advanced Programming Practice");
        put("adv programing", "Advanced Programming Practice");
        put("adv java", "Advanced Programming Practice");
        put("advanced java", "Advanced Programming Practice");
        put("app java", "Advanced Programming Practice");
        put("app", "Advanced Programming Practice");
        put("21csc203p", "Advanced Programming Practice");
        put("18csc207j", "Advanced Programming Practice");

        // Course Codes & Mathematical Math Courses
        put("21mab101t", "Calculus And Linear Algebra");
        put("21mab102t", "Advanced Calculus And Complex Analysis");
        put("21mab201t", "Transforms And Boundary Value Problems");
        put("21mab302t", "Discrete Mathematics");
        put("21mab401t", "Probability And Queueing Theory");
        put("21mab501t", "Numerical Methods & Analysis");
        put("21csc201j", "Data Structures And Algorithm");
        put("21csc204j", "Design And Analysis Of Algorithms");
        put("21csc301t", "Formal Language And Automata");
        put("21csc302t", "Database Management Systems");
        put("21csc303t", "Computer Networks");
        put("21csc304t", "Compiler Design");

        // AI, ML, Data Science
        put("artificial intelligence", "Artificial Intelligence");
        put("ai", "Artificial Intelligence");
        put("machine learning", "Machine Learning");
        put("ml", "Machine Learning");
        put("data science", "Data Science");
        put("foundation of data science", "Foundation of Data Science (FDS)");
        put("fds", "Foundation of Data Science (FDS)");

        // Engineering, Sciences & Others
        put("software engineering & project management", "Software Engineering & Project Management (SEPM)");
        put("software engineering and project management", "Software Engineering & Project Management (SEPM)");
        put("software engineering", "Software Engineering & Project Management (SEPM)");
        put("software project management", "Software Engineering & Project Management (SEPM)");
        put("software management and economics", "Software Engineering & Project Management (SEPM)");
        put("software management", "Software Engineering & Project Management (SEPM)");
        put("sepm", "Software Engineering & Project Management (SEPM)");
        put("spm", "Software Engineering & Project Management (SEPM)");
        put("cocomo", "Software Engineering & Project Management (SEPM)");
        put("cocomo ii", "Software Engineering & Project Management (SEPM)");
        put("fundamental of economics", "Fundamental Of Economics (FOE)");
        put("economics", "Fundamental Of Economics (FOE)");
        put("foe", "Fundamental Of Economics (FOE)");
        put("cga", "CGA");
        put("introduction to computational biology", "Introduction To Computational Biology");
        put("computational biology", "Introduction To Computational Biology");
        put("comp bio", "Introduction To Computational Biology");
        put("physical and analytical chemistry", "Physical And Analytical Chemistry");
        put("chemistry", "Chemistry");
        put("chem", "Chemistry");
        put("semiconductor physics and computational methods", "Semiconductor Physics And Computational Methods");
        put("semiconductor physics", "Semiconductor Physics And Computational Methods");
        put("physics", "Semiconductor Physics And Computational Methods");
        put("electromagnetic physics", "Electromagnetic Physics");
        put("electrical and electronics engineering", "Electrical And Electronics Engineering");
        put("electrical", "Electrical And Electronics Engineering");
        put("eee", "Electrical And Electronics Engineering");
        put("cell biology", "Cell Biology");
        put("cell bio", "Cell Biology");
        put("biology", "Biology");
        put("biochemistry", "Biochemistry");
        put("design thinking and methodology", "Design Thinking And Methodology");
        put("design thinking", "Design Thinking And Methodology");
        put("dtm", "Design Thinking And Methodology");
        put("solid state devices", "Solid State Devices");
        put("ssd", "Solid State Devices");
        put("electronic system and pcb design", "Electronic System And PCB Design");
        put("pcb design", "Electronic System And PCB Design");
        put("pcb", "Electronic System And PCB Design");
        put("engineering mechanics", "Engineering Mechanics");
        put("communicative english", "Communicative English");
        put("english", "Communicative English");
        put("social engineering", "Social Engineering");
        put("philosophy of engineering", "Philosophy Of Engineering");
        put("foreign languages", "Foreign Languages");
    }};

    private static final Map<String, List<String>> SEMESTER_SUBJECTS = Map.of(
        "Semester 1", List.of(
            "Calculus And Linear Algebra", "Programming For Problem Solving", "Physical And Analytical Chemistry",
            "Chemistry", "Cell Biology", "Biology", "Fundamental Of Economics (FOE)", "Philosophy Of Engineering",
            "Introduction To Computational Biology", "Foreign Languages"
        ),
        "Semester 2", List.of(
            "Advanced Calculus And Complex Analysis", "Object Oriented Design And Programming", "Electrical And Electronics Engineering",
            "Semiconductor Physics And Computational Methods", "Electromagnetic Physics", "Electronic System And PCB Design",
            "Engineering Mechanics", "Communicative English"
        ),
        "Semester 3", List.of(
            "Transforms And Boundary Value Problems", "Numerical Methods & Analysis", "Data Structures And Algorithm",
            "Operating Systems", "Computer Organization And Architecture", "Digital Logic Design",
            "Advanced Programming Practice", "Biochemistry", "Design Thinking And Methodology",
            "Electromagnetic Thoery And Interference", "Solid State Devices", "Foundation of Data Science (FDS)",
            "Genetics And Cytogenetics", "Microbiology"
        ),
        "Semester 4", List.of(
            "Probability And Queueing Theory", "Probability & Applied Statistics", "Database Management Systems",
            "Design And Analysis Of Algorithms", "Artificial Intelligence", "CGA", "Cell Communication And Signaling",
            "Bioprocess Engineering", "Molecular Biology", "Software Process", "Internet Of Things (IOT)", "Social Engineering"
        ),
        "Semester 5", List.of(
            "Discrete Mathematics", "Computer Networks", "Full Stack Web Development", "Formal Language And Automata"
        ),
        "Semester 6", List.of(
            "Compiler Design", "Data Science", "Software Engineering & Project Management (SEPM)"
        )
    );

    private static final Map<String, Map<String, String>> SEMESTER_DOMAIN_MAP = Map.of(
        "Semester 1", Map.ofEntries(
            Map.entry("math", "Calculus And Linear Algebra"),
            Map.entry("maths", "Calculus And Linear Algebra"),
            Map.entry("mathematics", "Calculus And Linear Algebra"),
            Map.entry("calculus", "Calculus And Linear Algebra"),
            Map.entry("linear algebra", "Calculus And Linear Algebra"),
            Map.entry("cla", "Calculus And Linear Algebra"),
            Map.entry("programming", "Programming For Problem Solving"),
            Map.entry("coding", "Programming For Problem Solving"),
            Map.entry("c language", "Programming For Problem Solving"),
            Map.entry("pps", "Programming For Problem Solving"),
            Map.entry("chemistry", "Physical And Analytical Chemistry"),
            Map.entry("chem", "Physical And Analytical Chemistry"),
            Map.entry("biology", "Cell Biology"),
            Map.entry("bio", "Cell Biology"),
            Map.entry("economics", "Fundamental Of Economics (FOE)"),
            Map.entry("foe", "Fundamental Of Economics (FOE)")
        ),
        "Semester 2", Map.ofEntries(
            Map.entry("math", "Advanced Calculus And Complex Analysis"),
            Map.entry("maths", "Advanced Calculus And Complex Analysis"),
            Map.entry("mathematics", "Advanced Calculus And Complex Analysis"),
            Map.entry("complex analysis", "Advanced Calculus And Complex Analysis"),
            Map.entry("acca", "Advanced Calculus And Complex Analysis"),
            Map.entry("programming", "Object Oriented Design And Programming"),
            Map.entry("oops", "Object Oriented Design And Programming"),
            Map.entry("oop", "Object Oriented Design And Programming"),
            Map.entry("java", "Object Oriented Design And Programming"),
            Map.entry("oodp", "Object Oriented Design And Programming"),
            Map.entry("physics", "Semiconductor Physics And Computational Methods"),
            Map.entry("electrical", "Electrical And Electronics Engineering"),
            Map.entry("eee", "Electrical And Electronics Engineering"),
            Map.entry("pcb", "Electronic System And PCB Design"),
            Map.entry("english", "Communicative English")
        ),
        "Semester 3", Map.ofEntries(
            Map.entry("math", "Transforms And Boundary Value Problems"),
            Map.entry("maths", "Transforms And Boundary Value Problems"),
            Map.entry("mathematics", "Transforms And Boundary Value Problems"),
            Map.entry("tpde", "Transforms And Boundary Value Problems"),
            Map.entry("transforms", "Transforms And Boundary Value Problems"),
            Map.entry("numerical methods", "Numerical Methods & Analysis"),
            Map.entry("dsa", "Data Structures And Algorithm"),
            Map.entry("data structures", "Data Structures And Algorithm"),
            Map.entry("os", "Operating Systems"),
            Map.entry("operating systems", "Operating Systems"),
            Map.entry("coa", "Computer Organization And Architecture"),
            Map.entry("dld", "Digital Logic Design"),
            Map.entry("digital logic", "Digital Logic Design"),
            Map.entry("app", "Advanced Programming Practice"),
            Map.entry("advanced programming", "Advanced Programming Practice"),
            Map.entry("advance programming", "Advanced Programming Practice"),
            Map.entry("advance programing", "Advanced Programming Practice"),
            Map.entry("adv programming", "Advanced Programming Practice"),
            Map.entry("fds", "Foundation of Data Science (FDS)")
        ),
        "Semester 4", Map.ofEntries(
            Map.entry("math", "Probability And Queueing Theory"),
            Map.entry("maths", "Probability And Queueing Theory"),
            Map.entry("probability", "Probability And Queueing Theory"),
            Map.entry("pqt", "Probability And Queueing Theory"),
            Map.entry("dbms", "Database Management Systems"),
            Map.entry("database", "Database Management Systems"),
            Map.entry("daa", "Design And Analysis Of Algorithms"),
            Map.entry("algorithms", "Design And Analysis Of Algorithms"),
            Map.entry("ai", "Artificial Intelligence"),
            Map.entry("cga", "CGA"),
            Map.entry("iot", "Internet Of Things (IOT)")
        ),
        "Semester 5", Map.ofEntries(
            Map.entry("math", "Discrete Mathematics"),
            Map.entry("maths", "Discrete Mathematics"),
            Map.entry("discrete", "Discrete Mathematics"),
            Map.entry("dm", "Discrete Mathematics"),
            Map.entry("cn", "Computer Networks"),
            Map.entry("networks", "Computer Networks"),
            Map.entry("web dev", "Full Stack Web Development"),
            Map.entry("fswd", "Full Stack Web Development"),
            Map.entry("toc", "Formal Language And Automata"),
            Map.entry("flata", "Formal Language And Automata"),
            Map.entry("automata", "Formal Language And Automata")
        ),
        "Semester 6", Map.ofEntries(
            Map.entry("compiler", "Compiler Design"),
            Map.entry("cd", "Compiler Design"),
            Map.entry("data science", "Data Science"),
            Map.entry("sepm", "Software Engineering & Project Management (SEPM)"),
            Map.entry("software engineering", "Software Engineering & Project Management (SEPM)")
        )
    );

    private String normalizeAcademicText(String text) {
        if (text == null) return "";
        String t = text.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", " ");
        t = t.replaceAll("\\badvance\\b", "advanced")
             .replaceAll("\\bprograming\\b", "programming")
             .replaceAll("\\balgorithim\\b|\\balgorythm\\b|\\balgos\\b", "algorithm")
             .replaceAll("\\bcalculas\\b", "calculus")
             .replaceAll("\\bprobablity\\b|\\bprobalility\\b", "probability")
             .replaceAll("\\bdescrete\\b", "discrete")
             .replaceAll("\\boperatng\\b|\\boprating\\b", "operating")
             .replaceAll("\\bstructur\\b|\\bstructres\\b", "structure")
             .replaceAll("\\bnetwrok\\b|\\bnetwrk\\b", "network");
        return t.replaceAll("\\s+", " ").trim();
    }

    private String extractSemester(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String lower = text.toLowerCase();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:sem(?:ester)?\\s*([1-8])|([1-8])(?:st|nd|rd|th)?\\s+sem(?:ester)?|\\bs([1-8])\\b)"
        );
        java.util.regex.Matcher m = p.matcher(lower);
        if (m.find()) {
            String num = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : m.group(3));
            return "Semester " + num;
        }
        return null;
    }

    private String detectSubjectFromText(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String lower = text.toLowerCase();
        String norm = normalizeAcademicText(text);
        String detectedSem = extractSemester(lower);

        if (detectedSem != null && SEMESTER_DOMAIN_MAP.containsKey(detectedSem)) {
            Map<String, String> domainMap = SEMESTER_DOMAIN_MAP.get(detectedSem);
            List<Map.Entry<String, String>> sortedDomainEntries = new ArrayList<>(domainMap.entrySet());
            sortedDomainEntries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

            for (Map.Entry<String, String> entry : sortedDomainEntries) {
                String pattern = "\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b";
                if (java.util.regex.Pattern.compile(pattern).matcher(lower).find()
                        || java.util.regex.Pattern.compile(pattern).matcher(norm).find()) {
                    return entry.getValue();
                }
            }
        }

        if (detectedSem != null && SEMESTER_SUBJECTS.containsKey(detectedSem)) {
            List<String> semCandidates = SEMESTER_SUBJECTS.get(detectedSem);
            for (String cand : semCandidates) {
                if (lower.contains(cand.toLowerCase()) || norm.contains(cand.toLowerCase())) {
                    return cand;
                }
            }
        }

        List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(SUBJECT_ALIASES.entrySet());
        sortedEntries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        for (Map.Entry<String, String> entry : sortedEntries) {
            String pattern = "\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b";
            if (java.util.regex.Pattern.compile(pattern).matcher(lower).find()
                    || java.util.regex.Pattern.compile(pattern).matcher(norm).find()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<RetrievedChunk> filterChunksBySubject(List<RetrievedChunk> chunks, String activeSubject) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        if (activeSubject == null || activeSubject.trim().isEmpty() || "General".equalsIgnoreCase(activeSubject) || "All".equalsIgnoreCase(activeSubject)) {
            return chunks;
        }
        String targetSubjNorm = normalizeAcademicText(activeSubject);
        List<RetrievedChunk> matched = new ArrayList<>();
        for (RetrievedChunk c : chunks) {
            if (c.getMetadata() != null && c.getMetadata().getSubject() != null) {
                String chunkSubj = c.getMetadata().getSubject().trim();
                String chunkSubjNorm = normalizeAcademicText(chunkSubj);
                if (chunkSubjNorm.equals(targetSubjNorm)
                        || chunkSubjNorm.contains(targetSubjNorm)
                        || targetSubjNorm.contains(chunkSubjNorm)
                        || isSubjectDomainEquivalent(targetSubjNorm, chunkSubjNorm)) {
                    matched.add(c);
                }
            }
        }
        return matched;
    }

    private boolean isSubjectDomainEquivalent(String s1, String s2) {
        if (s1.contains("advanced programming") && s2.contains("advanced programming")) return true;
        if (s1.contains("data structure") && s2.contains("data structure")) return true;
        if (s1.contains("operating system") && s2.contains("operating system")) return true;
        if (s1.contains("database") && s2.contains("database")) return true;
        if (s1.contains("discrete") && s2.contains("discrete")) return true;
        if (s1.contains("calculus") && s2.contains("calculus")) return true;
        if (s1.contains("transforms") && s2.contains("transforms")) return true;
        if (s1.contains("probability") && s2.contains("probability")) return true;
        if (s1.contains("network") && s2.contains("network")) return true;
        if (s1.contains("software engineering") && s2.contains("software engineering")) return true;
        if (s1.contains("compiler") && s2.contains("compiler")) return true;
        return false;
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
                || q.contains("end sem") || q.contains("cycle test") || q.contains("give me questions")
                || q.contains("give me past pyqs") || q.contains("question paper");
    }

    private String enrichRetrievalQueryWithHistory(String query, List<MessageRecord> priorMessages, String activeSubject) {
        if (query == null || query.trim().isEmpty()) {
            return activeSubject != null ? activeSubject + " course concepts" : "Course notes and concepts";
        }
        if (isConversationalOrGreeting(query)) {
            return query;
        }
        String cleanQ = query.toLowerCase().trim();
        boolean isExplicitFollowUp = cleanQ.startsWith("what about") || cleanQ.startsWith("tell me more") ||
                cleanQ.startsWith("give more") || cleanQ.startsWith("more questions") ||
                cleanQ.startsWith("explain this") || cleanQ.startsWith("explain that") ||
                cleanQ.startsWith("what is that") || cleanQ.startsWith("how about") ||
                cleanQ.startsWith("can you give") || cleanQ.startsWith("give me") ||
                cleanQ.startsWith("solve") || cleanQ.startsWith("show") ||
                cleanQ.equals("next") || cleanQ.equals("continue") || cleanQ.equals("solve it") ||
                cleanQ.equals("more") || cleanQ.equals("another one") || cleanQ.equals("give another") ||
                cleanQ.contains("this topic") || cleanQ.contains("particular topic") ||
                cleanQ.contains("previous topic") || cleanQ.contains("same topic") ||
                cleanQ.contains("example") || cleanQ.contains("previous year") || cleanQ.contains("pyq") ||
                cleanQ.contains("question paper") || cleanQ.contains("past paper") || cleanQ.contains("exam paper") ||
                cleanQ.contains("questions") || cleanQ.contains("question") ||
                cleanQ.contains("yes") || cleanQ.contains("do so") || cleanQ.contains("do it");

        String contextTopic = null;
        if (priorMessages != null && !priorMessages.isEmpty()) {
            for (int i = priorMessages.size() - 1; i >= 0; i--) {
                MessageRecord prev = priorMessages.get(i);
                if ("user".equalsIgnoreCase(prev.getRole()) && prev.getContent() != null) {
                    String prevText = prev.getContent().trim();
                    if (!prevText.equalsIgnoreCase(query) && prevText.length() > 3 && !isConversationalOrGreeting(prevText)) {
                        contextTopic = prevText;
                        break;
                    }
                }
            }
        }

        StringBuilder enriched = new StringBuilder();
        if (activeSubject != null && !activeSubject.trim().isEmpty() && !cleanQ.contains(activeSubject.toLowerCase())) {
            enriched.append(activeSubject.trim()).append(" ");
        }
        if (isExplicitFollowUp && contextTopic != null) {
            enriched.append(contextTopic).append(" ");
        }
        enriched.append(query);

        return enriched.toString().trim();
    }

    private String buildGroundedPrompt(String query, boolean isGreeting, String studyMode, String activeSubject, List<RetrievedChunk> primaryChunks, List<RetrievedChunk> pyqChunks, List<AttachmentRecord> attachments) {
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

        if (activeSubject != null && !activeSubject.trim().isEmpty()) {
            sb.append("=== ACTIVE FOCUS SUBJECT ===\n");
            sb.append("Selected Course Subject: ").append(activeSubject.trim()).append("\n");
            sb.append("The student has explicitly focused on ").append(activeSubject.trim()).append(".\n");
            sb.append("All references to 'Unit 1', 'Unit 2', syllabus modules, exam questions, code, and concepts in this session STRICTLY refer to ")
              .append(activeSubject.trim()).append(", NOT to any other course or default subject.\n\n");
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
        } else if (activeSubject != null && !activeSubject.trim().isEmpty()) {
            sb.append("=== SRM COURSE REFERENCE MATERIALS ===\n");
            sb.append("(No direct reference notes retrieved for ").append(activeSubject.trim()).append(". Teach and explain concepts strictly for ")
              .append(activeSubject.trim()).append(" using your expert domain knowledge and syllabus understanding without cross-subject confusion.)\n\n");
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
        if (activeSubject != null && !activeSubject.trim().isEmpty()) {
            sb.append("1. ACTIVE SUBJECT LOCK: You are teaching ").append(activeSubject.trim()).append(".\n");
            sb.append("2. Under NO circumstances should you switch courses (e.g. do not switch from Java/APP to Software Engineering, Calculus, or Economics).\n");
            sb.append("3. All questions, past exam papers, code snippets, and explanations must remain 100% focused on ").append(activeSubject.trim()).append(".\n");
        }
        sb.append("4. NEVER jump to an unrelated course unless the student explicitly asks for a different subject.\n\n");

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
