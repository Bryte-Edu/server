package com.mistral.api.sse

import io.ktor.utils.io.*

/** Minimal SSE reader for "data: ..." chunks. Returns raw payload string (concatenated data fields). */
class SseParser {
    suspend fun readNext(channel: ByteReadChannel): String? {
        val sb = StringBuilder()
        var saw = false
        while (!channel.isClosedForRead) {
            val line = channel.readLine() ?: break
            if (line.isBlank()) {
                if (saw) break else continue
            }
            saw = true
            if (line.startsWith("data:")) sb.append(line.removePrefix("data:").trim())
        }
        val out = sb.toString().trim()
        return if (out.isEmpty()) null else out
    }
}