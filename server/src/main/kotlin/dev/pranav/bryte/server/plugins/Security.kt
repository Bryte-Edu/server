package dev.pranav.bryte.server.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.pranav.bryte.model.ErrorResponse
import dev.pranav.bryte.server.JWK_X
import dev.pranav.bryte.server.JWK_Y
import dev.pranav.bryte.server.SUPABASE_URL
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.*

fun Application.configureSecurity() {
    val jwk =
        mapOf(
            "kty" to "EC",
            "crv" to "P-256",
            "x" to JWK_X,
            "y" to JWK_Y
        )
    val publicKey = jwkToECPublicKey(jwk)

    install(Authentication) {
        jwt("auth-jwt") {
            authHeader { call ->
                val header = call.request.parseAuthorizationHeader()
                if (header != null) return@authHeader header

                val queryToken = call.request.queryParameters["token"]
                if (!queryToken.isNullOrBlank()) {
                    return@authHeader HttpAuthHeader.Single("Bearer", queryToken)
                }

                null
            }
            verifier(
                JWT.require(Algorithm.ECDSA256(publicKey))
                    .withAudience("authenticated")
                    .withIssuer("$SUPABASE_URL/auth/v1")
                    .build()
            )
            validate { credential ->
                if (credential.payload.audience.contains("authenticated")) {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
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
    val xBytes = Base64.getUrlDecoder().decode(jwk["x"])
    val yBytes = Base64.getUrlDecoder().decode(jwk["y"])
    val pubPoint = ECPoint(BigInteger(1, xBytes), BigInteger(1, yBytes))

    val ecParameters = AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1"))
        getParameterSpec(ECParameterSpec::class.java)
    }

    val pubSpec = ECPublicKeySpec(pubPoint, ecParameters)
    return KeyFactory.getInstance("EC").generatePublic(pubSpec) as ECPublicKey
}
