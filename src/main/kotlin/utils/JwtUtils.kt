package utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.Date

object JwtUtils {
    private const val SECRET = "whattoeat-jwt-secret-key-change-in-production"
    const val ISSUER = "whattoeat-api"
    const val AUDIENCE = "whattoeat-users"

    const val ACCESS_TOKEN_EXPIRY_MS = 15L * 60 * 1000           // 15 minutes
    const val REFRESH_TOKEN_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
    const val ACCESS_TOKEN_EXPIRY_SECONDS = 900

    val algorithm: Algorithm = Algorithm.HMAC256(SECRET)

    fun generateAccessToken(userId: Int, email: String): String {
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_MS))
            .sign(algorithm)
    }

    fun generateRefreshToken(userId: Int, email: String): String {
        return JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim("userId", userId)
            .withClaim("email", email)
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRY_MS))
            .sign(algorithm)
    }

    fun verifyToken(token: String): DecodedJWT? {
        return try {
            JWT.require(algorithm)
                .withIssuer(ISSUER)
                .withAudience(AUDIENCE)
                .build()
                .verify(token)
        } catch (e: Exception) {
            null
        }
    }
}
