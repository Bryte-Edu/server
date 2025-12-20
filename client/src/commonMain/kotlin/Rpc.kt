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


suspend fun main() = runBlocking {

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
        bearerAuth("eyJhbGciOiJFUzI1NiIsImtpZCI6IjZjODJkMjU2LTYwMmQtNDM1MC1hNmMyLTczMTlhYzAwMDNiMyIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Zjc2RpY3dkaWJ4ZGp1Z3dobWRlLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiIwZTY5M2ZkYi1iOGQ4LTQxNjgtODNiOS05YzhmNGQ2YzIyOGUiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzY2MjMwMjA4LCJpYXQiOjE3NjYyMjY2MDgsImVtYWlsIjoidGVzdEBicnl0ZS5jb20iLCJwaG9uZSI6IiIsImFwcF9tZXRhZGF0YSI6eyJwcm92aWRlciI6ImVtYWlsIiwicHJvdmlkZXJzIjpbImVtYWlsIl19LCJ1c2VyX21ldGFkYXRhIjp7ImFib3V0X21lIjoia2J1eWJvXG4iLCJlbWFpbCI6InRlc3RAYnJ5dGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsInBob25lX3ZlcmlmaWVkIjpmYWxzZSwic3ViIjoiMGU2OTNmZGItYjhkOC00MTY4LTgzYjktOWM4ZjRkNmMyMjhlIiwidXNlcm5hbWUiOiJ1dGdvcHUifSwicm9sZSI6ImF1dGhlbnRpY2F0ZWQiLCJhYWwiOiJhYWwxIiwiYW1yIjpbeyJtZXRob2QiOiJwYXNzd29yZCIsInRpbWVzdGFtcCI6MTc2NjIyNjYwOH1dLCJzZXNzaW9uX2lkIjoiNmFhZDI4MjUtMTAwOS00MzE3LWE5MDItNjM0MWQ3NDFlM2FkIiwiaXNfYW5vbnltb3VzIjpmYWxzZX0.4WNxNPZoh7Su2F_MAw37VI-F2sb-IaqBA2hz6CAxs1SlLmd8Iovf00bne2yznZ5z7dFTepty8g8vj7Dmy9IXPg")

        url {
            host = "localhost"
            port = 8080
            encodedPath = "/api/rpc/flashcards/f938bee7-7dad-47cc-b892-2fdf763b38f4"
        }
        println("connecting")
    }.flashcardService().flashcards()

    println("Flow received")
    f.collect {
        println("Received: $it")
    }
}