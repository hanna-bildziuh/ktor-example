package repositories

import models.dto.UserData
import repositories.database.User

interface UserRepository {
    suspend fun createUser(email: String, password: String): Result<UserData>
    suspend fun getUserByEmail(email: String): Result<User?>
    suspend fun emailExists(email: String): Boolean
}