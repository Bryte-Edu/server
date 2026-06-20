package dev.pranav.bryte.server.util

import dev.pranav.bryte.model.stats.FSRSState
import dev.pranav.bryte.model.stats.TopicAnalytics
import io.ktor.util.logging.*
import kotlin.math.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal val FSRS_LOGGER = KtorSimpleLogger("dev.pranav.bryte.server.util.SpacedRepetitionScheduler")

object SpacedRepetitionScheduler {

    // FSRS v4 default weights natively
    private val w = doubleArrayOf(
        0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01,
        1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26,
        0.29, 2.61
    )

    enum class State(val value: Int) {
        New(0), Learning(1), Review(2), Relearning(3)
    }

    fun calculateNextState(currentState: FSRSState?, grade: Int): FSRSState {
        FSRS_LOGGER.info("FSRS calculateNextState: grade=$grade, isNew=${currentState == null}, currentStability=${currentState?.stability ?: "N/A"}, currentDifficulty=${currentState?.difficulty ?: "N/A"}")
        val now = Clock.System.now()
        val s = currentState ?: FSRSState(
            userId = "",
            sessionId = "",
            questionId = "",
            topicId = "",
            state = State.New.value,
            difficulty = 0.0, stability = 0.0, reps = 0, lapses = 0,
            lastReview = now.toString(), nextReview = now.toString()
        )

        var newDifficulty: Double
        var newStability: Double
        val state = s.state
        val isNew = state == 0
        var newLapses = s.lapses

        if (isNew) {
            newDifficulty = max(1.0, min(10.0, w[4] - w[5] * (grade - 3)))
            newStability = max(w[grade - 1], 0.1)
        } else {
            // Calculate Retrievability (elapsed days since last review)
            val lastReviewInstant = Instant.parse(s.lastReview!!)
            val elapsedDays = (now - lastReviewInstant).inWholeDays.toDouble()
            val retrievability = (1 + elapsedDays / (9 * s.stability)).pow(-1)

            newDifficulty = s.difficulty - w[6] * (grade - 3)
            newDifficulty = min(max(newDifficulty, 1.0), 10.0)
            // Mean reversion
            newDifficulty = w[7] * (w[4] - w[5]) + (1 - w[7]) * newDifficulty

            if (grade == 1) { // Lapse
                newStability =
                    w[11] * newDifficulty.pow(-w[12]) * (s.stability + 1).pow(-w[13]) * exp(-w[14] * (1 - retrievability))
                newStability = max(0.1, min(newStability, s.stability))
                newLapses++
            } else { // Review
                val modifier = when (grade) {
                    2 -> w[15]
                    3 -> 1.0
                    4 -> w[16]
                    else -> 1.0
                }
                newStability =
                    s.stability * (1 + exp(w[8]) * (11 - newDifficulty) * s.stability.pow(-w[9]) * (exp((1 - retrievability) * w[10]) - 1) * modifier)
            }
        }

        val newStateCode = if (grade == 1) 3 else 2 // Relearning vs Review
        val newReps = s.reps + 1

        val intervalDays = max(1, (newStability * 9 * (1.0 / 0.9 - 1)).roundToInt())
        val nextReviewTime = now.plus(intervalDays.days)
        FSRS_LOGGER.info("FSRS result: newState=${if (grade == 1) "Relearning" else "Review"}, stability=$newStability, difficulty=$newDifficulty, interval=${intervalDays}d, lapses=$newLapses")

        return FSRSState(
            id = s.id,
            userId = s.userId,
            sessionId = s.sessionId,
            questionId = s.questionId,
            topicId = s.topicId,
            state = newStateCode,
            difficulty = newDifficulty,
            stability = newStability,
            reps = newReps,
            lapses = newLapses,
            lastReview = now.toString(),
            nextReview = nextReviewTime.toString()
        )
    }

    fun calculateReadiness(topicStats: List<FSRSState>, analytics: TopicAnalytics): TopicAnalytics {
        if (topicStats.isEmpty()) return analytics
        val avgStability = topicStats.map { it.stability }.average()
        val avgDifficulty = topicStats.map { it.difficulty }.average()

        // Smart blending logic
        var readiness = (avgStability * 10) - (avgDifficulty * 0.5)
        val firstTryBonus =
            if (analytics.totalReviews > 0) (analytics.correctFirstTry.toDouble() / analytics.totalReviews) * 5.0 else 0.0

        readiness = max(0.0, min(100.0, readiness + firstTryBonus))

        return analytics.copy(
            readinessScore = readiness,
            lastUpdated = Clock.System.now().toString()
        )
    }
}
