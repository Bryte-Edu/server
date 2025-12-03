package dev.pranav.bryte.server.postgrest

import dev.pranav.bryte.server.models.DocumentChunk
import dev.pranav.bryte.server.models.Session
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class SessionRepository(val postgrest: PostgrestQueryBuilder) {

    suspend fun getById(sessionId: String): Session? {
        return postgrest.select {
            filter { eq("id", sessionId) }
        }.decodeSingleOrNull()
    }

    suspend fun getByDocumentId(documentId: String): List<Session> {
        return postgrest.select {
            filter { eq("document_id", documentId) }
        }.decodeList()
    }

    suspend fun insert(session: Session): Session? {
        return postgrest.insert(session) {
            select()
        }.decodeSingleOrNull()
    }

    suspend fun insert(sessions: List<Session>): List<Session> {
        return postgrest.insert(sessions) {
            select()
        }.decodeList()
    }
}


class SessionsDelegate(private val supabase: SupabaseClient) : ReadOnlyProperty<Any?, SessionRepository> {
    private val table by lazy {
        supabase.postgrest.from("quiz_sessions")
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): SessionRepository {
        return SessionRepository(table)
    }
}
