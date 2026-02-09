package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import models.dto.Problem
import models.dto.UserProfile
import repositories.UserRepository

fun Route.configureUserRoutes(userRepository: UserRepository) {
    route("/user") {
        authenticate("auth-jwt") {
            get("/profile") {
                val principal = call.principal<JWTPrincipal>()
                val email = principal?.payload?.getClaim("email")?.asString()

                if (email == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        Problem(
                            type = "https://whattoeat.example.com/problems/authentication-failed",
                            title = "Authentication Failed",
                            status = 401,
                            detail = "Invalid or expired token"
                        )
                    )
                    return@get
                }

                val userResult = userRepository.getUserByEmail(email)
                val user = userResult.getOrNull()

                if (user == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        Problem(
                            type = "https://whattoeat.example.com/problems/authentication-failed",
                            title = "Authentication Failed",
                            status = 401,
                            detail = "User not found"
                        )
                    )
                    return@get
                }

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
