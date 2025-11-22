package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import models.dto.ErrorResponse
import models.dto.RegisterRequest
import models.dto.RegisterResponse
import repositories.UserRepository
import utils.ValidationUtils

fun Route.configureAuthRoutes(userRepository: UserRepository) {
    route("/api/auth") {
        post("/register") {
            try {
                val request = call.receive<RegisterRequest>()

                val validation = ValidationUtils.validateRegistration(request)
                if (!validation.isValid) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(
                            success = false,
                            error = validation.errorMessage ?: "Invalid request"
                        )
                    )
                    return@post
                }

                val result = userRepository.createUser(request.email, request.password)

                result.fold(
                    onSuccess = { userData ->
                        call.respond(
                            HttpStatusCode.Created,
                            RegisterResponse(
                                success = true,
                                message = "Account created successfully",
                                data = userData
                            )
                        )
                    },
                    onFailure = { exception ->
                        val statusCode = when {
                            exception.message?.contains("already registered") == true ->
                                HttpStatusCode.Conflict
                            else -> HttpStatusCode.InternalServerError
                        }

                        call.respond(
                            statusCode,
                            ErrorResponse(
                                success = false,
                                error = exception.message ?: "Failed to create account"
                            )
                        )
                    }
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(
                        success = false,
                        error = "Invalid request format"
                    )
                )
            }
        }
    }
}