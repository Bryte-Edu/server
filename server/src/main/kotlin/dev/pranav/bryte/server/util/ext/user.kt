package dev.pranav.bryte.server.util.ext

import dev.pranav.bryte.server.errors.UnauthorizedException
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty


fun ApplicationCall.userId() = UserIdDelegate(this)

class UserIdDelegate(private val call: ApplicationCall) : ReadOnlyProperty<Any?, String> {

  override fun getValue(thisRef: Any?, property: KProperty<*>): String {

    val principal = call.principal<JWTPrincipal>()
        ?: throw UnauthorizedException("JWT principal missing")

    val userId = principal.subject
        ?: throw UnauthorizedException("JWT subject missing")

    return userId
  }
}