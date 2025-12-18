package dev.pranav.bryte.model.quiz

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String? = null,
    @SerialName("session_id") val sessionId: String,
    @SerialName("chunk_id") val chunkId: String,
    val page: Int,
    val type: String,
    val difficulty: String,
    val content: Content,
    val explanation: String,
    @SerialName("answered") val studentAnswer: String? = null,
    @SerialName("is_correct") val isCorrect: Boolean? = null,
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("time_spent_seconds") val timeSpent: Long? = null,
) {
}

@Serializable
sealed class Content {

    @Serializable
    @SerialName("SPOT_THE_ERROR")
    data class SpotTheError(
        val steps: List<Step>, val scenario: String, val errorStepIndex: Int
    ) : Content() {
        @Serializable
        data class Step(
            val isCorrect: Boolean,
            val stepNumber: Int,
            val description: String,
        )
    }

    @Serializable
    @SerialName("MULTIPLE_CHOICE")
    data class MultipleChoice(
        val question: String,
        val options: List<String>,
        val correctOptionIndex: Int,
    ) : Content()

    @Serializable
    @SerialName("MATCH")
    data class MatchTheFollowing(
        val leftItems: List<String>,
        val rightItems: List<String>,
        val correctMatches: List<Pair<Int, Int>>
    ) : Content()

    val type: String
        get() = when (this) {
            is MultipleChoice -> "MULTIPLE_CHOICE"
            is SpotTheError -> "SPOT_THE_ERROR"
            is MatchTheFollowing -> "MATCH"
        }
}
