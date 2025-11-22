package utils

import org.mindrot.jbcrypt.BCrypt

object PasswordUtils {
    private const val SALT_ROUNDS = 12

    fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt(SALT_ROUNDS))
    }

    fun verifyPassword(password: String, hashedPassword: String): Boolean {
        return BCrypt.checkpw(password, hashedPassword)
    }
}