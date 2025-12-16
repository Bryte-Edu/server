package dev.pranav

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

    println("HttpClient with KRPC installed.")

    client.rpc {
        bearerAuth("eyJhbGciOiJFUzI1NiIsImtpZCI6IjZjODJkMjU2LTYwMmQtNDM1MC1hNmMyLTczMTlhYzAwMDNiMyIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2Zjc2RpY3dkaWJ4ZGp1Z3dobWRlLnN1cGFiYXNlLmNvL2F1dGgvdjEiLCJzdWIiOiIwZTY5M2ZkYi1iOGQ4LTQxNjgtODNiOS05YzhmNGQ2YzIyOGUiLCJhdWQiOiJhdXRoZW50aWNhdGVkIiwiZXhwIjoxNzY1ODIzNzg0LCJpYXQiOjE3NjU4MjAxODQsImVtYWlsIjoidGVzdEBicnl0ZS5jb20iLCJwaG9uZSI6IiIsImFwcF9tZXRhZGF0YSI6eyJwcm92aWRlciI6ImVtYWlsIiwicHJvdmlkZXJzIjpbImVtYWlsIl19LCJ1c2VyX21ldGFkYXRhIjp7ImFib3V0X21lIjoia2J1eWJvXG4iLCJlbWFpbCI6InRlc3RAYnJ5dGUuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsInBob25lX3ZlcmlmaWVkIjpmYWxzZSwic3ViIjoiMGU2OTNmZGItYjhkOC00MTY4LTgzYjktOWM4ZjRkNmMyMjhlIiwidXNlcm5hbWUiOiJ1dGdvcHUifSwicm9sZSI6ImF1dGhlbnRpY2F0ZWQiLCJhYWwiOiJhYWwxIiwiYW1yIjpbeyJtZXRob2QiOiJwYXNzd29yZCIsInRpbWVzdGFtcCI6MTc2NTgyMDE4NH1dLCJzZXNzaW9uX2lkIjoiNjFlYjgxNDktYjgzZS00ODQ3LTk2MmYtNGQ1ZmViODQ1OTkxIiwiaXNfYW5vbnltb3VzIjpmYWxzZX0.NLHhH2gwbKslv-2VaQaULz_z-FmQvut75BmFrmAp4KuM5z1G2HrwxvCyIdC2hHClPOiBfOczmHF59Xr2s9mf7Q")

        url {
            host = "localhost"
            port = 8080
            encodedPath = "/api/rpc/d07cedf7-9ab5-4955-845a-59f7d4386771"
        }
        println("connecting")
    }.sessionService().questions().collect {
        println("Received question: $it")
    }
}