package dev.pranav.bryte.server.postgrest

import dev.pranav.bryte.model.card.Flashcard
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class FlashcardRepository(val postgrest: PostgrestQueryBuilder) {

    suspend fun getByDocumentId(documentId: String): List<Flashcard> {
        return postgrest.select {
            filter { eq("document_id", documentId) }
        }.decodeList()
    }

    suspend fun getByTopicId(topicId: String): List<Flashcard> {
        return postgrest.select {
            filter { eq("chunk_id", topicId) }
        }.decodeList()
    }

    suspend fun getById(id: String): Flashcard? {
        return postgrest.select {
            filter { eq("id", id) }
        }.decodeSingleOrNull()
    }

    suspend fun insert(flashcard: Flashcard): Flashcard? {
        return postgrest.insert(flashcard) {
            select()
        }.decodeSingleOrNull()
    }

    suspend fun insert(flashcards: List<Flashcard>): List<Flashcard> {
        return postgrest.insert(flashcards) {
            select()
        }.decodeList()
    }
}

class FlashcardsDelegate(private val supabase: SupabaseClient) : ReadOnlyProperty<Any?, FlashcardRepository> {

    private val table by lazy {
        supabase.postgrest.from("flashcards")
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): FlashcardRepository {
        return FlashcardRepository(table)
    }
}
