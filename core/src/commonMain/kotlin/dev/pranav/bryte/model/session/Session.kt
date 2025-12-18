package dev.pranav.bryte.model.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Session(
    val id: String = "",
    @SerialName("document_id")
    val documentId: String,
    @SerialName("user_id")
    val userId: String,
    val difficulty: String,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
    @SerialName("total_questions")
    val totalQuestions: Int = 0,
    @SerialName("correct_count")
    val correctCount: Int = 0,
    @SerialName("incorrect_count")
    val incorrectCount: Int = 0,
    val stats: Map<String, String> = emptyMap()
)