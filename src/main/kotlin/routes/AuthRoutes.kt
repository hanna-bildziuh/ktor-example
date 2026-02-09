package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import models.dto.AuthData
import models.dto.LoginRequest
import models.dto.Problem
import models.dto.RefreshTokenRequest
import models.dto.RegisterRequest
import models.dto.TokenData
import repositories.TokenRepository
import repositories.UserRepository
import utils.JwtUtils
import utils.PasswordUtils
import utils.ValidationUtils
import java.time.Instant

fun Route.configureAuthRoutes(userRepository: UserRepository, tokenRepository: TokenRepository) {
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

        post("/login") {
            try {
                val request = call.receive<LoginRequest>()

                val emailValidation = ValidationUtils.validateEmail(request.email)
                if (!emailValidation.isValid) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        Problem(
                            type = "https://whattoeat.example.com/problems/validation-error",
                            title = "Validation Error",
                            status = 400,
                            detail = emailValidation.errorMessage ?: "Invalid email format"
                        )
                    )
                    return@post
                }

                val userResult = userRepository.getUserByEmail(request.email)
                val user = userResult.getOrNull()

                if (user == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        Problem(
                            type = "https://whattoeat.example.com/problems/authentication-failed",
                            title = "Authentication Failed",
                            status = 401,
                            detail = "Invalid email or password"
                        )
                    )
                    return@post
                }

                if (!PasswordUtils.verifyPassword(request.password, user.passwordHash)) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        Problem(
                            type = "https://whattoeat.example.com/problems/authentication-failed",
                            title = "Authentication Failed",
                            status = 401,
                            detail = "Invalid email or password"
                        )
                    )
                    return@post
                }

                val accessToken = JwtUtils.generateAccessToken(user.id, user.email)
                val refreshToken = JwtUtils.generateRefreshToken(user.id, user.email)

                tokenRepository.storeRefreshToken(
                    userId = user.id,
                    token = refreshToken,
                    expiresAtMillis = System.currentTimeMillis() + JwtUtils.REFRESH_TOKEN_EXPIRY_MS
                )

                call.respond(
                    HttpStatusCode.OK,
                    AuthData(
                        userId = user.id,
                        email = user.email,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresIn = JwtUtils.ACCESS_TOKEN_EXPIRY_SECONDS
                    )
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

        post("/refresh") {
            try {
                val request = call.receive<RefreshTokenRequest>()

                val decoded = JwtUtils.verifyToken(request.refreshToken)
                if (decoded == null || decoded.getClaim("type").asString() != "refresh") {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        Problem(
                            type = "https://whattoeat.example.com/problems/authentication-failed",
                            title = "Authentication Failed",
                            status = 401,
                            detail = "Invalid or expired refresh token"
                        )
                    )
                    return@post
                }

                val storedToken = tokenRepository.findRefreshToken(request.refreshToken).getOrNull()
                if (storedToken == null || storedToken.revoked || storedToken.expiresAt.isBefore(Instant.now())) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        Problem(
                            type = "https://whattoeat.example.com/problems/authentication-failed",
                            title = "Authentication Failed",
                            status = 401,
                            detail = "Invalid or expired refresh token"
                        )
                    )
                    return@post
                }

                tokenRepository.revokeRefreshToken(request.refreshToken)

                val userId = decoded.getClaim("userId").asInt()
                val email = decoded.getClaim("email").asString()

                val newAccessToken = JwtUtils.generateAccessToken(userId, email)
                val newRefreshToken = JwtUtils.generateRefreshToken(userId, email)

                tokenRepository.storeRefreshToken(
                    userId = userId,
                    token = newRefreshToken,
                    expiresAtMillis = System.currentTimeMillis() + JwtUtils.REFRESH_TOKEN_EXPIRY_MS
                )

                call.respond(
                    HttpStatusCode.OK,
                    TokenData(
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken,
                        expiresIn = JwtUtils.ACCESS_TOKEN_EXPIRY_SECONDS
                    )
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
