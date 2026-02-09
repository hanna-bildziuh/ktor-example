package plugins

import exceptions.AuthenticationException
import exceptions.AuthenticationRequiredException
import exceptions.ConflictException
import exceptions.ValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import models.dto.Problem

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                Problem(
                    type = "https://whattoeat.example.com/problems/validation-error",
                    title = "Validation Error",
                    status = 400,
                    detail = cause.message ?: "Invalid request"
                )
            )
        }

        exception<AuthenticationException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                Problem(
                    type = "https://whattoeat.example.com/problems/authentication-failed",
                    title = "Authentication Failed",
                    status = 401,
                    detail = cause.message ?: "Authentication failed"
                )
            )
        }

        exception<AuthenticationRequiredException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                Problem(
                    type = "https://whattoeat.example.com/problems/authentication-required",
                    title = "Authentication Required",
                    status = 401,
                    detail = cause.message ?: "Authentication required"
                )
            )
        }

        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                Problem(
                    type = "https://whattoeat.example.com/problems/conflict",
                    title = "Resource Conflict",
                    status = 409,
                    detail = cause.message ?: "Resource conflict"
                )
            )
        }

        exception<Exception> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                Problem(
                    type = "https://whattoeat.example.com/problems/server-error",
                    title = "Internal Server Error",
                    status = 500,
                    detail = cause.message ?: "An unexpected error occurred"
                )
            )
        }
    }
}
