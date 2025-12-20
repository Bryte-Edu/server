package dev.pranav.bryte.server.routes

import dev.pranav.bryte.model.session.DocumentChunk
import dev.pranav.bryte.model.session.DocumentItem
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.server.migration.Neo4jManager
import dev.pranav.bryte.server.models.CreateSessionRequest
import dev.pranav.bryte.server.models.SessionCreateResponse
import dev.pranav.bryte.server.util.ext.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*


fun Application.configureSessionRoutes() {
    routing {
        authenticate("auth-jwt") {
            get("/api/create-session") {
                val userId by call.userId()
                val sessions by supabase.sessions()
                val documents by supabase.documents()
                val documentChunks by supabase.documentChunks()

                val request = call.receive<CreateSessionRequest>()

                val parser = getDocumentParser(request.docType)

                val parsed = parser.parseDocument(request.source)
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Failed to parse document")

                val documentItem = documents.insert(
                    DocumentItem(
                        userId = userId,
                        title = parsed.title,
                        type = request.docType.name,
                        source = request.source,
                        metadata = mapOf(
                            "num_pages" to parsed.topics.maxOf { it.pages.max() }.toString()
                        )
                    )
                )

                val chunks = documentChunks.insert(parsed.topics.map {
                    DocumentChunk(
                        documentId = documentItem.id,
                        header = it.header,
                        content = it.content,
                        images = it.images,
                        pageNumber = it.pages
                    )
                })

                val graph = Neo4jManager()
                graph.ingestDocument(documentItem, chunks)

                val session = sessions.insert(
                    Session(
                        userId = userId,
                        documentId = documentItem.id,
                        difficulty = "medium",
                    )
                )

                call.respond(HttpStatusCode.OK, SessionCreateResponse(session!!.id))
            }
        }
    }
}
