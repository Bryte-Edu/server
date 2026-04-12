package dev.pranav

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import kotlinx.rpc.krpc.ktor.client.installKrpc

fun HttpClientConfig<*>.configureForProject() {
    installKrpc()
    install(SSE)
}
