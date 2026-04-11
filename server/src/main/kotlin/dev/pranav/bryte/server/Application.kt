package dev.pranav.bryte.server

import dev.pranav.bryte.server.plugins.*
import dev.pranav.bryte.server.routes.configureFlashcardRoutes
import dev.pranav.bryte.server.routes.configureRpcRoutes
import dev.pranav.bryte.server.routes.configureSessionRoutes
import dev.pranav.bryte.server.util.ext.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.cio.EngineMain.main(args)
}

suspend fun Application.module() {
    configureSecurity()
    configureMonitoring()
    configureAdministration()
    configureSockets()
    configureFrameworks()
    configureSerialization()
    configureTemplating()
    configureHTTP()
    configureRouting()
    configureRpcRoutes()
    configureSessionRoutes()
    configureFlashcardRoutes()

    supabase.auth.signInWith(Email) {
        email = TEST_EMAIL
        password = TEST_PASSWORD
    }

    println(supabase.auth.currentAccessTokenOrNull())
}
