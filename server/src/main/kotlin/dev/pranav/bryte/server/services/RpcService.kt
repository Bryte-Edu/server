package dev.pranav.bryte.server.services

import dev.pranav.SessionService
import dev.pranav.bryte.server.models.Session
import dev.pranav.bryte.server.util.ext.questions
import dev.pranav.bryte.server.util.ext.sessions
import dev.pranav.bryte.server.util.ext.supabase
import dev.pranav.model.SessionDetails
import dev.pranav.model.quiz.Question
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.rpc.annotations.Rpc


class SessionServiceImpl(val session: Session) : SessionService {
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

  override fun questions(): Flow<Question> {
    // TODO: Implement streaming questions
    return flow {
      // Stream questions as they are generated
    }
  }
}
