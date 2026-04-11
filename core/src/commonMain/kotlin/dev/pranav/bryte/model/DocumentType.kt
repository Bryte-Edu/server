package dev.pranav.bryte.model

import kotlinx.serialization.Serializable

/**
 * Enum class representing different types of documents.
 */
@Serializable
enum class DocumentType {
    PDF,
    PPTX,
    DOCX,
    YOUTUBE,
    EPUB,
    WEBPAGE
}
