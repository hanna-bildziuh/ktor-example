package utils

import models.dto.RegisterRequest

object ValidationUtils {
    private val EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
    private val PASSWORD_UPPERCASE = ".*[A-Z].*".toRegex()
    private val PASSWORD_LOWERCASE = ".*[a-z].*".toRegex()
    private val PASSWORD_DIGIT = ".*\\d.*".toRegex()
    private val PASSWORD_SPECIAL = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*".toRegex()

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null
    )

    fun validateEmail(email: String): ValidationResult {
        return when {
            email.isBlank() -> ValidationResult(false, "Email is required")
            email.length > 255 -> ValidationResult(false, "Email too long (max 255 characters)")
            !EMAIL_REGEX.matches(email) -> ValidationResult(false, "Invalid email format")
            else -> ValidationResult(true)
        }
    }

    fun validatePassword(password: String): ValidationResult {
        return when {
            password.isBlank() -> ValidationResult(false, "Password is required")
            password.length < 8 -> ValidationResult(false, "Password must be at least 8 characters")
            password.length > 128 -> ValidationResult(false, "Password too long (max 128 characters)")
            !PASSWORD_UPPERCASE.matches(password) -> ValidationResult(false, "Password must contain at least one uppercase letter")
            !PASSWORD_LOWERCASE.matches(password) -> ValidationResult(false, "Password must contain at least one lowercase letter")
            !PASSWORD_DIGIT.matches(password) -> ValidationResult(false, "Password must contain at least one digit")
            !PASSWORD_SPECIAL.matches(password) -> ValidationResult(false, "Password must contain at least one special character")
            else -> ValidationResult(true)
        }
    }

    fun validateRegistration(request: RegisterRequest): ValidationResult {
        val emailValidation = validateEmail(request.email)
        if (!emailValidation.isValid) return emailValidation

        val passwordValidation = validatePassword(request.password)
        if (!passwordValidation.isValid) return passwordValidation

        return ValidationResult(true)
    }
}