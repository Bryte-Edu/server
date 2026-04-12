package dev.pranav.bryte

import dev.pranav.bryte.model.card.Flashcard
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc

/**
 * Defines the remote procedure call (RPC) service for managing and retrieving flashcard content.
 * This service provides client applications with access to generated quiz content,
 * where each item is conceptually used as a flashcard.
 */
@Rpc
interface FlashcardService {

    /**
     * Retrieves a list of generated flashcards related to a specific document topic.
     *
     * @param topicId The unique identifier for the document chunk or topic.
     * @return A [List] of [Flashcard] objects, representing the flashcards for the topic.
     */
    suspend fun flashcardsByTopic(topicId: String): List<Flashcard>

    /**
     * Provides a real-time, sequential stream of generated flashcards.
     * This method is used to consume new flashcards as they are dynamically generated
     *
     * by the backend system (e.g., an AI agent).
     * @return A [Flow] of [Flashcard] objects, providing flashcards one by one.
     */
    fun flashcards(): Flow<Flashcard>
}
