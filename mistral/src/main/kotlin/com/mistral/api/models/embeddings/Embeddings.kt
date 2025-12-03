package com.mistral.api.models.embeddings

import kotlinx.serialization.Serializable

@Serializable
data class EmbeddingsRequest(val model: String, val input: List<String>)


@Serializable
data class EmbeddingsResponse(
    val id: String,
    val `object`: String,
    val model: String,
    val data: List<EmbeddingItem>,
    val usage: EmbeddingsUsage? = null
)


@Serializable
data class EmbeddingItem(val embedding: List<Double>, val index: Int)


@Serializable
data class EmbeddingsUsage(val prompt_tokens: Int? = null, val total_tokens: Int? = null)