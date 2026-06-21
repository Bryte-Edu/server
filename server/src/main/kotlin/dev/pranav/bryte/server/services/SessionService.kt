package dev.pranav.bryte.server.services

import dev.pranav.bryte.SessionService
import dev.pranav.bryte.model.SessionDetails
import dev.pranav.bryte.model.quiz.Question
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.model.stats.AnalyticsTimelineRow
import dev.pranav.bryte.model.stats.FSRSReview
import dev.pranav.bryte.model.stats.FSRSState
import dev.pranav.bryte.model.stats.TopicAnalytics
import dev.pranav.bryte.server.ai.QuestionGenerator
import dev.pranav.bryte.server.postgrest.analyticsTimeline
import dev.pranav.bryte.server.postgrest.fsrsStates
import dev.pranav.bryte.server.postgrest.topicAnalytics
import dev.pranav.bryte.server.util.SpacedRepetitionScheduler
import dev.pranav.bryte.server.util.ext.documentChunks
import dev.pranav.bryte.server.util.ext.questions
import dev.pranav.bryte.server.util.ext.supabase
import io.ktor.util.logging.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

internal val SESSION_LOGGER = KtorSimpleLogger("dev.pranav.bryte.server.services.SessionService")

class SessionServiceImpl(val session: Session) : SessionService {

    private var generator: QuestionGenerator

    init {
        val chunks by supabase.documentChunks()
        val questions by supabase.questions()
        val topicAnalyticsRepo by supabase.topicAnalytics()

        generator = QuestionGenerator(
            session,
            chunks,
            questions,
            topicAnalyticsRepo
        )
    }

    override suspend fun details(): SessionDetails {

        return SessionDetails(
            sessionId = session.id,
            userId = session.userId,
            documentId = session.documentId,
            createdAt = session.createdAt,
        )
    }

    override suspend fun savedQuestions(): List<Question> {
        val questions by supabase.questions()

        return questions.getBySession(session.id)
    }

    override fun questions(): Flow<Question> = flow {
        val fsrsRepo by supabase.fsrsStates()
        val questionsRepo by supabase.questions()

        val overdueStates = fsrsRepo.getOverdue(session.id, Clock.System.now().toString())
        if (overdueStates.isNotEmpty()) {
            SESSION_LOGGER.info("Found ${overdueStates.size} overdue FSRS questions for session ${session.id}. Emitting first.")
            val overdueIds = overdueStates.map { it.questionId }
            val overdueQuestions = questionsRepo.getByIds(overdueIds)
            for (q in overdueQuestions) {
                emit(q)
            }
        } else {
            SESSION_LOGGER.info("No overdue questions found for session ${session.id}.")
        }

        if (!generator.exhausted) {
            SESSION_LOGGER.info("Falling back to generator for new questions.")
          generator.generateQuestions().collect { emit(it) }
        } else {
            SESSION_LOGGER.info("Generator exhausted. No more questions.")
        }
    }

    override suspend fun getTopicAnalytics(topicId: String): TopicAnalytics? {
        val topicAnalyticsRepo by supabase.topicAnalytics()
        return topicAnalyticsRepo.getByTopicId(topicId)
    }

    override suspend fun getSessionAnalytics(): dev.pranav.bryte.model.stats.SessionAnalytics {
        return calculateSessionAnalytics(session.id)
    }

