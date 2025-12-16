package dev.pranav.bryte.server.services

import dev.pranav.SessionService
import dev.pranav.bryte.server.ai.QuestionGenerator
import dev.pranav.bryte.server.models.Session
import dev.pranav.bryte.server.util.ext.documentChunks
import dev.pranav.bryte.server.util.ext.questions
import dev.pranav.bryte.server.util.ext.supabase
import dev.pranav.model.SessionDetails
import dev.pranav.model.quiz.Question
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking


class SessionServiceImpl(val session: Session) : SessionService {

  private var generator: QuestionGenerator

  init {
    val chunks by supabase.documentChunks()
    generator = QuestionGenerator(
      session,
      chunks
    )
  }

  override suspend fun details(): SessionDetails {

    return SessionDetails(
      sessionId = session.id,
      userId = session.userId,
      documentId = session.documentId,
      createdAt = session.createdAt,
      updatedAt = session.updatedAt
    )
  }

  override suspend fun savedQuestions(): List<Question> {
    val questions by supabase.questions()

    return questions.getBySession(session.id)
  }

  override fun questions(): Flow<Question> = runBlocking {
    generator.generateQuestions()
  }
}
