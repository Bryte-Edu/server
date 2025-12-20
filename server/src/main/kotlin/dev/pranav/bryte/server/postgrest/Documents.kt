package dev.pranav.bryte.server.postgrest

import dev.pranav.bryte.model.session.DocumentItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestQueryBuilder
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class DocumentsRepository(val postgrest: PostgrestQueryBuilder) {

    suspend fun getAll(): List<DocumentItem> {
        return postgrest.select().decodeList()
    }

    suspend fun getById(documentId: String): DocumentItem {
        return postgrest.select {
            filter { eq("id", documentId) }
        }.decodeSingle()
    }

    suspend fun getByFileId(fileId: String): DocumentItem {
        return postgrest.select {
            filter { eq("file_id", fileId) }
        }.decodeSingle()
    }

    suspend fun insert(doc: DocumentItem): DocumentItem {
        return postgrest.insert(doc) {
            select()
        }.decodeSingle()
    }
}


class DocumentsDelegate(private val supabase: SupabaseClient) : ReadOnlyProperty<Any?, DocumentsRepository> {
    private val table by lazy {
        supabase.postgrest.from("documents")
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): DocumentsRepository {
        return DocumentsRepository(table)
    }
}
