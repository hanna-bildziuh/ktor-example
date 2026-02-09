package utils

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class PasswordUtilsTest {

    @Test
    fun `hashPassword returns a BCrypt hash`() {
        val hash = PasswordUtils.hashPassword("MyPassword1!")
        assertTrue(hash.startsWith("\$2a\$"))
    }

    @Test
    fun `hashPassword produces different hashes for the same password`() {
        val hash1 = PasswordUtils.hashPassword("MyPassword1!")
        val hash2 = PasswordUtils.hashPassword("MyPassword1!")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `verifyPassword returns true for correct password`() {
        val hash = PasswordUtils.hashPassword("MyPassword1!")
        assertTrue(PasswordUtils.verifyPassword("MyPassword1!", hash))
    }

    @Test
    fun `verifyPassword returns false for wrong password`() {
        val hash = PasswordUtils.hashPassword("MyPassword1!")
        assertFalse(PasswordUtils.verifyPassword("WrongPassword1!", hash))
    }
}
