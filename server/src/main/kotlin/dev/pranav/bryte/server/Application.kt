package dev.pranav.bryte.server

import dev.pranav.bryte.server.plugins.*
import dev.pranav.bryte.server.routes.configureFlashcardRoutes
import dev.pranav.bryte.server.routes.configureRpcRoutes
import dev.pranav.bryte.server.routes.configureSessionRoutes
import dev.pranav.bryte.server.util.ext.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.ktor.server.application.*
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    io.ktor.server.cio.EngineMain.main(args)
}

fun Application.module() {
    configureSecurity()
    configureMonitoring()
    configureAdministration()
    configureSockets()
    configureFrameworks()
    configureSerialization()
    configureHTTP()
    configureRouting()
    configureRpcRoutes()
    configureSessionRoutes()
    configureFlashcardRoutes()

    launch {
        supabase.auth.signInWith(Email) {
            email = "test@bryte.com"
            password = "testuser"
        }

        println(supabase.auth.currentAccessTokenOrNull())
    }
}
