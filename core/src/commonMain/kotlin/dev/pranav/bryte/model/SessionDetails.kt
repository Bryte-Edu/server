package dev.pranav.bryte.model

import kotlinx.serialization.Serializable

@Serializable
data class SessionDetails(
    val sessionId: String,
    val userId: String,
    val documentId: String,
    val createdAt: String,
    val updatedAt: String
)
