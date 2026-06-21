package dev.pranav.bryte.server.plugins

import ai.koog.ktor.Koog
import dev.pranav.bryte.server.GEMINI_API_KEY
import dev.pranav.bryte.server.MISTRAL_API_KEY
import io.ktor.server.application.*
import io.ktor.server.sse.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureFrameworks() {
    install(SSE)
    install(Krpc)
    install(Koog) {
        llm {
            google(apiKey = GEMINI_API_KEY)
            mistral(apiKey = MISTRAL_API_KEY)
        }
    }
    install(Koin) {
        slf4jLogger()
    }
}

