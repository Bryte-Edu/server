package dev.pranav.bryte.model.session

import dev.pranav.bryte.model.serializer.VectorSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DocumentChunk(
    val id: String? = null,
    @SerialName("document_id")
    val documentId: String,
    @SerialName("page_number")
    val pageNumber: List<Int>,
    val header: String,
    val content: String,
    val images: List<Image>,
    @Serializable(with = VectorSerializer::class)
    var embedding: List<Double>? = null
)

@Serializable
data class Image(
    val id: String,
    @SerialName("image_base64")
    val base64: String? = null,
    val annotations: List<Annotation> = listOf()
)