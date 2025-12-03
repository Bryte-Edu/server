package dev.pranav.bryte.server.util.ext

import dev.pranav.bryte.server.SUPABASE_KEY
import dev.pranav.bryte.server.SUPABASE_URL
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.storage.Storage

val supabase by lazy {
    createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) {
        defaultSerializer = KotlinXSerializer()

        install(Auth)
        install(Storage)
        install(Postgrest)
    }
}