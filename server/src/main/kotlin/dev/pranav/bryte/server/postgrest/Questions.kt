package dev.pranav.bryte.server.postgrest

import dev.pranav.bryte.model.quiz.Question
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class QuestionRepository(val postgrest: PostgrestQueryBuilder) {

    suspend fun getById(id: String): Question {
        return postgrest.select {
            filter { eq("id", id) }
        }.decodeSingle()
    }

    suspend fun getBySession(sessionId: String): List<Question> {
        return postgrest.select {
            filter { eq("session_id", sessionId) }
        }.decodeList()
    }

    suspend fun getByChunk(chunkId: String): List<Question> {
        return postgrest.select {
            filter { eq("chunk_id", chunkId) }
        }.decodeList()
    }

    suspend fun insert(data: Question): Question? {
        return postgrest.insert(data) {
            select()
        }.decodeSingleOrNull()
    }

    suspend fun insert(data: List<Question>): List<Question> {
        return postgrest.insert(data) {
            select()
        }.decodeList()
    }
}


class QuestionsDelegate(private val supabase: SupabaseClient) : ReadOnlyProperty<Any?, QuestionRepository> {
    private val table by lazy {
        supabase.postgrest.from("questions")
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): QuestionRepository {
        return QuestionRepository(table)
    }
}
