package com.mistral.api.models.ocr
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
@Serializable
data class OcrRequest(
    val model: String,
    val document: DocumentInput,
    val pages: List<Int>? = null,
    @SerialName("include_image_base64")
    val includeImageBase64: Boolean? = null,
    @SerialName("image_limit")
    val imageLimit: Int? = null,
    @SerialName("image_min_size")
    val imageMinSize: Int? = null,
    @SerialName("bbox_annotation_format")
    val bboxAnnotationFormat: ResponseFormat? = null,
    @SerialName("document_annotation_format")
    val documentAnnotationFormat: ResponseFormat? = null
)
/**
 * The `document` field can be one of:
 * - FileChunk (uploaded file already referenced by file_id)
 * - DocumentURLChunk (remote document)
 * - ImageURLChunk (remote image)
 */
@Serializable
sealed class DocumentInput
@Serializable
@SerialName("file")
data class FileChunk(
    @SerialName("file_id") val fileId: String
) : DocumentInput()
@Serializable
@SerialName("document_url")
data class DocumentURLChunk(
    val document_url: String
) : DocumentInput()
@Serializable
@SerialName("image_url")
data class ImageURLChunk(
    val url: String
) : DocumentInput()
@Serializable
data class DocumentAnnotationSchema(
    val name: String,
    val schema: JsonElement
)
/** Response format definition (json_schema only is valid) */
@Serializable
data class ResponseFormat(
    val type: String = "json_schema",
    val json_schema: DocumentAnnotationSchema
)

@Serializable
data class OcrResponse(
    val pages: List<OcrPage>,
    val model: String,
    @SerialName("document_annotation")
    val documentAnnotation: String? = null,
    @SerialName("usage_info")
    val usageInfo: OcrUsageInfo
)
@Serializable
data class OcrPage(
    val index: Int,
    val markdown: String,
    val images: List<OcrImage>,
    val dimensions: OcrPageDimensions?
)
@Serializable
data class OcrImage(
    val id: String,
    @SerialName("top_left_x") val topLeftX: Int?,
    @SerialName("top_left_y") val topLeftY: Int?,
    @SerialName("bottom_right_x") val bottomRightX: Int?,
    @SerialName("bottom_right_y") val bottomRightY: Int?,
    @SerialName("image_base64") val imageBase64: String? = null,
    @SerialName("image_annotation") val imageAnnotation: String? = null,
)
@Serializable
data class OcrPageDimensions(
    val dpi: Int,
    val width: Int,
    val height: Int
)
@Serializable
data class OcrUsageInfo(
    @SerialName("pages_processed")
    val pagesProcessed: Int,
    @SerialName("doc_size_bytes")
    val docSizeBytes: Long? = null
)