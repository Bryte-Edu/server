package dev.pranav.bryte.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class SessionCreateResponse(
    val sessionId: String
)