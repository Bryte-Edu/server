package dev.pranav.bryte.server.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import net.mamoe.yamlkt.toYamlElement
import java.util.*

fun Application.configureSampleRoutes() {
  routing {
    get("/") {
      call.respondText("Welcome to Ktor Sample App!")
    }

    authenticate("auth-jwt") {
      get("/protected") {
        val principal = call.principal<JWTPrincipal>()!!
        val username = principal!!.payload.getClaim("username").asString()
        call.respondText("Hello, $username! You are authenticated.")
      }
    }

    webSocket("/ws") {
      send("Connected to WebSocket!")
      for (frame in incoming) {
        if (frame is Frame.Text) {
          val text = frame.readText()
          send("Echo: $text")
          if (text.equals("bye", ignoreCase = true)) {
            close(CloseReason(CloseReason.Codes.NORMAL, "Client said BYE"))
          }
        }
      }
    }
  }
}
