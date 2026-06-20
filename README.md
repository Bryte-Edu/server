# Bryte Backend

Bryte is a Kotlin Multiplatform (KMP) backend infrastructure designed for multi-modal document ingestion, GraphRAG-based knowledge synthesis, and mathematically optimized spaced repetition. 

It processes complex inputs (PDFs, YouTube transcripts, web pages) into structured learning ecosystems using autonomous AI agents, and serves them to clients via type-safe, full-duplex WebSockets.

For a deep dive into the system architecture, data pipelines, and orchestration logic, see [INFRASTRUCTURE.md](./INFRASTRUCTURE.md).

---

## Core Capabilities

### GraphRAG & Semantic Retrieval
* **Hybrid Storage:** Utilizes Neo4j for both 1024-dim vector storage (via Mistral) and topological graph relationships.
* **Weighted Cypher Queries:** Custom retrieval algorithms balance local document focus with cross-document knowledge bridges using configurable `docBias` and `crossDocPenalty` weights.
* **Reranking:** Integrates Cohere to rerank retrieved chunks before passing them to the LLM context window.

### Agentic Orchestration (Koog Framework)
* **Stateful Agents:** Uses JetBrains' Koog framework to manage non-linear generation workflows (e.g., research → synthesize → evaluate).
* **Context Management:** Implements autonomous history compression to maintain coherence during long-context sessions (100k+ tokens).
* **Resilient Streaming:** Features a custom event-driven `MarkdownStreamingParser` that processes fragmented LLM tokens in real-time, including heuristic recovery for broken table structures mid-stream.

### Cognitive Scheduling (FSRS v4)
* **Native Implementation:** A 100% native Kotlin port of the Free Spaced Repetition Scheduler (FSRS v4) algorithm.
* **State Tracking:** Calculates and stores Stability, Difficulty, and Retrievability for every learnable unit without relying on external scheduling libraries.
* **Dynamic Analytics:** Derives real-time "Topic Readiness Scores" and session consistency metrics based on historical lapse rates and FSRS state transitions.

### Multi-Modal Ingestion
* **PDF/Docs:** Dedicated `:mistral` SDK module handling file uploads, OCR recognition, and signed URL generation.
* **YouTube:** Native extraction via the NewPipe Extractor, bypassing official APIs, with custom TTML-to-text sanitization.
* **Webpages:** Recursive HTML cleaning and LLM-powered reconstruction of hierarchical notes from unstructured web text.

### Contract-First Communication
* **kotlinx.rpc:** API contracts are defined as standard Kotlin interfaces in the `:core` module.
* **Full-Duplex Streaming:** Uses `kotlinx.rpc` over WebSockets to stream generated flashcards and questions to the client the millisecond they are parsed, multiplexing multiple services over a single connection.

---

## Tech Stack

* **Language:** Kotlin 2.4.0 (Multiplatform)
* **Server:** Ktor 3.5 (CIO Engine)
* **AI & Agents:** JetBrains Koog, OpenAI, Gemini, Mistral
* **Databases:** Supabase (PostgreSQL / Auth), Neo4j (Graph & Vector)
* **Communication:** `kotlinx.rpc` (WebSockets), `kotlinx.serialization`
* **Dependency Injection:** Koin

---

## Project Structure

The repository is structured as a Gradle multi-module project optimized for code sharing and binary compatibility:

| Module | Description |
| :--- | :--- |
| **`:server`** | The primary Ktor execution engine. Houses AI agent orchestration, GraphRAG Cypher queries, FSRS scheduling, and ingestion pipelines. |
| **`:core`** | The shared KMP domain layer. Contains all serializable models, the FSRS algorithm, and `kotlinx.rpc` service interfaces. |
| **`:mistral`** | A standalone, production-grade SDK wrapping the Mistral AI API (OCR, Chat, Embeddings, Files) with SSE streaming support. |
| **`:client`** | Reference KMP client utilizing `ktor-client` and `krpc` to consume server-side services and content streams. |
| **`:test-ui`** | A Swing-based desktop suite for end-to-end verification of generation pipelines and GraphStream visualization. |

---

## Environment Configuration

The server requires the following environment variables to be set (see `Env.kt`):

```env
# AI Providers
GEMINI_API_KEY=...
MISTRAL_API_KEY=...
AZURE_API_URL=...
AZURE_API_KEY=...

# Databases
SUPABASE_URL=...
SUPABASE_KEY=...
NEO4J_URI=...
NEO4J_USERNAME=...
NEO4J_PASSWORD=...

# Auth (Supabase JWK)
JWK_X=...
JWK_Y=...
```

---

## License

This repository is **proprietary, source-available software**. It is dual-licensed:
1. **Source-Available Developer License:** Free for personal, educational, and non-commercial use.
2. **Commercial License:** Required for any corporate, production, SaaS, or revenue-generating deployment.

See [LICENSE.md](./LICENSE.md) for full legal terms.
