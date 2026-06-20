package dev.pranav

import dev.pranav.bryte.FlashcardService
import dev.pranav.bryte.SessionService
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.rpc.RpcClient
import kotlinx.rpc.krpc.ktor.client.installKrpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService

fun HttpClient.rpcClient(url: String): RpcClient = rpc {
    url(url)

    rpcConfig {
        serialization {
            json()
        }
    }
}

fun RpcClient.sessionService(): SessionService = withService<SessionService>()
fun RpcClient.flashcardService(): FlashcardService = withService<FlashcardService>()


fun main(args: Array<String>) = runBlocking {
    val token = args[0]
    val documentId = args[1]

    println("Starting RPC client...")

    val client by lazy {
        HttpClient {
            installKrpc {
                serialization {
                    json()
                }
            }
        }
    }

    println("HttpClient with kRPC installed.")

    val f = client.rpc {
        bearerAuth(token)

        url {
            host = "localhost"
            port = 8080
            encodedPath = "/api/rpc/flashcards/$documentId"
        }
        println("connecting")
    }.flashcardService().flashcards()

    println("Flow received")
    f.collect {
        println("Received: $it")
    }
}