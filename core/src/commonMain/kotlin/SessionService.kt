package dev.pranav

import dev.pranav.model.SessionDetails
import dev.pranav.model.quiz.Question
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Serializable
data class Data(val value: String)

@Rpc
interface SessionService {
    /**
     * Fetches the details of the current session.
     *
     * @return SessionDetails object containing session information.
     */
    suspend fun details(): SessionDetails

    /**
     * Retrieves the list of saved questions.
     *
     * @return List of Question objects that have been saved.
     */
    suspend fun savedQuestions(): List<Question>


    fun questions(): Flow<Question>
}
