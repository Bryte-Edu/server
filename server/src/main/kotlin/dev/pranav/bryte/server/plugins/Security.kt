package dev.pranav.bryte.server.plugins

import ai.koog.ktor.Koog
import ai.koog.ktor.aiAgent
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.hayden.KHealth
import dev.pranav.bryte.server.SUPABASE_URL
import io.github.flaxoos.ktor.server.plugins.ratelimiter.*
import io.github.flaxoos.ktor.server.plugins.ratelimiter.implementations.*
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.di.*
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.css.*
import kotlinx.html.*
import kotlinx.rpc.krpc.ktor.server.Krpc
import kotlinx.rpc.krpc.ktor.server.rpc
import kotlinx.rpc.krpc.serialization.json.*
import kotlinx.serialization.Serializable
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64

fun Application.configureSecurity() {
    val jwk =
        mapOf(
            "kty" to "EC",
            "crv" to "P-256",
            "x" to "_cmSfJGlzeSXjCop4_f-WjDHDEqYYUd_rJ_eS5EzKrg",
            "y" to "uClbH_QK-eHQalge7t_L2SbSm_giOQ-6tIR0q-WRiHs"
        )
    val publicKey = jwkToECPublicKey(jwk)

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT.require(Algorithm.ECDSA256(publicKey))
                    .withAudience("authenticated")
                    .withIssuer("$SUPABASE_URL/auth/v1")
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains("authenticated")) {
                    println("JWT validated for subject: ${credential.payload.subject}")
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized)
            }
        }
    }
}

/**
 * Converts a JWK (JSON Web Key) representation of an EC public key to an ECPublicKey object.
 *
 * @param jwk A map representing the JWK with keys "kty", "crv", "x", and "y".
 * @return The corresponding ECPublicKey object.
 * @throws IllegalArgumentException if the JWK is invalid or unsupported.
 */
fun jwkToECPublicKey(jwk: Map<String, String>): ECPublicKey {
    // Decode x ad y from Base64URL to BigInteger
    val xBytes = Base64.getUrlDecoder().decode(jwk["x"])
    val yBytes = Base64.getUrlDecoder().decode(jwk["y"])
    val x = BigInteger(1, xBytes) // 1 means positive
    val y = BigInteger(1, yBytes)

    // Generate the EC parameters for the secp256r1 curve
    val keyPairGenerator = KeyPairGenerator.getInstance("EC")
    val ecSpec = ECGenParameterSpec("secp256r1")
    keyPairGenerator.initialize(ecSpec, SecureRandom())
    val keyPair = keyPairGenerator.generateKeyPair()
    val ecPublicKey = keyPair.public as ECPublicKey
    val params = ecPublicKey.params

    // Create the public key
    val pubPoint = ECPoint(x, y)
    val pubSpec = ECPublicKeySpec(pubPoint, params)

    val keyFactory = KeyFactory.getInstance("EC")
    return keyFactory.generatePublic(pubSpec) as ECPublicKey
}