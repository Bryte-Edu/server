package dev.pranav.bryte.server.routes

import dev.pranav.bryte.model.session.DocumentChunk
import dev.pranav.bryte.model.session.DocumentItem
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.server.models.CreateSessionRequest
import dev.pranav.bryte.server.models.SessionCreateResponse
import dev.pranav.bryte.server.util.ext.documentChunks
import dev.pranav.bryte.server.util.ext.documents
import dev.pranav.bryte.server.util.ext.getDocumentParser
import dev.pranav.bryte.server.util.ext.sessions
import dev.pranav.bryte.server.util.ext.supabase
import dev.pranav.bryte.server.util.ext.userId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing


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
                            "num_pages" to parsed.topics.maxOf { it.pages.max() })
                    )
                )

                documentChunks.insert(parsed.topics.map {
                    DocumentChunk(
                        documentId = documentItem!!.id,
                        header = it.header,
                        content = it.content,
                        images = it.images,
                        pageNumber = it.pages
                    )
                })

                val session = sessions.insert(
                    Session(
                        userId = userId,
                        documentId = documentItem!!.id,
                        difficulty = "medium",
                    )
                )

                call.respond(HttpStatusCode.OK, SessionCreateResponse(session!!.id))
            }
        }
    }
}
