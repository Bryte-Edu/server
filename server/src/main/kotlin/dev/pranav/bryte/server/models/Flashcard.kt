package dev.pranav.bryte.server.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Flashcard(
  val id: String,
  @SerialName("document_id")
  val documentId: String,
  @SerialName("chunk_id")
  val chunkId: String,
  val front: String,
  val back: String,
  val page: Int,
  @SerialName("importance_level")
  val importance: Int?,
  val topic: String,
  @SerialName("created_at")
  val createdAt: String,
)
