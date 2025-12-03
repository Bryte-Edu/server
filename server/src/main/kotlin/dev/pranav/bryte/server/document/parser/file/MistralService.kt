package dev.pranav.bryte.server.document.parser.file

import com.mistral.api.MistralClient
import com.mistral.api.models.embeddings.EmbeddingsRequest
import com.mistral.api.models.files.FileItem
import com.mistral.api.models.ocr.DocumentURLChunk
import com.mistral.api.models.ocr.FileChunk
import com.mistral.api.models.ocr.OcrRequest
import com.mistral.api.models.ocr.OcrResponse
import dev.pranav.bryte.server.MISTRAL_API_KEY
import dev.pranav.bryte.server.document.Topic
import java.io.File
import kotlin.collections.map

/**
 * Centralized helper for Mistral AI operations used by the app.
 * Wraps common flows like OCR and embeddings behind simple suspend functions.
 */
object MistralService {
    private val client: MistralClient by lazy { MistralClient(MISTRAL_API_KEY) }

    /**
     * Upload a file to Mistral for OCR processing. Returns the uploaded file item.
     */
    suspend fun uploadForOcr(file: File, purpose: String = "ocr"): FileItem = client.files.upload(file, purpose)

    /**
     * Perform OCR on a previously uploaded file using its fileId.
     */
    suspend fun ocrByFileId(
        fileId: String,
        model: String = "mistral-ocr-latest",
        includeImageBase64: Boolean? = null,
        pages: List<Int>? = null,
        imageLimit: Int? = null,
        imageMinSize: Int? = null
    ): OcrResponse {
        val request = OcrRequest(
            model = model,
            document = FileChunk(fileId = fileId),
            pages = pages,
            includeImageBase64 = includeImageBase64,
            imageLimit = imageLimit,
            imageMinSize = imageMinSize,
            bboxAnnotationFormat = null,
            documentAnnotationFormat = null
        )
        return client.ocr.recognize(request)
    }

    suspend fun ocrByFileUrl(
        fileUrl: String,
        model: String = "mistral-ocr-latest",
        includeImageBase64: Boolean? = null,
        pages: List<Int>? = null,
        imageLimit: Int? = null,
        imageMinSize: Int? = null
    ): OcrResponse {
        val request = OcrRequest(
            model = model,
            document = DocumentURLChunk(document_url = fileUrl),
            pages = pages,
            includeImageBase64 = includeImageBase64,
            imageLimit = imageLimit,
            imageMinSize = imageMinSize,
            bboxAnnotationFormat = null,
            documentAnnotationFormat = null
        )
        return client.ocr.recognize(request)
    }

    /**
     * Get a temporary signed URL to download a file from Mistral Files API.
     */
    suspend fun getFileSignedUrl(fileId: String, expiryHours: Int? = null): String =
        client.files.getSignedUrl(fileId, expiryHours)
}