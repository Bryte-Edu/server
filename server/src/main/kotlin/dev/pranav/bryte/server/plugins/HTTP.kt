package dev.pranav.bryte.server.plugins

import dev.pranav.bryte.model.CreateSessionRequest
import dev.pranav.bryte.model.FlashcardRequest
import io.ktor.server.application.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.requestvalidation.*

fun Application.configureHTTP() {
    install(DefaultHeaders) {
        header("Bryte-Version", "1.0.0")
    }
    install(RequestValidation) {
        validate<CreateSessionRequest> { request ->
            if (request.source.isBlank()) {
                ValidationResult.Invalid("source cannot be blank")
            } else {
                ValidationResult.Valid
            }
        }
        validate<FlashcardRequest> { request ->
            if (request.documentId.isBlank()) {
                ValidationResult.Invalid("documentId cannot be blank")
            } else {
                ValidationResult.Valid
            }
        }
    }
}
