package dev.pranav.bryte.server.routes

import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient
import ai.koog.prompt.executor.clients.mistralai.MistralAIModels
import dev.pranav.bryte.model.session.DocumentChunk
import dev.pranav.bryte.model.session.DocumentItem
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.server.MISTRAL_API_KEY
import dev.pranav.bryte.server.errors.BadRequestException
import dev.pranav.bryte.server.errors.ExternalServiceException
import dev.pranav.bryte.server.migration.Neo4jManager
import dev.pranav.bryte.server.util.ext.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import io.modelcontextprotocol.kotlin.sdk.types.toJson
import kotlin.time.Duration.Companion.seconds

private val llmClient by lazy {
    MistralAILLMClient(MISTRAL_API_KEY)
}
private val embedder by lazy {
    LLMEmbedder(
        llmClient,
        MistralAIModels.Embeddings.MistralEmbed
    )
}

fun Application.configureSessionRoutes() {
    routing {
        authenticate("auth-jwt") {
            get("/api/graph/{documentId}") {
                call.application.environment.log.info("Fetching graph visualization for document: ${call.parameters["documentId"]}")
                val userId by call.userId()
                val documentId = call.parameters["documentId"] ?: throw BadRequestException("documentId is required")
                try {
                    val result = Neo4jManager().getGraphVisualization(userId, documentId)
                    call.respond(HttpStatusCode.OK, result.toJson())
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw ExternalServiceException("Failed to fetch graph visualization: ${e.message}")
                }
            }

            sse("/api/create-session") {
                heartbeat {
                    period = 5.seconds
                    event = ServerSentEvent("heartbeat")
                }

                val userId by call.userId()
                val sessions by supabase.sessions()
                val documents by supabase.documents()
                val documentChunks by supabase.documentChunks()

                val docTypeName = call.parameters["docType"] ?: throw BadRequestException("docType required")
                val source = call.parameters["source"] ?: throw BadRequestException("source required")
                val docType = dev.pranav.bryte.model.DocumentType.valueOf(docTypeName)

                if (source.isBlank()) throw BadRequestException("source cannot be blank")

                send(ServerSentEvent(data = "parsing", event = "status"))
                val parsed = getDocumentParser(docType).parseDocument(source)
                    ?: throw BadRequestException("Failed to parse document")
                if (parsed.topics.isEmpty()) throw BadRequestException("No content found in document")

                val documentItem = documents.insert(
                    DocumentItem(
                        userId = userId,
                        title = parsed.title,
                        type = docType.name,
                        source = source,
                        metadata = mapOf(
                            "num_pages" to (parsed.topics.asSequence()
                                .flatMap { it.pages.asSequence() }
                                .maxOrNull()?.toString() ?: "0")
                        )
                    )
                )

                send(ServerSentEvent(data = "embedding", event = "status"))
                val chunksList = parsed.topics.mapIndexed { index, topic ->
                    val embedding = try {
                        embedder.embed(topic.content).values
                    } catch (e: Exception) {
                        e.printStackTrace()
                        throw ExternalServiceException("Failed to generate embeddings: ${e.message}")
                    }
                    DocumentChunk(
                        documentId = documentItem.id,
                        header = topic.header,
                        content = topic.content,
                        images = topic.images,
                        pageNumber = topic.pages,
                        embedding = embedding,
                        index = index
                    )
                }

                val chunks = documentChunks.insert(chunksList)

                send(ServerSentEvent(data = "indexing", event = "status"))
                try {
                    val graph = Neo4jManager()
                    graph.ingestDocument(documentItem, chunks)
                    graph.interLinkWithDocumentBias(documentItem.userId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw ExternalServiceException("Failed to index document graph: ${e.message}")
                }

                val session = sessions.insert(
                    Session(userId = userId, documentId = documentItem.id, difficulty = "medium")
                ) ?: throw ExternalServiceException("Failed to create session")

                send(ServerSentEvent(data = session.id, event = "done"))
            }
        }
    }
}