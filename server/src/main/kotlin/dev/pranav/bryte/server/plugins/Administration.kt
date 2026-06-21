package dev.pranav.bryte.server.plugins

import io.github.flaxoos.ktor.server.plugins.ratelimiter.RateLimiting
import io.github.flaxoos.ktor.server.plugins.ratelimiter.implementations.TokenBucket
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureAdministration() {
    routing {
        install(RateLimiting) {
            rateLimiter {
                type = TokenBucket::class
                capacity = 5
                rate = 10.seconds
            }
        }
    }
}
