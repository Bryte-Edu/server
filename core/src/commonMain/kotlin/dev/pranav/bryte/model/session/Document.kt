package dev.pranav.bryte.model.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DocumentItem(
    val id: String = "",
    @SerialName("user_id")
    val userId: String,
    val title: String,
    @SerialName("created_at")
    val createdAt: String = "",
    val metadata: Map<String, Int> = emptyMap(),
    val type: String = "pdf",
    val source: String
)

@Serializable
data class DocumentMetadata(
    val language: String? = null,
    val authors: List<String>? = null,
    val title: String?,
    @SerialName("num_pages")
    val numPages: Int? = null
)