package dev.pranav.bryte.server.models

import dev.pranav.bryte.server.document.DocumentType
import kotlinx.serialization.Serializable

@Serializable
data class FlashcardRequest(
    val documentId: String
)

@Serializable
data class CreateSessionRequest(
    val docType: DocumentType,
    val source: String
)
