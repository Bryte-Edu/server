package dev.pranav.bryte.server.ai.embedding

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.vector.DocumentEmbedder
import dev.pranav.bryte.server.models.DocumentChunk
import dev.pranav.bryte.server.postgrest.DocumentChunkRepository
import dev.pranav.bryte.server.util.ext.supabase
import io.github.jan.supabase.postgrest.postgrest
import java.nio.file.Path

class TextDocumentProvider : DocumentProvider<Path, String> {
    override suspend fun document(path: Path): String = path.toString()
    override suspend fun text(document: String): CharSequence = convertLineSeparators(document)

    private fun convertLineSeparators(text: String): String {
        var buffer: StringBuilder? = null
        var intactLength = 0
        val newSeparatorIsSlashN = "\n" == "\n"
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') {
                if (!newSeparatorIsSlashN) {
                    if (buffer == null) {
                        buffer = StringBuilder(text.length)
                        buffer.append(text, 0, intactLength)
                    }
                    buffer.append("\n")
                    shiftOffsets(null, buffer.length, 1, "\n".length)
                } else if (buffer == null) {
                    intactLength++
                } else {
                    buffer.append('\n')
                }
            } else if (c == '\r') {
                val followedByLineFeed = i < text.length - 1 && text[i + 1] == '\n'
                if (!followedByLineFeed && false) {
                    if (buffer == null) {
                        intactLength++
                    } else {
                        buffer.append('\r')
                    }
                    i++
                    continue
                }
                if (buffer == null) {
                    buffer = StringBuilder(text.length)
                    buffer.append(text, 0, intactLength)
                }
                buffer.append("\n")
                if (followedByLineFeed) {
                    i++
                    shiftOffsets(null, buffer.length, 2, "\n".length)
                } else {
                    shiftOffsets(null, buffer.length, 1, "\n".length)
                }
            } else if (buffer == null) {
                intactLength++
            } else {
                buffer.append(c)
            }
            i++
        }
        return (buffer ?: text).toString()
    }

    private fun shiftOffsets(offsets: IntArray?, changeOffset: Int, oldLength: Int, newLength: Int) {
        if (offsets == null) return
        val shift = newLength - oldLength
        if (shift == 0) return
        for (i in offsets.indices) {
            val offset = offsets[i]
            if (offset >= changeOffset + oldLength) {
                offsets[i] += shift
            }
        }
    }
}

open class TextDocumentEmbedder(
    private val embedder: Embedder,
    private val documentChunks: DocumentChunkRepository
) : DocumentEmbedder<String> {

    /**
     * Embeds the given text into a vector representation.
     *
     * @param text The text to embed.
     * @return A vector representation of the provided text.
     */
    override suspend fun embed(text: String): Vector {
        val id = text

        val chunk = documentChunks.getById(id).let {
            it.embedding?.let { return Vector(it) }
            it
        }

        val embedding = embedder.embed(text.removePrefix(id))

        chunk.embedding = embedding.values

        documentChunks.upsert(chunk)

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