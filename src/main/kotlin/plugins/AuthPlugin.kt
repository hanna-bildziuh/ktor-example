package plugins

import exceptions.AuthenticationRequiredException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import utils.JwtUtils

fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                com.auth0.jwt.JWT.require(JwtUtils.algorithm)
                    .withIssuer(JwtUtils.ISSUER)
                    .withAudience(JwtUtils.AUDIENCE)
                    .withClaim("type", "access")
                    .build()
            )

            validate { credential ->
                val userId = credential.payload.getClaim("userId")
                val email = credential.payload.getClaim("email")
                if (!userId.isMissing && !email.isMissing) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { _, _ ->
                throw AuthenticationRequiredException("Missing or invalid authentication token")
            }
        }
    }
}
