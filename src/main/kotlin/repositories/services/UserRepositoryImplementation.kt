package repositories.services

import models.dto.UserData
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import repositories.UserRepository
import repositories.database.User
import repositories.database.Users
import utils.PasswordUtils
import java.time.Instant

class UserRepositoryImplementation : UserRepository {

    override suspend fun createUser(email: String, password: String): Result<UserData> {
        return try {
            if (emailExists(email)) {
                return Result.failure(Exception("The email is already registered"))
            }

            val hashedPassword = PasswordUtils.hashPassword(password)
            val now = Instant.now()

            val userId = dbQuery {
                Users.insert {
                    it[Users.email] = email.lowercase()
                    it[passwordHash] = hashedPassword
                    it[createdAt] = now
                    it[updatedAt] = now
                } get Users.id
            }

            val userData = UserData(
                userId = userId,
                email = email.lowercase(),
                createdAt = now.toString()
            )

            Result.success(userData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserByEmail(email: String): Result<User?> {
        return try {
            val user = dbQuery {
                Users.selectAll().where { Users.email eq email.lowercase() }
                    .map { rowToUser(it) }
                    .singleOrNull()
            }
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun emailExists(email: String): Boolean {
        return dbQuery {
            Users.selectAll().where { Users.email eq email.lowercase() }
                .count() > 0
        }
    }

    private fun rowToUser(row: ResultRow): User {
        return User(
            id = row[Users.id],
            email = row[Users.email],
            passwordHash = row[Users.passwordHash],
            createdAt = row[Users.createdAt],
            updatedAt = row[Users.updatedAt]
        )
    }
}

suspend fun <T> dbQuery(block: suspend () -> T): T {
    return newSuspendedTransaction { block() }
}