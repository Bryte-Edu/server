package com.mistral.api

import com.mistral.api.apis.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Central Mistral client. Configure once and reuse across your app.
 * Provides typed sub-APIs for Models, Chat, Embeddings, Files, Transcriptions,
 * Agents, FineTuning, Moderation, Batch, Conversations, Libraries.
 */
class MistralClient(
    val apiKey: String,
    val baseUrl: String = "https://api.mistral.ai",
    httpClient: HttpClient? = null,
) : AutoCloseable {

    internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    internal val http: HttpClient = httpClient ?: HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { logger = Logger.SIMPLE; level = LogLevel.INFO }
        install(HttpTimeout) { requestTimeoutMillis = 120_000 }
        expectSuccess = false
    }

    internal fun authHeader(): Pair<String, String> = "Authorization" to "Bearer $apiKey"
    internal fun basePath(path: String) = if (path.startsWith("/")) "$baseUrl$path" else "$baseUrl/$path"

    // Sub-APIs
    val models = ModelsApi(this)
    val chat = ChatApi(this)
    val embeddings = EmbeddingsApi(this)
    val files = FilesApi(this)
    val transcription = TranscriptionApi(this)
    val agents = AgentsApi(this)
    val fineTuning = FineTuningApi(this)
    val moderation = ModerationApi(this)
    val batch = BatchApi(this)
    val conversations = ConversationsApi(this)
    val libraries = LibrariesApi(this)
    val ocr = OcrApi(this)

    override fun close() {
        try {
            http.close()
        } catch (_: Exception) {
        }
    }
}