    override suspend fun submitReview(review: FSRSReview): FSRSState {
        val questionsRepo by supabase.questions()
        val fsrsRepo by supabase.fsrsStates()
        val topicAnalyticsRepo by supabase.topicAnalytics()

        val questionId = review.questionId
        val question = questionsRepo.getById(questionId) ?: throw IllegalArgumentException("Question not found")

        // Fetch existing state
        val currentState = fsrsRepo.getByQuestionId(questionId)

        // Calculate new state
        val nextState = SpacedRepetitionScheduler.calculateNextState(currentState, review.grade).copy(
            userId = session.userId,
            sessionId = session.id,
            questionId = questionId,
            topicId = question.chunkId
        )
        SESSION_LOGGER.info("Calculated new FSRS state for question $questionId: stability=${nextState.stability}, difficulty=${nextState.difficulty}, scheduled for ${nextState.nextReview}")

        // Save state
        val savedState = fsrsRepo.upsert(nextState) ?: throw IllegalStateException("Failed to save FSRS state")

        // Recalculate Topic Analytics
        val allTopicStates = fsrsRepo.getByTopicId(question.chunkId)
        val currentAnalytics = topicAnalyticsRepo.getByTopicId(question.chunkId) ?: TopicAnalytics(
            userId = session.userId,
            sessionId = session.id,
            topicId = question.chunkId
        )
        val updatedAnalytics = SpacedRepetitionScheduler.calculateReadiness(allTopicStates, currentAnalytics)
        topicAnalyticsRepo.upsert(updatedAnalytics)

        // Snapshot timeline
        val currentSessionAnalytics = getSessionAnalytics()
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

    override suspend fun getAnalyticsTimeline(): List<AnalyticsTimelineRow> {
        val timelineRepo by supabase.analyticsTimeline()
        return timelineRepo.getBySession(session.id)
    }
}

suspend fun calculateSessionAnalytics(sessionId: String): dev.pranav.bryte.model.stats.SessionAnalytics {
    val topicAnalyticsRepo by supabase.topicAnalytics()
    val topics = topicAnalyticsRepo.getBySessionId(sessionId)

    val fsrsRepo by supabase.fsrsStates()
    val fsrsStates = fsrsRepo.getBySessionId(sessionId).filter { it.reps > 0 }

    val totalTopicsLearned = topics.size
    val averageReadiness = if (totalTopicsLearned > 0) {
        topics.map { it.readinessScore }.average()
    } else {
        0.0
    }
    val completedReviews = fsrsStates.sumOf { it.reps }

    // Average time spent
    val responseTime = if (topics.isNotEmpty()) topics.map { it.avgTimeSpentSeconds }.average() else 0.0

    // Compute accuracy based on current state (Review state = 2 means currently known, 1/3 means struggling)
    val totalAttempted = fsrsStates.size
    val currentlyKnown = fsrsStates.count { it.state == 2 }
    val accuracy = if (totalAttempted > 0) (currentlyKnown.toDouble() / totalAttempted) * 100.0 else 0.0

    // Overall performance: a mix of average readiness and accuracy, giving more weight to recent actuals via FSRS stability scaling
    val avgStability = if (fsrsStates.isNotEmpty()) fsrsStates.map { it.stability }.average() else 0.0
    val overallPerformance =
        if (totalAttempted > 0) (averageReadiness + accuracy + (avgStability * 10).coerceAtMost(100.0)) / 3.0 else 0.0

    // Consistency: inversely related to lapses vs reps
    val totalLapses = fsrsStates.sumOf { it.lapses }
    val consistency = if (completedReviews > 0) {
        val lapseRate = totalLapses.toDouble() / completedReviews
        ((1.0 - lapseRate) * 100.0).coerceIn(0.0, 100.0)
    } else 0.0

    // Recommendations dynamically computed based on FSRS state
    val recommendations = mutableListOf<String>()
    val strugglingTopics = fsrsStates.filter { it.state == 1 || it.state == 3 || it.difficulty > 7.0 }
        .groupBy { it.topicId }
        .mapValues { it.value.size }
        .entries.sortedByDescending { it.value }
        .take(2)

    if (strugglingTopics.isNotEmpty()) {
        recommendations.add("Consider reviewing the top ${strugglingTopics.size} topics where you recently had lapses or high difficulty.")
    }
    if (accuracy < 70 && totalAttempted > 5) {
        recommendations.add("Your current accuracy is dipping. Try shorter study spaces within this session.")
    } else if (accuracy > 90) {
        recommendations.add("Great retention! You are ready to space out these topics further or move to new material.")
    }

    return dev.pranav.bryte.model.stats.SessionAnalytics(
        sessionId = sessionId,
        averageReadiness = averageReadiness,
        totalTopicsLearned = totalTopicsLearned,
        completedReviews = completedReviews,
        topics = topics,
        accuracy = accuracy,
        totalAttempted = totalAttempted,
        overallPerformance = overallPerformance,
        consistency = consistency,
        responseTime = responseTime,
        recommendations = recommendations
    )
}