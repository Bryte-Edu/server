# Bryte Backend Infrastructure

This document provides a technical overview of the architectural components, data pipelines, and operational logic
governing the Bryte backend.

---

## 1. System Architecture

Bryte is built as a **distributed multi-module Kotlin project**, optimized for code sharing and low-latency
communication between services.

### Multi-Module Structure

- **`:server` (Ktor 3.5):** The primary execution engine. It handles HTTP/WebSocket traffic, AI agent orchestration, and
  long-running ingestion tasks using the CIO engine.
- **`:core` (Kotlin Multiplatform):** The shared domain layer. Contains all serializable models, the FSRS algorithm
  implementation, and `kotlinx.rpc` service interfaces. This ensures binary compatibility and type safety across the
  server and clients.
- **`:mistral`:** A standalone SDK module for Mistral AI services (OCR, Chat, Embeddings, Files).
- **`:client`:** A reference implementation utilizing `ktor-client` and `krpc` to consume server-side services.

### Communication Protocol (kRPC)

The system uses **kotlinx.rpc (kRPC)** over WebSockets for full-duplex, type-safe communication.

- **Contract-First:** API contracts are defined as standard Kotlin interfaces in the `:core` module.
- **Service Multiplexing:** Multiple services (e.g., `SessionService`, `FlashcardService`) are multiplexed over a single
  WebSocket connection using kRPC’s routing capabilities.

---

## 2. Data Persistence & Retrieval

Bryte utilizes a hybrid database strategy to handle relational, semantic, and vector data.

### Relational Layer (Supabase / PostgreSQL)

- **Persistence:** Manages user data, document metadata, session states, and historical analytics.
- **Auth:** Integrated with Supabase Auth (JWT) for secure, row-level security (RLS) enabled access.
- **PostgREST:** Used for standard CRUD operations on metadata.

### Semantic Layer (Neo4j GraphRAG)

- **Vector Search:** Neo4j Vector Index stores high-dimensional embeddings (1024-dim from Mistral) for semantic chunk
  retrieval.
- **Graph Topology:** Chunks are nodes; relationships (`RELATED_TO`) are weighted edges.
- **Cypher Retrieval:** Custom queries perform multi-hop traversal to synthesize context from prerequisites and related
  cross-document topics.

---

## 3. AI & Orchestration Engine

The backend manages stateful LLM interactions through a reactive orchestration layer.

### Koog Framework

- **Agent Strategies:** Uses `AIAgentGraphStrategy` to define non-linear generation workflows (research -> synthesize ->
  evaluate).
- **History Management:** Implements autonomous history compression once a session exceeds 100k tokens to maintain model
  coherence and performance.
- **Tool Registry:** Specialized toolsets allow agents to call internal functions for GraphRAG search, document topic
  retrieval, and exhaustion signaling.

### Resilient Generation Pipelines

- **Markdown Stream Parser:** A custom event-driven parser that converts raw Markdown streams from the LLM into typed
  Kotlin objects in real-time.
- **Structure Recovery:** The `StructureFixingParser` provides a fallback mechanism to repair malformed structured
  data (JSON/Markdown) returned by models under high-load or complex reasoning tasks.

---

## 4. Ingestion & Normalization Pipelines

### Media & Document Parsers

- **YouTube Engine:** Built on the **NewPipe Extractor**, it performs native fetching and TTML-to-text conversion
  without relying on Google’s official YouTube API.
- **Web Normalization:** A recursive parser that cleans raw HTML and utilizes Gemini to reconstruct hierarchical notes
  from unstructured web text.
- **Mistral OCR:** A high-fidelity pipeline for PDF processing, leveraging the custom `mistral` SDK to handle document
  uploads, OCR recognition, and signed URL generation.

---

## 5. Engineering Standards & Concurrency

- **Structured Concurrency:** All I/O and AI generation tasks are managed via Kotlin Coroutines and Flows, ensuring that
  the server remains non-blocking even during heavy GPU-bound model tasks.
- **FSRS Scheduling:** A native Kotlin port of the FSRS v4 algorithm, allowing for high-performance scheduling
  calculations without external library overhead.
- **Type Safety:** 100% of data transfer is governed by `kotlinx.serialization` and `kotlinx.rpc`, eliminating the
  possibility of runtime schema mismatches.
