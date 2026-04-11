package dev.pranav.bryte.server.document

import dev.pranav.bryte.model.DocumentType
import dev.pranav.bryte.model.session.Image

/**
 * Data class representing a parsed document with its details and topics.
 */
data class ParsedDocument(
    val id: String,
    val title: String,
    val type: DocumentType,
    val metadata: Map<String, Any> = mapOf(),
    val topics: List<Topic> = emptyList()
)

/**
 * Data class representing a topic within a document.
 */
data class Topic(
    val header: String,
    val content: String,
    val images: List<Image>,
    val pages: List<Int>
)