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
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService

class BryteClient(
    private val baseUrl: String,
    private val authToken: String? = null
) {
    private val client = HttpClient {
        installKrpc {
            serialization {
                json()
            }
        }
        defaultRequest {
            url(baseUrl)
            authToken?.let { bearerAuth(it) }
        }

        install(ContentNegotiation)
    }

    suspend fun createSession(docType: DocumentType, source: String): SessionCreateResponse {
        return client.post("/api/create-session") {
            setBody(CreateSessionRequest(docType, source))
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun getFlashcards(documentId: String): List<Flashcard> {
        return client.post("/api/flashcards") {
            setBody(FlashcardRequest(documentId))
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun getSessionRpc(sessionId: String): SessionService {
        val rpcClient = client.rpc {
            url {
                encodedPath = "/api/rpc/$sessionId"
            }
        }
        return rpcClient.withService<SessionService>()
    }

    suspend fun getFlashcardRpc(sessionId: String): FlashcardService {
        val rpcClient = client.rpc {
            url {
                encodedPath = "/api/rpc/flashcards/$sessionId"
            }
        }
        return rpcClient.withService<FlashcardService>()
    }

    fun close() {
        client.close()
    }
}
