package com.mistral.api.models.transcriptions
import kotlinx.serialization.Serializable
@Serializable
data class TranscriptionRequest(
    val model: String,
    val file_url: String? = null,
    val language: String? = null,
    val temperature: Double? = null
)

@Serializable
data class TranscriptionResponse(
    val text: String,
    val language: String? = null,
    val segments: List<TranscriptionSegment>? = null
)

@Serializable
data class TranscriptionSegment(
    val id: Int? = null,
    val start: Double? = null,
    val end: Double? = null,
    val text: String? = null
)