package dev.pranav.bryte.server.postgrest

import dev.pranav.bryte.model.session.DocumentChunk
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class DocumentChunkRepository(val postgrest: PostgrestQueryBuilder) {

    suspend fun getById(chunkId: String): DocumentChunk {
        return postgrest.select {
            filter { eq("id", chunkId) }
        }.decodeSingle()
    }

    suspend fun getByDocumentId(documentId: String): List<DocumentChunk> {
        return postgrest.select {
            filter { eq("document_id", documentId) }
        }.decodeList()
    }

    suspend fun insert(chunk: DocumentChunk): DocumentChunk? {
        return postgrest.insert(chunk) {
            select()
        }.decodeSingleOrNull()
    }

    suspend fun insert(chunks: List<DocumentChunk>): List<DocumentChunk> {
        return postgrest.insert(chunks) {
            select()
        }.decodeList()
    }

    suspend fun upsert(chunk: DocumentChunk): DocumentChunk? {
        return postgrest.upsert(chunk) {
            select()
        }.decodeSingleOrNull()
    }
}


class DocumentChunksDelegate(private val supabase: SupabaseClient) : ReadOnlyProperty<Any?, DocumentChunkRepository> {
    private val table by lazy {
        supabase.postgrest.from("document_chunks")
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): DocumentChunkRepository {
        return DocumentChunkRepository(table)
    }
}
