package dev.pranav.bryte.server.util.ext

import dev.pranav.bryte.server.postgrest.*
import io.github.jan.supabase.SupabaseClient

/**
 * Provides access to the "sessions" table in Supabase.
 *
 * @return A [SessionsDelegate] for interacting with the "sessions" table.
 */
fun SupabaseClient.sessions() = SessionsDelegate(this)


/**
 * Provides access to the "documents" table in Supabase.
 *
 * @return A [DocumentsDelegate] for interacting with the "documents" table.
 */
fun SupabaseClient.documents() = DocumentsDelegate(this)

/**
 * Provides access to the "document_chunks" table in Supabase.
 *
 * @return A [DocumentChunksDelegate] for interacting with the "document_chunks" table.
 */
fun SupabaseClient.documentChunks() = DocumentChunksDelegate(this)

/**
 * Provides access to the "questions" table in Supabase.
 *
 * @return A [QuestionsDelegate] for interacting with the "questions" table.
 */
fun SupabaseClient.questions() = QuestionsDelegate(this)

/**
 * Provides access to the "flashcards" table in Supabase.
 *
 * @return A [FlashcardsDelegate] for interacting with the "flashcards" table.
 */
fun SupabaseClient.flashcards() = FlashcardsDelegate(this)
