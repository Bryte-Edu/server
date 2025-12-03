package dev.pranav.bryte.server.util.ext

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


fun ApplicationCall.userId() = UserIdDelegate(this)

class UserIdDelegate(private val call: ApplicationCall) : ReadOnlyProperty<Any?, String> {

  override fun getValue(thisRef: Any?, property: KProperty<*>): String {

    val principal = call.principal<JWTPrincipal>()
      ?: throw IllegalStateException("JWT Principal not found in authenticated call.")

    val userId = principal.subject
      ?: throw IllegalStateException("JWT 'subject' claim (userId) is missing.")

    return userId
  }
}