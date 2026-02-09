package repositories.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object RefreshTokens : Table("refresh_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val token = varchar("token", 512).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val revoked = bool("revoked").default(false)

    override val primaryKey = PrimaryKey(id)
}

data class RefreshToken(
    val id: Int,
    val userId: Int,
    val token: String,
    val expiresAt: Instant,
    val createdAt: Instant,
    val revoked: Boolean
)
