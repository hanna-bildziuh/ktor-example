package utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mindrot.jbcrypt.BCrypt

object PasswordUtils {
    private const val SALT_ROUNDS = 12

    suspend fun hashPassword(password: String): String = withContext(Dispatchers.Default) {
        BCrypt.hashpw(password, BCrypt.gensalt(SALT_ROUNDS))
    }

    suspend fun verifyPassword(password: String, hashedPassword: String): Boolean = withContext(Dispatchers.Default) {
        BCrypt.checkpw(password, hashedPassword)
    }
}