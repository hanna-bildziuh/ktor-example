package utils

import models.dto.RegisterRequest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class ValidationUtilsTest {

    // --- Email validation ---

    @Test
    fun `validateEmail accepts valid email`() {
        val result = ValidationUtils.validateEmail("user@example.com")
        assertTrue(result.isValid)
    }

    @Test
    fun `validateEmail rejects blank email`() {
        val result = ValidationUtils.validateEmail("")
        assertFalse(result.isValid)
        assertEquals("Email is required", result.errorMessage)
    }

    @Test
    fun `validateEmail rejects email without at sign`() {
        val result = ValidationUtils.validateEmail("userexample.com")
        assertFalse(result.isValid)
        assertEquals("Invalid email format", result.errorMessage)
    }

    @Test
    fun `validateEmail rejects email without domain extension`() {
        val result = ValidationUtils.validateEmail("user@example")
        assertFalse(result.isValid)
        assertEquals("Invalid email format", result.errorMessage)
    }

    @Test
    fun `validateEmail rejects email exceeding 255 characters`() {
        val longEmail = "a".repeat(250) + "@b.com"
        val result = ValidationUtils.validateEmail(longEmail)
        assertFalse(result.isValid)
        assertEquals("Email too long (max 255 characters)", result.errorMessage)
    }

    // --- Password validation ---

    @Test
    fun `validatePassword accepts valid password`() {
        val result = ValidationUtils.validatePassword("MyPass1!")
        assertTrue(result.isValid)
    }

    @Test
    fun `validatePassword rejects blank password`() {
        val result = ValidationUtils.validatePassword("")
        assertFalse(result.isValid)
        assertEquals("Password is required", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects password shorter than 8 chars`() {
        val result = ValidationUtils.validatePassword("Ab1!")
        assertFalse(result.isValid)
        assertEquals("Password must be at least 8 characters", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects password exceeding 128 chars`() {
        val longPw = "A1!" + "a".repeat(126)
        val result = ValidationUtils.validatePassword(longPw)
        assertFalse(result.isValid)
        assertEquals("Password too long (max 128 characters)", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects password without uppercase`() {
        val result = ValidationUtils.validatePassword("mypass1!")
        assertFalse(result.isValid)
        assertEquals("Password must contain at least one uppercase letter", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects password without lowercase`() {
        val result = ValidationUtils.validatePassword("MYPASS1!")
        assertFalse(result.isValid)
        assertEquals("Password must contain at least one lowercase letter", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects password without digit`() {
        val result = ValidationUtils.validatePassword("MyPasswd!")
        assertFalse(result.isValid)
        assertEquals("Password must contain at least one digit", result.errorMessage)
    }

    @Test
    fun `validatePassword rejects password without special character`() {
        val result = ValidationUtils.validatePassword("MyPasswd1")
        assertFalse(result.isValid)
        assertEquals("Password must contain at least one special character", result.errorMessage)
    }

    // --- Registration validation ---

    @Test
    fun `validateRegistration accepts valid request`() {
        val result = ValidationUtils.validateRegistration(
            RegisterRequest(email = "user@example.com", password = "MyPass1!")
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `validateRegistration rejects invalid email first`() {
        val result = ValidationUtils.validateRegistration(
            RegisterRequest(email = "bad", password = "MyPass1!")
        )
        assertFalse(result.isValid)
        assertEquals("Invalid email format", result.errorMessage)
    }

    @Test
    fun `validateRegistration rejects invalid password when email is valid`() {
        val result = ValidationUtils.validateRegistration(
            RegisterRequest(email = "user@example.com", password = "short")
        )
        assertFalse(result.isValid)
        assertEquals("Password must be at least 8 characters", result.errorMessage)
    }
}
