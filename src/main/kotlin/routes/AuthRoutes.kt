package routes

import exceptions.AuthenticationException
import exceptions.ValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import models.dto.AuthData
import models.dto.LoginRequest
import models.dto.RefreshTokenRequest
import models.dto.RegisterRequest
import models.dto.TokenData
import models.dto.UserData
import repositories.TokenRepository
import repositories.UserRepository
import services.NotificationService
import utils.JwtUtils
import utils.PasswordUtils
import utils.ValidationUtils
import java.time.Instant

fun Route.configureAuthRoutes(userRepository: UserRepository, tokenRepository: TokenRepository, notificationService: NotificationService) {
    route("/auth") {
        post("/register") {
            val request = call.receive<RegisterRequest>()

            val validation = ValidationUtils.validateRegistration(request)
            if (!validation.isValid) {
                throw ValidationException(validation.errorMessage ?: "Invalid request")
            }

            val result = userRepository.createUser(request.email, request.password)

            result.fold(
                onSuccess = { userData ->
                    notificationService.sendWelcomeNotification(userData.userId!!, userData.email!!)
                    call.respond(HttpStatusCode.Created, userData)
                },
                onFailure = {
                    call.respond(HttpStatusCode.Created, UserData(email = request.email))
                }
            )
        }

        post("/login") {
            val request = call.receive<LoginRequest>()

            val emailValidation = ValidationUtils.validateEmail(request.email)
            if (!emailValidation.isValid) {
                throw ValidationException(emailValidation.errorMessage ?: "Invalid email format")
            }

            val userResult = userRepository.getUserByEmail(request.email)
            val user = userResult.getOrNull()
                ?: throw AuthenticationException("Invalid email or password")

            if (!PasswordUtils.verifyPassword(request.password, user.passwordHash)) {
                throw AuthenticationException("Invalid email or password")
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
        }

        post("/refresh") {
            val request = call.receive<RefreshTokenRequest>()

            val decoded = JwtUtils.verifyToken(request.refreshToken)
            if (decoded == null || decoded.getClaim("type").asString() != "refresh") {
                throw AuthenticationException("Invalid or expired refresh token")
            }

            val storedToken = tokenRepository.findRefreshToken(request.refreshToken).getOrNull()
            if (storedToken == null || storedToken.revoked || storedToken.expiresAt.isBefore(Instant.now())) {
                throw AuthenticationException("Invalid or expired refresh token")
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
        }
    }
}
