package dev.pranav.bryte.server.plugins

import ai.koog.ktor.Koog
import ai.koog.ktor.aiAgent
import ai.koog.prompt.executor.clients.google.GoogleModels
import dev.pranav.bryte.server.GEMINI_API_KEY
import dev.pranav.bryte.server.MISTRAL_API_KEY
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureFrameworks() {
    install(Krpc)
    install(Koog) {
        llm {
            google(apiKey = GEMINI_API_KEY)
            mistral(apiKey = MISTRAL_API_KEY)
        }
    }

    routing {
        route("/ai") {
            post("/chat") {
                val userInput = call.receive<String>()
                val output = aiAgent(userInput, model = GoogleModels.Gemini2_0FlashLite001)
                call.respondText(output)
            }
        }
    }
    install(Koin) {
        slf4jLogger()
    }
}

