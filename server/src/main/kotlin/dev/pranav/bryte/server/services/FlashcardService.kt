package dev.pranav.bryte.server.services

import dev.pranav.bryte.FlashcardService
import dev.pranav.bryte.model.card.Flashcard
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.model.stats.FSRSReview
import dev.pranav.bryte.model.stats.FSRSState
import dev.pranav.bryte.model.stats.TopicAnalytics
import dev.pranav.bryte.server.ai.FlashcardGenerator
import dev.pranav.bryte.server.postgrest.fsrsStates
import dev.pranav.bryte.server.postgrest.topicAnalytics
import dev.pranav.bryte.server.util.SpacedRepetitionScheduler
import dev.pranav.bryte.server.util.ext.documentChunks
import dev.pranav.bryte.server.util.ext.flashcards
import dev.pranav.bryte.server.util.ext.supabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


class FlashcardServiceImpl(val session: Session) : FlashcardService {

    private var generator: FlashcardGenerator
    private val flashcards by supabase.flashcards()

    init {
        val chunks by supabase.documentChunks()
        val flashcards by supabase.flashcards()
        generator = FlashcardGenerator(
            session,
            chunks,
            flashcards
        )
    }

    override suspend fun flashcardsByTopic(topicId: String): List<Flashcard> {
        return flashcards.getByTopicId(topicId).map { it.copy() }
    }

    override fun flashcards(): Flow<Flashcard> = flow {
        if (!generator.exhausted) {
            generator.generateFlashcards().collect { emit(it) }
        }
    }

    override suspend fun submitReview(review: FSRSReview): FSRSState {
        val fsrsStates by supabase.fsrsStates()
        val topicAnalyticsRepo by supabase.topicAnalytics()

        val card = flashcards.getById(review.cardId) ?: throw Exception("Card not found")
        val topicId = card.chunkId

        // Get current FSRS state
        val currentState = fsrsStates.getByCardId(review.cardId)

        // Calculate next state
        val dummyState = currentState ?: FSRSState(
            userId = session.userId,
            sessionId = session.id,
            cardId = review.cardId,
            topicId = topicId
        )

        val nextState = SpacedRepetitionScheduler.calculateNextState(dummyState, review.grade)
        val upsertedState = fsrsStates.upsert(nextState) ?: throw Exception("Failed to upsert FSRS state")

        // Topic Analytics tracking
        val currentAnalytics = topicAnalyticsRepo.getByTopicId(topicId) ?: TopicAnalytics(
            userId = session.userId,
            sessionId = session.id,
            topicId = topicId
        )

        val isFirstTry = currentAnalytics.totalReviews == 0 && review.grade >= 3 // "Good" or "Easy"
        val newAnalytics = currentAnalytics.copy(
            totalReviews = currentAnalytics.totalReviews + 1,
            correctFirstTry = if (isFirstTry) currentAnalytics.correctFirstTry + 1 else currentAnalytics.correctFirstTry,
            avgTimeSpentSeconds = ((currentAnalytics.avgTimeSpentSeconds * currentAnalytics.totalReviews) + review.timeSpentSeconds) / (currentAnalytics.totalReviews + 1)
        )

        // Blended score
        val allTopicStates = fsrsStates.getByTopicId(topicId)
        val finalAnalytics = SpacedRepetitionScheduler.calculateReadiness(allTopicStates + upsertedState, newAnalytics)
        topicAnalyticsRepo.upsert(finalAnalytics)

        return upsertedState
    }
}
