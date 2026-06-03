package com.mistral.api.models.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null
)

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    val `object`: String,
    val model: String,
    val choices: List<ChatChoice>
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ChatStreamEvent(val payload: String)