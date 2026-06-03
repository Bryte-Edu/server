package com.mistral.api.models.conversations

import kotlinx.serialization.Serializable

@Serializable
data class ConversationCreateRequest(val title: String? = null)

@Serializable
data class ConversationResponse(
    val id: String,
    val title: String? = null,
    val messages: List<ConversationMessage>? = null
)

@Serializable
data class ConversationMessage(val role: String, val content: String)