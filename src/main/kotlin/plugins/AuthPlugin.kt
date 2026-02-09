package plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import models.dto.Problem
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
                call.respond(
                    HttpStatusCode.Unauthorized,
                    Problem(
                        type = "https://whattoeat.example.com/problems/authentication-required",
                        title = "Authentication Required",
                        status = 401,
                        detail = "Missing or invalid authentication token"
                    )
                )
            }
        }
    }
}
