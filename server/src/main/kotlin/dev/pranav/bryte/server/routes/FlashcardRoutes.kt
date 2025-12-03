package dev.pranav.bryte.server.routes

import dev.pranav.bryte.server.util.ext.flashcards
import dev.pranav.bryte.server.util.ext.supabase
import dev.pranav.bryte.server.util.ext.userId
import dev.pranav.bryte.server.models.FlashcardRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureFlashcardRoutes() {
  routing {
    authenticate("auth-jwt") {
      get("/api/flashcards") {
        val userId by call.userId()
        val flashcardRepository by supabase.flashcards()
        println(userId)

        val document = call.receive<FlashcardRequest>()
        println(document)

        val flashcards = flashcardRepository.getByDocumentId(document.documentId)

        if (flashcards.isNotEmpty()) {
          call.respond(HttpStatusCode.OK, flashcards)
        }


      }
    }
  }
}
