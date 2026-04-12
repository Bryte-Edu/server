package dev.pranav.bryte.server.services

import dev.pranav.bryte.SessionService
import dev.pranav.bryte.model.SessionDetails
import dev.pranav.bryte.model.quiz.Question
import dev.pranav.bryte.model.session.Session
import dev.pranav.bryte.model.stats.TopicAnalytics
import dev.pranav.bryte.server.ai.QuestionGenerator
import dev.pranav.bryte.server.postgrest.topicAnalytics
import dev.pranav.bryte.server.util.ext.documentChunks
import dev.pranav.bryte.server.util.ext.questions
import dev.pranav.bryte.server.util.ext.supabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


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
    System.err.println("Session service for session ${session.id}")

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
    if (!generator.exhausted) {
      generator.generateQuestions().collect { emit(it) }
    }
  }

  override suspend fun getTopicAnalytics(topicId: String): TopicAnalytics? {
    val topicAnalyticsRepo by supabase.topicAnalytics()
    return topicAnalyticsRepo.getByTopicId(topicId)
  }

  override suspend fun getSessionAnalytics(): dev.pranav.bryte.model.stats.SessionAnalytics {
    val topicAnalyticsRepo by supabase.topicAnalytics()
    val topics = topicAnalyticsRepo.getBySessionId(session.id)

    val totalTopicsLearned = topics.size
    val averageReadiness = if (totalTopicsLearned > 0) {
      topics.map { it.readinessScore }.average()
    } else {
      0.0
    }
    val completedReviews = topics.sumOf { it.totalReviews }

    return dev.pranav.bryte.model.stats.SessionAnalytics(
      sessionId = session.id,
      averageReadiness = averageReadiness,
      totalTopicsLearned = totalTopicsLearned,
      completedReviews = completedReviews,
      topics = topics
    )
  }
}
