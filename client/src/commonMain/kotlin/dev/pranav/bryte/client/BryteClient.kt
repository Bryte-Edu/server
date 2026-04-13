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
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
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

    private val client = HttpClient {
        installKrpc {
            serialization {
                json()
            }
        }

        install(DefaultRequest) {
            // We use interceptors below for dynamic urls/auth, so we don't hardcode them here
        }

        install(WebSockets)

        install(ContentNegotiation) {
            json()
        }
    }

    init {
        client.plugin(HttpSend).intercept { request ->
            request.url {
                 // only override if the host is not fully specified or matches the old baseUrl
                 // Actually, we can just set authority
                 val currentBaseUrl = Url(baseUrl)
                 protocol = currentBaseUrl.protocol
                 host = currentBaseUrl.host
                 port = currentBaseUrl.port
            }
            authToken?.let {
                request.header(HttpHeaders.Authorization, "Bearer $it")
            }
            execute(request)
        }
    }

    suspend fun createSession(docType: DocumentType, source: String): SessionCreateResponse {
        return client.post {
            url("$baseUrl/api/create-session")
            setBody(CreateSessionRequest(docType, source))
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun getFlashcards(documentId: String): List<Flashcard> {
        return client.post {
            url("$baseUrl/api/flashcards")
            setBody(FlashcardRequest(documentId))
            contentType(ContentType.Application.Json)
        }.body()
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
