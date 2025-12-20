package dev.pranav.bryte.server

import dev.pranav.bryte.SessionService
import dev.pranav.bryte.server.plugins.configureSerialization
import dev.pranav.bryte.server.plugins.configureSockets
import dev.pranav.bryte.server.routes.configureRpcRoutes
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.rpc.krpc.ktor.client.Krpc
import kotlinx.rpc.krpc.ktor.client.rpc
import kotlinx.rpc.krpc.ktor.client.rpcConfig
import kotlinx.rpc.krpc.serialization.json.json
import kotlinx.rpc.withService
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

    @Test
    fun testRpc() = testApplication {
        application {
            configureSockets()
            configureSerialization()
            configureRpcRoutes()
        }

        val ktorClient = createClient {
            install(WebSockets)
            install(Krpc)
        }

        val rpcClient = ktorClient.rpc("/api") {
            rpcConfig {
                serialization {
                    json()
                }
            }
        }

        val service = rpcClient.withService<SessionService>()


        assertDoesNotThrow {
            service.details()
        }
    }

}
