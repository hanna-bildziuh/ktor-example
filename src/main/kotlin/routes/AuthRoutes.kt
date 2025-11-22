package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import models.dto.Problem
import models.dto.RegisterRequest
import repositories.UserRepository
import utils.ValidationUtils

fun Route.configureAuthRoutes(userRepository: UserRepository) {
    route("/auth") {
        post("/register") {
            try {
                val request = call.receive<RegisterRequest>()

                val validation = ValidationUtils.validateRegistration(request)
                if (!validation.isValid) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Problem(
                            type = "https://whattoeat.example.com/problems/validation-error",
                            title = "Validation Error",
                            status = 400,
                            detail = validation.errorMessage ?: "Invalid request"
                        )
                    )
                    return@post
                }

                val result = userRepository.createUser(request.email, request.password)

                result.fold(
                    onSuccess = { userData ->
                        call.respond(HttpStatusCode.Created, userData)
                    },
                    onFailure = { exception ->
                        val isConflict = exception.message?.contains("already registered") == true
                        val statusCode = if (isConflict) HttpStatusCode.Conflict else HttpStatusCode.InternalServerError

                        call.respond(
                            statusCode,
                            Problem(
                                type = if (isConflict) "https://whattoeat.example.com/problems/conflict"
                                    else "https://whattoeat.example.com/problems/server-error",
                                title = if (isConflict) "Resource Conflict" else "Internal Server Error",
                                status = statusCode.value,
                                detail = exception.message ?: "Failed to create account"
                            )
                        )
                    }
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    Problem(
                        type = "https://whattoeat.example.com/problems/validation-error",
                        title = "Validation Error",
                        status = 400,
                        detail = "Invalid request format"
                    )
                )
            }
        }
    }
}