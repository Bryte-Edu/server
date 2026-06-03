package com.mistral.api.models.models
import kotlinx.serialization.Serializable
@Serializable
data class ModelDetail(
    val id: String,
    val objectType: String? = null,
    val created: Long? = null,
    val owned_by: String? = null,
    val capabilities: ModelCapabilities? = null
)

@Serializable
data class ModelCapabilities(
    val completion_chat: Boolean? = null,
    val completion_fim: Boolean? = null,
    val completion: Boolean? = null,
    val embedding: Boolean? = null,
    val moderation: Boolean? = null,
    val function_calling: Boolean? = null,
    val vision: Boolean? = null
)
@Serializable
data class ModelsListResponse(
    val objectType: String? = null, // "list"
    val data: List<ModelDetail>
)