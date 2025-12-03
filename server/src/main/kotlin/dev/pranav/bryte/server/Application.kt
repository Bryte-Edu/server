package dev.pranav.bryte.server

import dev.pranav.bryte.server.plugins.*
import dev.pranav.bryte.server.routes.configureSampleRoutes
import dev.pranav.bryte.server.routes.configureRpcRoutes
import dev.pranav.bryte.server.routes.configureFlashcardRoutes
import dev.pranav.bryte.server.routes.configureSessionRoutes
import io.ktor.server.application.*

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
    configureTemplating()
    configureHTTP()
    configureRouting()
    configureSampleRoutes()
    configureRpcRoutes()
    configureSessionRoutes()
    configureFlashcardRoutes()
}
