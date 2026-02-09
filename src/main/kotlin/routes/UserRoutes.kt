package routes

import exceptions.AuthenticationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import models.dto.UserProfile
import repositories.UserRepository

fun Route.configureUserRoutes(userRepository: UserRepository) {
    route("/user") {
        authenticate("auth-jwt") {
            get("/profile") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim("email")?.asString()
                    ?: throw AuthenticationException("Invalid or expired token")

                val userResult = userRepository.getUserByEmail(email)
                val user = userResult.getOrNull()
                    ?: throw AuthenticationException("User not found")

                call.respond(
                    HttpStatusCode.OK,
                    UserProfile(
                        userId = user.id,
                        email = user.email,
                        createdAt = user.createdAt.toString(),
                        updatedAt = user.updatedAt.toString()
                    )
                )
            }
        }
    }
}
