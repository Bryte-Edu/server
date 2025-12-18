package dev.pranav.bryte.model.card

import dev.pranav.bryte.model.common.ImportanceLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Flashcard(
    val id: String? = null,
    @SerialName("document_id")
    val documentId: String,
    @SerialName("chunk_id")
    val chunkId: String,
    val page: Int,
    val front: String,
    val back: String,
    val topic: String,
    @SerialName("importance_level")
    val importance: ImportanceLevel? = ImportanceLevel.MEDIUM,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Suppress("PropertyName")
@Serializable
data class FlashcardSet(
    val document_id: String,
    val flashcards: List<Flashcard>,
    val total_count: Int
)