package dev.pranav.bryte.server.routes

import dev.pranav.bryte.FlashcardService
import dev.pranav.bryte.SessionService
import dev.pranav.bryte.server.services.FlashcardServiceImpl
import dev.pranav.bryte.server.services.SessionServiceImpl
import dev.pranav.bryte.server.util.ext.sessions
import dev.pranav.bryte.server.util.ext.supabase
import dev.pranav.bryte.server.util.ext.userId
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.*
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.json

fun Application.configureRpcRoutes() {
    routing {
        authenticate("auth-jwt") {
            rpc("/api/rpc/{sessionId}") {
                rpcConfig {
                    serialization {
                        json()
                    }
                }

                val userId by call.userId()
                val sessions by supabase.sessions()
                val session = sessions.getById(call.parameters["sessionId"]!!)

                if (session == null || session.userId != userId) {
                    throw IllegalAccessException("Session not found or access denied.")
                }

                registerService<SessionService> { SessionServiceImpl(session) }
            }

            rpc("/api/rpc/flashcards/{sessionId}") {
                rpcConfig {
                    serialization {
                        json()
                    }
                }

                val userId by call.userId()
                val sessions by supabase.sessions()
                val session = sessions.getById(call.parameters["sessionId"]!!)

                if (session == null || session.userId != userId) {
                    throw IllegalAccessException("Session not found or access denied.")
                }

                registerService<FlashcardService> { FlashcardServiceImpl(session) }
            }
        }
    }
}
