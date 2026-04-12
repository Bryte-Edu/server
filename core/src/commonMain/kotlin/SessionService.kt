package dev.pranav.bryte

import dev.pranav.bryte.model.SessionDetails
import dev.pranav.bryte.model.quiz.Question
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc

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

    /**
     * Retrieves the topic analytics such as FSRS readiness score for a specific topic
     */
    suspend fun getTopicAnalytics(topicId: String): dev.pranav.bryte.model.stats.TopicAnalytics?

    /**
     * Retrieves the full session analytics rolling up all learned topics
     */
    suspend fun getSessionAnalytics(): dev.pranav.bryte.model.stats.SessionAnalytics

    /**
     * Submits an FSRS review for a question.
     * Updates the spaced repetition algorithm state and recalculates the
     * topic readiness score dynamically for the session.
     *
     * @param review The [dev.pranav.bryte.model.stats.FSRSReview] review containing the question ID and user's grade.
     * @return The updated [dev.pranav.bryte.model.stats.FSRSState].
     */
    suspend fun submitReview(review: dev.pranav.bryte.model.stats.FSRSReview): dev.pranav.bryte.model.stats.FSRSState

}
