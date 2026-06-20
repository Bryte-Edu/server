package dev.pranav.bryte.server.routes

import dev.pranav.bryte.model.CreateSessionRequest
import dev.pranav.bryte.model.SessionCreateResponse
import dev.pranav.bryte.model.session.DocumentChunk
import dev.pranav.bryte.model.session.DocumentItem
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.server.errors.BadRequestException
import dev.pranav.bryte.server.errors.ExternalServiceException
import dev.pranav.bryte.server.migration.Neo4jManager
import dev.pranav.bryte.server.util.ext.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.types.toJson


fun Application.configureSessionRoutes() {
    routing {
        authenticate("auth-jwt") {
            get("/api/graph/{documentId}") {
                call.application.environment.log.info("Fetching graph visualization for document: ${call.parameters["documentId"]}")
                val userId by call.userId()
                val documentId = call.parameters["documentId"] ?: throw BadRequestException("documentId is required")

                try {
                    val graph = Neo4jManager()
                    val result = graph.getGraphVisualization(userId, documentId)
                    call.respond(HttpStatusCode.OK, result.toJson())
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw ExternalServiceException("Failed to fetch graph visualization: ${e.message}")
                }
            }

            post("/api/create-session") {
                call.application.environment.log.info("Starting new session creation. Receiving document payload...")
                val userId by call.userId()
                val sessions by supabase.sessions()
                val documents by supabase.documents()
                val documentChunks by supabase.documentChunks()

                val request = call.receive<CreateSessionRequest>()
                if (request.source.isBlank()) {
                    throw BadRequestException("source cannot be blank")
                }

                call.application.environment.log.info("Parsing document of type: ${request.docType}")
                val parser = getDocumentParser(request.docType)

                val parsed = parser.parseDocument(request.source)
                    ?: throw BadRequestException("Failed to parse document")

                if (parsed.topics.isEmpty()) {
                    throw BadRequestException("No content found in document")
                }

                call.application.environment.log.info("Successfully parsed document into ${parsed.topics.size} topics.")

                val documentItem = documents.insert(
                    DocumentItem(
                        userId = userId,
                        title = parsed.title,
                        type = request.docType.name,
                        source = request.source,
                        metadata = mapOf(
                            "num_pages" to (parsed.topics.asSequence()
                                .flatMap { it.pages.asSequence() }
                                .maxOrNull()
                                ?.toString() ?: "0")
                        )
                    )
                )

                val llmClient =
                    ai.koog.prompt.executor.clients.mistralai.MistralAILLMClient(dev.pranav.bryte.server.MISTRAL_API_KEY)
                val embedder = ai.koog.embeddings.local.LLMEmbedder(
                    llmClient,
                    ai.koog.prompt.executor.clients.mistralai.MistralAIModels.Embeddings.MistralEmbed
                )

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

                if (chunksList.isEmpty()) {
                    throw ExternalServiceException("Failed to create document chunks")
                }

                call.application.environment.log.info("Generated embeddings for ${chunksList.size} chunks. Saving to database...")
                val chunks = documentChunks.insert(chunksList)

                try {
                    call.application.environment.log.info("Ingesting document chunks into Neo4j graph...")
                    val graph = Neo4jManager()
                    graph.ingestDocument(documentItem, chunks)
                    call.application.environment.log.info("Interlinking document nodes with bias...")
                    graph.interLinkWithDocumentBias(documentItem.userId)
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw ExternalServiceException("Failed to index document graph: ${e.message}")
                }

                call.application.environment.log.info("Creating session record in database...")
                val session = sessions.insert(
                    Session(
                        userId = userId,
                        documentId = documentItem.id,
                        difficulty = "medium",
                    )
                )
                    ?: throw ExternalServiceException("Failed to create session")

                call.respond(HttpStatusCode.Created, SessionCreateResponse(session.id))
            }
        }
    }
}
