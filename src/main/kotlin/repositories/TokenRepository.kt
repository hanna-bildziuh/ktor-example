package repositories

import repositories.database.RefreshToken

interface TokenRepository {
    suspend fun storeRefreshToken(userId: Int, token: String, expiresAtMillis: Long): Result<RefreshToken>
    suspend fun findRefreshToken(token: String): Result<RefreshToken?>
    suspend fun revokeRefreshToken(token: String): Result<Boolean>
    suspend fun revokeAllUserTokens(userId: Int): Result<Boolean>
}
