package dev.pranav.bryte.server.services

import dev.pranav.bryte.FlashcardService
import dev.pranav.bryte.model.card.Flashcard
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.model.stats.AnalyticsTimelineRow
import dev.pranav.bryte.model.stats.FSRSReview
import dev.pranav.bryte.model.stats.FSRSState
import dev.pranav.bryte.model.stats.TopicAnalytics
import dev.pranav.bryte.server.ai.FlashcardGenerator
import dev.pranav.bryte.server.postgrest.analyticsTimeline
import dev.pranav.bryte.server.postgrest.fsrsStates
import dev.pranav.bryte.server.postgrest.topicAnalytics
import dev.pranav.bryte.server.util.SpacedRepetitionScheduler
import dev.pranav.bryte.server.util.ext.documentChunks
import dev.pranav.bryte.server.util.ext.flashcards
import dev.pranav.bryte.server.util.ext.supabase
import io.ktor.util.logging.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

internal val FLASHCARD_LOGGER = KtorSimpleLogger("dev.pranav.bryte.server.services.FlashcardService")

class FlashcardServiceImpl(val session: Session) : FlashcardService {

    private var generator: FlashcardGenerator
    private val flashcards by supabase.flashcards()

    init {
        val chunks by supabase.documentChunks()

        generator = FlashcardGenerator(
            session,
            chunks,
            flashcards
        )
    }

    override suspend fun flashcardsByTopic(topicId: String): List<Flashcard> {
        return flashcards.getByTopicId(topicId)
    }

    override fun flashcards(): Flow<Flashcard> = flow {
        val fsrsRepo by supabase.fsrsStates()

        val overdueStates = fsrsRepo.getOverdue(session.id, Clock.System.now().toString())
        if (overdueStates.isNotEmpty()) {
            FLASHCARD_LOGGER.info("Found ${overdueStates.size} overdue flashcards. Emitting first.")
            val overdueIds = overdueStates.map { it.questionId }
            val overdueFlashcards = flashcards.getByIds(overdueIds)
            for (f in overdueFlashcards) {
                emit(f)
            }
        } else {
            FLASHCARD_LOGGER.info("No overdue flashcards found.")
        }

        if (!generator.exhausted) {
            FLASHCARD_LOGGER.info("Generating new flashcards...")
            generator.generateFlashcards().collect { emit(it) }
        } else {
            FLASHCARD_LOGGER.info("Flashcard generator exhausted.")
        }
    }

    override suspend fun submitReview(review: FSRSReview): FSRSState {
        val fsrsRepo by supabase.fsrsStates()
        val topicAnalyticsRepo by supabase.topicAnalytics()

        val flashcardId = review.questionId
        val flashcard = flashcards.getById(flashcardId) ?: throw IllegalArgumentException("Flashcard not found")

        // Fetch existing state
        val currentState = fsrsRepo.getByQuestionId(flashcardId)

        // Calculate new state
        val nextState = SpacedRepetitionScheduler.calculateNextState(currentState, review.grade).copy(
            userId = session.userId,
            sessionId = session.id,
            questionId = flashcardId,
            topicId = flashcard.chunkId
        )
        FLASHCARD_LOGGER.info("Calculated new FSRS state for flashcard $flashcardId: stability=${nextState.stability}, difficulty=${nextState.difficulty}, scheduled for ${nextState.nextReview}")

        // Save state
        val savedState = fsrsRepo.upsert(nextState) ?: throw IllegalStateException("Failed to save FSRS state")

        // Recalculate Topic Analytics
        val allTopicStates = fsrsRepo.getByTopicId(flashcard.chunkId)
        val currentAnalytics = topicAnalyticsRepo.getByTopicId(flashcard.chunkId) ?: TopicAnalytics(
            userId = session.userId,
            sessionId = session.id,
            topicId = flashcard.chunkId
        )
        val updatedAnalytics = SpacedRepetitionScheduler.calculateReadiness(allTopicStates, currentAnalytics)
        topicAnalyticsRepo.upsert(updatedAnalytics)

        // Snapshot timeline
        val currentSessionAnalytics = calculateSessionAnalytics(session.id)
        val timelineRepo by supabase.analyticsTimeline()
        timelineRepo.insert(
            AnalyticsTimelineRow(
                userId = session.userId,
                sessionId = session.id,
                accuracy = currentSessionAnalytics.accuracy,
                overallPerformance = currentSessionAnalytics.overallPerformance,
                createdAt = Clock.System.now().toString()
            )
        )

        return savedState
    }
}
