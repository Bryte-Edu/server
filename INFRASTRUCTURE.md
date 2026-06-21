# Bryte Backend Infrastructure

This document details the architectural components, data pipelines, and algorithmic implementations governing the Bryte backend. For a high-level overview, refer to the [README](./README.md).

---

## 1. System Topology & Communication

The system is structured as a Gradle multi-module project designed for strict binary compatibility and zero-drift communication between the server and clients.

### Module Architecture
* **`:server`**: The primary Ktor 3.5 execution engine. Handles HTTP/WebSocket routing, AI agent orchestration, database interactions, and ingestion pipelines.
* **`:core`**: The shared Kotlin Multiplatform (KMP) domain layer. Contains all `kotlinx.serialization` models, the native FSRS v4 algorithm, and `kotlinx.rpc` service interfaces.
* **`:mistral`**: A standalone, feature-complete SDK wrapping the Mistral AI API (OCR, Chat, Embeddings, Files, Agents) with native SSE streaming support.
* **`:client`**: Reference KMP client utilizing `ktor-client` and `krpc` to consume server-side services.
* **`:test-ui`**: A Swing-based desktop suite for end-to-end verification of generation pipelines and GraphStream visualization.

### Contract-First RPC (kotlinx.rpc)
Client-server communication bypasses traditional REST for stateful operations, utilizing `kotlinx.rpc` over WebSockets.
* **Service Multiplexing**: `SessionService` and `FlashcardService` are multiplexed over a single WebSocket connection.
* **Full-Duplex Streaming**: Generated content (flashcards, questions) is emitted as `kotlinx.coroutines.flow.Flow` objects, allowing the client to consume and render items the millisecond they are parsed from the LLM stream.

---

## 2. Hybrid Data Persistence (Supabase & Neo4j)

Bryte utilizes a dual-database strategy to separate relational state management from semantic graph traversal.

### Relational Layer (Supabase / PostgreSQL)
* **PostgREST Wrappers**: Type-safe Kotlin delegates (e.g., `DocumentChunkRepository`, `FSRSRepository`) wrap Supabase PostgREST queries for CRUD operations on metadata, session states, and historical analytics.
* **Authentication**: Integrates with Supabase Auth. The server validates ECDSA (P-256) JWTs using the Supabase JWK (`JWK_X`, `JWK_Y`) to enforce row-level user isolation.

### Semantic Layer (Neo4j GraphRAG)
Neo4j acts as the semantic engine, storing both 1024-dimensional vector embeddings (via Mistral) and topological graph relationships.
* **Vector Indexing**: Chunks are indexed using `db.index.vector.queryNodes('chunk_embeddings', ...)` with cosine similarity.
* **Weighted Cypher Retrieval**: The `searchKnowledgeGraphWeighted` function executes custom Cypher queries that balance local document focus with cross-document knowledge bridges. It applies a configurable `docBias` (default `0.15`) to the focus document and a `crossDocPenalty` (default `0.05`) to external documents before sorting by `weightedScore`.
* **Edge Inter-linking**: During ingestion, `interLinkWithDocumentBias` calculates cosine similarity between chunks and creates `RELATED_TO` edges, tagging them with `isInternal` (same document) and `weight` properties.

---

## 3. Agentic Orchestration & Context Management

Generation tasks are managed by the JetBrains Koog framework, utilizing `AIAgentGraphStrategy` to define non-linear execution flows.

### Stateful Agent Strategies
* **Tool-Calling Workflows**: Agents are equipped with specialized toolsets (`DocumentToolset`, `RAGToolset`) allowing them to sequentially request topics (`getNextTopic`), trigger GraphRAG searches (`graphContext`), and signal completion (`contentExhausted`).
* **Autonomous History Compression**: To prevent context window exhaustion during long-document processing, the agent implements `HistoryCompressionStrategy.FromLastNMessages(10)`. When the prompt history exceeds 100,000 tokens, the system autonomously compresses older messages while preserving the most recent 10 interactions.
* **Multi-LLM Execution**: The `FlashcardGenerator` utilizes Azure/OpenAI endpoints, while the `QuestionGenerator` routes through Google Gemini, managed via `MultiLLMPromptExecutor`.

### Resilient Stream Parsing
LLM outputs are processed in real-time via a custom `MarkdownStreamingParser`.
* **Structure Fallback**: For complex structured outputs, the system employs a `StructureFixingParser` that automatically retries and repairs malformed JSON/Markdown up to 4 times using a lightweight model (e.g., `Gemini2_5FlashLite`).

---

## 4. Multi-Modal Ingestion Pipelines

The backend normalizes unstructured media into hierarchical `ParsedDocument` objects containing `Topic` chunks.

| Source | Implementation Details |
| :--- | :--- |
| **PDF / Docs** | Utilizes the custom `:mistral` SDK to upload files and trigger Mistral OCR. The resulting markdown is parsed using regex to extract hierarchical topics based on heading levels, preserving image bounding boxes and base64 payloads. |
| **YouTube** | Bypasses the official API by integrating the **NewPipe Extractor**. Implements a custom Ktor-based `NewPipeDownloader` to fetch TTML subtitles. Includes a `ttmlToText()` sanitizer that strips HTML tags, decodes numeric entities, and removes speaker labels. |
| **Webpages** | Fetches raw HTML via Ktor, strips scripts/styles/nav tags via regex, and passes the cleaned text to Gemini. Uses the `StructureFixingParser` to reconstruct hierarchical study notes from noisy web text. |

---

## 5. Cognitive Scheduling Engine (FSRS v4)

The backend includes a 100% native Kotlin port of the Free Spaced Repetition Scheduler (FSRS v4) algorithm, located in `SpacedRepetitionScheduler`.

### State Transitions & Calculations
* **Metrics**: Tracks `Stability` (memory strength), `Difficulty` (item hardness), and `Retrievability` (probability of recall) for every question.
* **Next State Calculation**: `calculateNextState` processes user grades (1-4) to update difficulty and stability. It calculates the optimal interval in days using the FSRS v4 weight matrix (`w[0]` to `w[16]`) and determines the next review timestamp.
* **Lapse Handling**: Grades of 1 trigger a lapse, incrementing the `lapses` counter, transitioning the state to `Relearning`, and recalculating stability using the specific FSRS lapse formula.

### Topic Analytics & Readiness
* **Readiness Score**: `calculateReadiness` derives a dynamic "Topic Readiness Score" (0-100) by blending the average stability and difficulty of all questions within a topic chunk.
* **First-Try Bonus**: The algorithm applies a bonus to the readiness score based on the ratio of `correctFirstTry` to `totalReviews`, providing a more accurate reflection of initial comprehension.
* **Session Analytics**: Aggregates topic-level metrics to compute session-wide accuracy, consistency (inversely proportional to lapse rate), and generates dynamic study recommendations based on struggling topics.

---

## 6. Concurrency & Error Handling

* **Structured Concurrency**: All I/O, database queries, and AI generation tasks are executed within Kotlin Coroutines. The server utilizes the Ktor CIO engine to ensure non-blocking throughput during heavy, long-running LLM streaming operations.
* **Centralized Error Mapping**: The `StatusPages` plugin intercepts custom `ApiException` subclasses (e.g., `BadRequestException`, `ForbiddenException`, `ExternalServiceException`) and maps them to appropriate HTTP status codes and standardized `ErrorResponse` payloads.
