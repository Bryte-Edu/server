package dev.pranav.bryte.server.plugins

import dev.hayden.KHealth
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import org.slf4j.event.Level
import kotlin.text.startsWith

fun Application.configureMonitoring() {
    install(KHealth)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    install(CallLogging) {
        level = Level.DEBUG
    }
}
