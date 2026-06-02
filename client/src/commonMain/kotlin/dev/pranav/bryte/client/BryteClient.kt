package dev.pranav.bryte.client

import dev.pranav.bryte.FlashcardService
import dev.pranav.bryte.SessionService
import dev.pranav.bryte.model.CreateSessionRequest
import dev.pranav.bryte.model.DocumentType
import dev.pranav.bryte.model.FlashcardRequest
import dev.pranav.bryte.model.SessionCreateResponse
import dev.pranav.bryte.model.card.Flashcard
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService

class BryteClient(
    var baseUrl: String,
    var authToken: String? = null
) {
    private val wsUrl: String
        get() = baseUrl.replace("http", "ws")

    private val client = HttpClient(io.ktor.client.engine.cio.CIO) {
        installKrpc {
            serialization {
                json()
            }
        }

        install(WebSockets)

        install(ContentNegotiation) {
            json()
        }
    }

    private fun HttpRequestBuilder.applyAuth() {
        authToken?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
    }

    suspend fun createSession(docType: DocumentType, source: String): SessionCreateResponse {
        return client.post {
            url("$baseUrl/api/create-session")
            applyAuth()
            setBody(CreateSessionRequest(docType, source))
            contentType(ContentType.Application.Json)
        }.body()
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
        val rpcClient = client.rpc {
            url {
                takeFrom(wsUrl)
                encodedPath = "/api/rpc/$sessionId"
            }
        }
        return rpcClient.withService<SessionService>()
    }

    suspend fun getFlashcardRpc(sessionId: String): FlashcardService {
        val rpcClient = client.rpc {
            url {
                takeFrom(wsUrl)
                encodedPath = "/api/rpc/flashcards/$sessionId"
            }
        }
        return rpcClient.withService<FlashcardService>()
    }

    fun close() {
        client.close()
    }
}
