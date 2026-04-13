package dev.pranav.bryte.model.stats

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FSRSReview(
    val questionId: String,
    val grade: Int, // 1 = Again, 2 = Hard, 3 = Good, 4 = Easy
    val timeSpentSeconds: Long
)

@Serializable
data class FSRSState(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("question_id") val questionId: String,
    @SerialName("topic_id") val topicId: String,
    val state: Int = 0, // 0 = New, 1 = Learning, 2 = Review, 3 = Relearning
    val difficulty: Double = 0.0,
    val stability: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    @SerialName("last_review") val lastReview: String? = null,
    @SerialName("next_review") val nextReview: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class TopicAnalytics(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("topic_id") val topicId: String,
    @SerialName("readiness_score") val readinessScore: Double = 0.0,
    @SerialName("total_reviews") val totalReviews: Int = 0,
    @SerialName("correct_first_try") val correctFirstTry: Int = 0,
    @SerialName("avg_time_spent") val avgTimeSpentSeconds: Double = 0.0,
    @SerialName("last_updated") val lastUpdated: String? = null
)

@Serializable
data class SessionAnalytics(
    val sessionId: String,
    val averageReadiness: Double,
    val totalTopicsLearned: Int,
    val completedReviews: Int,
    val topics: List<TopicAnalytics>,
    val accuracy: Double = 0.0,
    val totalAttempted: Int = 0,
    val overallPerformance: Double = 0.0,
    val consistency: Double = 0.0,
    val responseTime: Double = 0.0,
    val recommendations: List<String> = emptyList()
)

@Serializable
data class AnalyticsTimelineRow(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("session_id") val sessionId: String,
    val accuracy: Double = 0.0,
    @SerialName("overall_performance") val overallPerformance: Double = 0.0,
    @SerialName("created_at") val createdAt: String? = null
)