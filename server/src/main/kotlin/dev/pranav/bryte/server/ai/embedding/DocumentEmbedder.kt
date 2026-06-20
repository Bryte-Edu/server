package dev.pranav.bryte.server.ai.embedding

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.vector.embedder.DocumentEmbedder
import dev.pranav.bryte.model.session.DocumentChunk
import kotlin.uuid.Uuid

open class TextDocumentEmbedder(
    private val embedder: Embedder,
    private val documentTopics: Iterable<DocumentChunk>
) : DocumentEmbedder<String> {

    /**
     * Embeds the given text into a vector representation.
     *
     * @param text The text to embed.
     * @return A vector representation of the provided text.
     */
    override suspend fun embed(text: String): Vector {
        if (text.isBlank()) return Vector(listOf())

        val id = text

        runCatching {
            Uuid.parse(id)
        }.getOrElse {
            return@getOrElse try {
                embedder.embed(text)
            } catch (e: Exception) {
                Vector(listOf())
            }
        }

        val chunk = documentTopics.find { it.id == id }?.let {
            it.embedding?.let { return Vector(it) }
            it
        }
        if (chunk == null) {
            return embedder.embed(text)
        }

        val embedding = try {
            embedder.embed(chunk.content)
        } catch (e: Exception) {
            Vector(listOf())
        }

        chunk.embedding = embedding.values

        return embedding
    }

    /**
     * Calculates the difference between two embeddings using the underlying embedder.
     * Lower values indicate more similarity between the embeddings.
     *
     * @param embedding1 The first embedding to compare.
     * @param embedding2 The second embedding to compare.
     * @return A double value representing the difference between the two embeddings.
     */
    override fun diff(
        embedding1: Vector, embedding2: Vector
    ): Double = embedder.diff(embedding1, embedding2)
}