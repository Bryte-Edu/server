package dev.pranav.bryte.client

import dev.pranav.bryte.FlashcardService
import dev.pranav.bryte.SessionService
import dev.pranav.bryte.model.DocumentType
import dev.pranav.bryte.model.FlashcardRequest
import dev.pranav.bryte.model.SessionCreateResponse
import dev.pranav.bryte.model.card.Flashcard
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService

class BryteClient(
    var baseUrl: String,
    var authToken: String? = null
) {
    private val wsUrl = baseUrl.replace("http", "ws")

    private val client = HttpClient(io.ktor.client.engine.cio.CIO) {
        installKrpc { serialization { json() } }
        install(SSE)
        install(WebSockets)
        install(ContentNegotiation) { json() }
    }

    private fun HttpRequestBuilder.applyAuth() {
        authToken?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
    }

    suspend fun createSession(
        docType: DocumentType,
        source: String,
        onStatus: ((String) -> Unit)? = null
    ): SessionCreateResponse {
        var sessionId: String? = null

        client.sse(
            urlString = "$baseUrl/api/create-session",
            request = {
                applyAuth()

                parameter("docType", docType.name)
                parameter("source", source)
            }
        ) {
            sessionId = incoming
                .onEach { event ->
                    when (event.event) {
                        "status" -> onStatus?.invoke(event.data ?: "")
                        "error" -> error(event.data ?: "Session creation failed")
                        // "heartbeat" events are silently ignored
                    }
                }
                .first { it.event == "done" }
                .data
        }

        return SessionCreateResponse(
            sessionId ?: error("SSE stream closed without a session ID")
        )
    }

    suspend fun getFlashcards(documentId: String): List<Flashcard> {
        return client.post {
            url("$baseUrl/api/flashcards")
            applyAuth()
            setBody(FlashcardRequest(documentId))
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun getGraphVisualization(documentId: String): String {
        return client.get("$baseUrl/api/graph/$documentId") {
            applyAuth()
        }.bodyAsText()
    }

    suspend fun getSessionRpc(sessionId: String): SessionService {
        return client.rpc {
            applyAuth()

            url {
                takeFrom(wsUrl);
                encodedPath = "/api/rpc/$sessionId"

                authToken?.let { parameters.append("token", it) }
            }
        }.withService<SessionService>()
    }

    suspend fun getFlashcardRpc(sessionId: String): FlashcardService {
        return client.rpc {
            url {
                takeFrom(wsUrl);
                encodedPath = "/api/rpc/flashcards/$sessionId"

                authToken?.let { parameters.append("token", it) }
            }
        }.withService<FlashcardService>()
    }

    fun close() = client.close()
}
