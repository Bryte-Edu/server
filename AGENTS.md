# AGENTS.md

## Project Snapshot

- Kotlin multi-module project: `server` (Ktor runtime), `core` (shared RPC contracts + models), `client` (Ktor/kRPC
  client), `mistral` (Mistral API wrapper) (`settings.gradle.kts`).
- Shared contract-first flow: `core/src/commonMain/kotlin/SessionService.kt` and
  `core/src/commonMain/kotlin/FlashcardService.kt` define `@Rpc` interfaces; server implements, client consumes.
- Most business data lives in Supabase PostgREST tables via repository wrappers in
  `server/src/main/kotlin/dev/pranav/bryte/server/postgrest/`.

## Architecture And Data Flow

- Server boot sequence is centralized in `server/src/main/kotlin/dev/pranav/bryte/server/Application.kt`; plugin wiring
  is split into `server/src/main/kotlin/dev/pranav/bryte/server/plugins/*.kt`.
- Session creation path (`GET /api/create-session`) in
  `server/src/main/kotlin/dev/pranav/bryte/server/routes/SessionRoutes.kt` does: auth -> parse input document -> insert
  `documents`/`document_chunks` in Supabase -> embed chunks -> ingest/interlink Neo4j -> create quiz session.
- Parser dispatch is document-type driven (`server/src/main/kotlin/dev/pranav/bryte/server/util/ext/parsers.kt`):
  `PDF/DOCX/PPTX -> FileParser`, `YOUTUBE -> YouTube`.
- kRPC endpoints are per-session and auth-scoped in
  `server/src/main/kotlin/dev/pranav/bryte/server/routes/RpcRoutes.kt`:
    - `/api/rpc/{sessionId}` -> `SessionServiceImpl`
    - `/api/rpc/flashcards/{sessionId}` -> `FlashcardServiceImpl`
- Client mirrors these paths in `client/src/commonMain/kotlin/dev/pranav/bryte/client/BryteClient.kt`.

## Build/Test/Run Workflows

- Root build: `./gradlew build`
- Run server: `./gradlew :server:run`
- Server tests only: `./gradlew :server:test`
- Fat jar/image tasks (from README): `:server:buildFatJar`, `:server:buildImage`, `:server:runDocker`.
- Ktor entrypoint is `dev.pranav.bryte.server.ApplicationKt.module` (`server/src/main/resources/application.yaml`).

## Required Local Configuration

- `server/build.gradle.kts` generates `BuildConfig.kt` from `server/.env` at compile time; constants are imported as
  top-level symbols (for example `MISTRAL_API_KEY`, `SUPABASE_URL`, `SUPABASE_KEY`, `GEMINI_API_KEY`, `NEO4J_URI`,
  `NEO4J_USERNAME`, `NEO4J_PASSWORD`).
- If `.env` is missing or incomplete, server compilation/runtime will fail where those constants are referenced.

## Codebase-Specific Patterns

- Supabase access pattern uses delegated properties for repositories (for example `val sessions by supabase.sessions()`)
  from `server/src/main/kotlin/dev/pranav/bryte/server/util/ext/tables.kt`; follow this style when adding new table
  accessors.
- Authenticated route user context is always resolved with `val userId by call.userId()` (
  `server/src/main/kotlin/dev/pranav/bryte/server/util/ext/user.kt`).
- RPC contract changes should be done in `core` first, then update both implementations in `server/services` and client
  call sites.
- AI generation pipelines (`server/src/main/kotlin/dev/pranav/bryte/server/ai/QuestionGenerator.kt`,
  `server/src/main/kotlin/dev/pranav/bryte/server/ai/FlashcardGenerator.kt`) stream markdown and parse into typed
  models; preserve parser structure contracts when modifying prompts/output schemas.

## Integration Boundaries

- Supabase: auth + PostgREST + storage client created in
  `server/src/main/kotlin/dev/pranav/bryte/server/util/ext/supabase.kt`.
- Neo4j graph ingestion/retrieval is encapsulated in `server/src/main/kotlin/dev/pranav/bryte/server/migration/Neo4j.kt`
  and is invoked during session creation + RAG lookup.
- LLM providers are mixed by feature:
    - Koog plugin setup (`server/src/main/kotlin/dev/pranav/bryte/server/plugins/Frameworks.kt`)
    - Mistral OCR in `server/src/main/kotlin/dev/pranav/bryte/server/document/parser/file/MistralService.kt`
    - YouTube transcript structuring in
      `server/src/main/kotlin/dev/pranav/bryte/server/document/parser/youtube/YouTube.kt`

## Known Practical Notes For Agents

- API routes currently use `GET` with JSON request bodies for `/api/create-session` and `/api/flashcards` (client and
  server are aligned on this behavior).
- Existing tests are minimal (`server/src/test/kotlin/ApplicationTest.kt`) and do not cover end-to-end
  Supabase/Neo4j/LLM flows; validate those paths manually when changing ingestion/generation logic.

