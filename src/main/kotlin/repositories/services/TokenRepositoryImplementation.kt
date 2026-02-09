package repositories.services

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import repositories.TokenRepository
import repositories.database.RefreshToken
import repositories.database.RefreshTokens
import java.time.Instant

class TokenRepositoryImplementation : TokenRepository {

    override suspend fun storeRefreshToken(userId: Int, token: String, expiresAtMillis: Long): Result<RefreshToken> {
        return try {
            val now = Instant.now()
            val expiresAt = Instant.ofEpochMilli(expiresAtMillis)

            val id = dbQuery {
                RefreshTokens.insert {
                    it[RefreshTokens.userId] = userId
                    it[RefreshTokens.token] = token
                    it[RefreshTokens.expiresAt] = expiresAt
                    it[RefreshTokens.createdAt] = now
                    it[RefreshTokens.revoked] = false
                } get RefreshTokens.id
            }

            Result.success(
                RefreshToken(
                    id = id,
                    userId = userId,
                    token = token,
                    expiresAt = expiresAt,
                    createdAt = now,
                    revoked = false
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun findRefreshToken(token: String): Result<RefreshToken?> {
        return try {
            val refreshToken = dbQuery {
                RefreshTokens.selectAll().where { RefreshTokens.token eq token }
                    .map { rowToRefreshToken(it) }
                    .singleOrNull()
            }
            Result.success(refreshToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun revokeRefreshToken(token: String): Result<Boolean> {
        return try {
            val updated = dbQuery {
                RefreshTokens.update({ RefreshTokens.token eq token }) {
                    it[revoked] = true
                }
            }
            Result.success(updated > 0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun revokeAllUserTokens(userId: Int): Result<Boolean> {
        return try {
            val updated = dbQuery {
                RefreshTokens.update({ RefreshTokens.userId eq userId }) {
                    it[revoked] = true
                }
            }
            Result.success(updated > 0)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun rowToRefreshToken(row: ResultRow): RefreshToken {
        return RefreshToken(
            id = row[RefreshTokens.id],
            userId = row[RefreshTokens.userId],
            token = row[RefreshTokens.token],
            expiresAt = row[RefreshTokens.expiresAt],
            createdAt = row[RefreshTokens.createdAt],
            revoked = row[RefreshTokens.revoked]
        )
    }
}
