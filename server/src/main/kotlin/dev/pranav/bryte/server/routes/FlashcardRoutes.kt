package dev.pranav.bryte.server.routes

import dev.pranav.bryte.model.FlashcardRequest
import dev.pranav.bryte.server.errors.BadRequestException
import dev.pranav.bryte.server.errors.ForbiddenException
import dev.pranav.bryte.server.util.ext.documents
import dev.pranav.bryte.server.util.ext.flashcards
import dev.pranav.bryte.server.util.ext.supabase
import dev.pranav.bryte.server.util.ext.userId
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureFlashcardRoutes() {
    routing {
        authenticate("auth-jwt") {
            post("/api/flashcards") {
                val document = call.receive<FlashcardRequest>()
                call.application.environment.log.info("Fetching flashcards for document: ${document.documentId}")
                val userId by call.userId()
                val flashcardRepository by supabase.flashcards()
                val documents by supabase.documents()

                if (document.documentId.isBlank()) {
                    throw BadRequestException("documentId cannot be blank")
                }

                val documentItem = runCatching { documents.getById(document.documentId) }.getOrNull()
                    ?: throw BadRequestException("Document not found")

                if (documentItem.userId != userId) {
                    throw ForbiddenException("Access denied")
                }

                val flashcards = flashcardRepository.getByDocumentId(document.documentId)
                call.respond(HttpStatusCode.OK, flashcards)
            }
        }
    }
}
