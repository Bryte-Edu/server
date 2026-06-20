package dev.pranav.bryte.server.plugins

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.logging.*

internal val LOGGER = KtorSimpleLogger("dev.pranav.bryte.server.plugins.RequestTracePlugin")
internal val StartTimeKey = AttributeKey<Long>("StartTime")

val RequestTracePlugin = createRouteScopedPlugin("RequestTracePlugin") {
    onCall { call ->
        call.attributes.put(StartTimeKey, System.currentTimeMillis())
        LOGGER.trace("--> Processing request: ${call.request.httpMethod.value} ${call.request.uri}")
    }

    onCallRespond { call, _ ->
        val startTime = call.attributes.getOrNull(StartTimeKey)
        if (startTime != null) {
            val duration = System.currentTimeMillis() - startTime
            val status = call.response.status()?.value ?: "Unknown"
            LOGGER.trace(
                "<-- Completed request: {} {} with status {} in {}ms",
                call.request.httpMethod.value,
                call.request.uri,
                status,
                duration
            )
        }
    }
}
