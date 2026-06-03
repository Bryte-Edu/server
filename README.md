# Bryte Backend

Server-side infrastructure for the Bryte learning application. It manages multi-source document ingestion, AI-driven
content synthesis, and mathematically optimized review scheduling through a multi-module Kotlin architecture.

## Server-Side Content Synthesis (Flashcards & Questions)

The backend utilizes the **Koog framework** and multi-agent workflows to transform processed document chunks into
structured learning material.

- **Evaluative Content:** Synthesizes pedagogical flashcards (front/back with rationales) and multiple quiz formats
  including Multiple Choice (MCQ), "Spot the Error", and "Match the Following".
- **Agentic Orchestration:** Implements `AIAgentGraphStrategy` to maintain state and coherence over long-context
  documents (100k+ tokens) using autonomous history compression and adaptive tool-calling.
- **Incremental Stream Parsing:** Features a resilient Markdown-to-JSON parser to process fragmented LLM output in
  real-time, ensuring structural integrity and allowing the client to consume generated objects via Kotlin Flows.

## Document Ingestion & Normalization Pipelines

- **YouTube Extraction:** Features a native implementation of the **NewPipe Extractor** to harvest transcripts and
  metadata directly. Includes custom TTML-to-text sanitization to remove artifacts and speaker labels.
- **Web Normalization:** Uses Gemini-based parsing with a recursive `StructureFixingParser` to convert raw, noisy HTML
  into structured study notes and hierarchical topics.
- **OCR & Technical Extraction:** Includes a dedicated `mistral` SDK module providing a feature-complete implementation
  of the Mistral AI OCR, File, and Chat APIs for high-fidelity processing of complex PDFs.

## Cognitive Scheduling Engine (FSRS v4)

The backend implements the **Free Spaced Repetition Scheduler (FSRS v4)** to manage adaptive review cycles.

- **Scheduling Logic:** Predicts optimal review intervals by tracking **Stability**, **Difficulty**, and *
  *Retrievability** for each learnable unit.
- **Cognitive Analytics:** Derives real-time "Readiness Scores" and mastery metrics from historical performance data and
  lapse counts.
- **Multiplatform Parity:** Domain models and service interfaces reside in a **Kotlin Multiplatform (KMP)** core,
  ensuring absolute behavioral consistency across the ecosystem while calculations are managed by the server.

## Knowledge Graph & Semantic Retrieval (GraphRAG)

Integrates high-dimensional vector search with topological graph analysis in **Neo4j** to manage semantic context.

- **Relationship Mapping:** Models conceptual dependencies (e.g., "Prerequisite Of", "Synthesizes") between document
  sections to support multi-hop context retrieval.
- **Weighted Retrieval:** Employs a Cypher-driven engine that balances local document focus with global knowledge
  bridges based on embedding similarity and user-specific library data.
- **Knowledge Inter-linking:** Automatically establishes semantic relationships between content chunks during ingestion
  to build a persistent knowledge map.

## System Architecture & Ecosystem Interoperability

- **Contract-First Communication:** Uses **kotlinx.rpc** over WebSockets to enforce type-safe service interfaces and
  eliminate API drift between the server and the consuming client.
- **Concurrent Execution:** Built on **Ktor 3.5** and structured concurrency, utilizing Kotlin Coroutines and Flows for
  high-throughput, non-blocking AI operations.
- **Tech Stack:** Kotlin 2.0, Supabase (PostgreSQL/Auth), Neo4j, Koin (DI), OpenAI, Gemini, Mistral, Cohere (Rerank).

## Project Organization

- `server/`: Primary backend implementation containing AI generation logic, retrieval services, and ingestion pipelines.
- `core/`: Kotlin Multiplatform (KMP) module housing shared models and RPC service definitions.
- `mistral/`: Standalone, feature-complete SDK for Mistral AI platform integration.
- `client/`: Reference Kotlin client for consuming server-side services and content streams.
- `test-ui/`: Suite for end-to-end verification of generation pipelines.
