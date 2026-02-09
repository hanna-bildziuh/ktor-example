package utils

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class PasswordUtilsTest {

    @Test
    fun `hashPassword returns a BCrypt hash`() = runTest {
        val hash = PasswordUtils.hashPassword("MyPassword1!")
        assertTrue(hash.startsWith("\$2a\$"))
    }

    @Test
    fun `hashPassword produces different hashes for the same password`() = runTest {
        val hash1 = PasswordUtils.hashPassword("MyPassword1!")
        val hash2 = PasswordUtils.hashPassword("MyPassword1!")
        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `verifyPassword returns true for correct password`() = runTest {
        val hash = PasswordUtils.hashPassword("MyPassword1!")
        assertTrue(PasswordUtils.verifyPassword("MyPassword1!", hash))
    }

    @Test
    fun `verifyPassword returns false for wrong password`() = runTest {
        val hash = PasswordUtils.hashPassword("MyPassword1!")
        assertFalse(PasswordUtils.verifyPassword("WrongPassword1!", hash))
    }

}