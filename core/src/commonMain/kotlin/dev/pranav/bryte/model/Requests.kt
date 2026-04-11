package dev.pranav.bryte.model

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
