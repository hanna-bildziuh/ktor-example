# Create Account Flow Implementation Plan

## Overview
This document outlines the detailed implementation plan for the user registration (create account) flow in the Ktor-based application using Exposed ORM and JWT authentication.

---

## 1. Database Schema

### Users Table (Exposed DSL)

```kotlin
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = datetime("created_at").clientDefault { LocalDateTime.now() }
    val updatedAt = datetime("updated_at").clientDefault { LocalDateTime.now() }

    override val primaryKey = PrimaryKey(id)
}
```

**Fields:**
- `id`: Auto-incrementing primary key
- `email`: Unique email address (max 255 chars)
- `passwordHash`: BCrypt hashed password
- `createdAt`: Account creation timestamp
- `updatedAt`: Last update timestamp

---

## 2. API Endpoint Specification

### Endpoint: Create Account
```
POST /api/auth/register
Content-Type: application/json
```

### Request Body
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

### Success Response (201 Created)
```json
{
  "success": true,
  "message": "Account created successfully",
  "data": {
    "userId": 1,
    "email": "user@example.com",
    "createdAt": "2025-11-22T20:15:00"
  }
}
```

### Error Responses

**400 Bad Request** - Invalid input
```json
{
  "success": false,
  "error": "Invalid email format"
}
```

**409 Conflict** - Email already exists
```json
{
  "success": false,
  "error": "Email already registered"
}
```

**500 Internal Server Error** - Server error
```json
{
  "success": false,
  "error": "Internal server error"
}
```

---

## 3. Data Models (DTOs)

### Request DTO
```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String
)
```

### Response DTOs
```kotlin
import kotlinx.serialization.Serializable

@Serializable
data class RegisterResponse(
    val success: Boolean,
    val message: String? = null,
    val data: UserData? = null,
    val error: String? = null
)

@Serializable
data class UserData(
    val userId: Int,
    val email: String,
    val createdAt: String
)
```

---

## 4. Password Security

### BCrypt Implementation

**Add Dependency to pom.xml:**
```xml
<dependency>
    <groupId>org.mindrot</groupId>
    <artifactId>jbcrypt</artifactId>
    <version>0.4</version>
</dependency>
```

**Password Utility Class:**
```kotlin
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
```

**Security Considerations:**
- Use BCrypt with 12 salt rounds (good balance of security and performance)
- Never store plain text passwords
- Hash is one-way (cannot be reversed)
- Each hash includes unique salt automatically

---

## 5. Input Validation

### Validation Rules

**Email Validation:**
- Must match valid email format
- Maximum 255 characters
- Case-insensitive (normalize to lowercase)

**Password Validation:**
- Minimum 8 characters
- Maximum 128 characters (BCrypt limit is 72, but we'll validate before)
- Must contain at least:
  - One uppercase letter
  - One lowercase letter
  - One digit
  - One special character

### Validation Implementation

```kotlin
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
```

---

## 6. Service Layer Architecture

### UserService Interface

```kotlin
interface UserService {
    suspend fun createUser(email: String, password: String): Result<UserData>
    suspend fun getUserByEmail(email: String): Result<User?>
    suspend fun emailExists(email: String): Boolean
}

data class User(
    val id: Int,
    val email: String,
    val passwordHash: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
```

### UserService Implementation

```kotlin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class UserServiceImpl : UserService {

    override suspend fun createUser(email: String, password: String): Result<UserData> {
        return try {
            // Check if email already exists
            if (emailExists(email)) {
                return Result.failure(Exception("Email already registered"))
            }

            // Hash password
            val hashedPassword = PasswordUtils.hashPassword(password)

            // Insert user into database
            val userId = dbQuery {
                Users.insert {
                    it[Users.email] = email.lowercase()
                    it[passwordHash] = hashedPassword
                    it[createdAt] = LocalDateTime.now()
                    it[updatedAt] = LocalDateTime.now()
                } get Users.id
            }

            val userData = UserData(
                userId = userId,
                email = email.lowercase(),
                createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
            )

            Result.success(userData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserByEmail(email: String): Result<User?> {
        return try {
            val user = dbQuery {
                Users.select { Users.email eq email.lowercase() }
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
            Users.select { Users.email eq email.lowercase() }
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

// Database query helper
suspend fun <T> dbQuery(block: suspend () -> T): T {
    return newSuspendedTransaction { block() }
}
```

---

## 7. Routing Configuration

### Authentication Routes

```kotlin
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureAuthRoutes(userService: UserService) {
    routing {
        route("/api/auth") {
            // Register endpoint
            post("/register") {
                try {
                    // Parse request
                    val request = call.receive<RegisterRequest>()

                    // Validate input
                    val validation = ValidationUtils.validateRegistration(request)
                    if (!validation.isValid) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            RegisterResponse(
                                success = false,
                                error = validation.errorMessage
                            )
                        )
                        return@post
                    }

                    // Create user
                    val result = userService.createUser(request.email, request.password)

                    result.fold(
                        onSuccess = { userData ->
                            call.respond(
                                HttpStatusCode.Created,
                                RegisterResponse(
                                    success = true,
                                    message = "Account created successfully",
                                    data = userData
                                )
                            )
                        },
                        onFailure = { exception ->
                            val statusCode = when {
                                exception.message?.contains("already registered") == true ->
                                    HttpStatusCode.Conflict
                                else -> HttpStatusCode.InternalServerError
                            }

                            call.respond(
                                statusCode,
                                RegisterResponse(
                                    success = false,
                                    error = exception.message ?: "Failed to create account"
                                )
                            )
                        }
                    )
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        RegisterResponse(
                            success = false,
                            error = "Invalid request format"
                        )
                    )
                }
            }
        }
    }
}
```

### Application.kt Setup

```kotlin
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureSerialization()
        configureDatabase()

        val userService = UserServiceImpl()
        configureAuthRoutes(userService)
    }.start(wait = true)
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun Application.configureDatabase() {
    // Connect to H2 database
    Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        driver = "org.h2.Driver",
        user = "root",
        password = ""
    )

    // Create tables
    transaction {
        SchemaUtils.create(Users)
    }
}
```

---

## 8. Implementation Steps

### Step 1: Add BCrypt Dependency
Update `pom.xml` to include jBCrypt:
```xml
<dependency>
    <groupId>org.mindrot</groupId>
    <artifactId>jbcrypt</artifactId>
    <version>0.4</version>
</dependency>
```

### Step 2: Create Project Structure
```
src/main/kotlin/
├── Application.kt
├── models/
│   ├── User.kt
│   └── dto/
│       ├── RegisterRequest.kt
│       └── RegisterResponse.kt
├── database/
│   └── Tables.kt
├── services/
│   ├── UserService.kt
│   └── UserServiceImpl.kt
├── routes/
│   └── AuthRoutes.kt
└── utils/
    ├── PasswordUtils.kt
    └── ValidationUtils.kt
```

### Step 3: Implement Database Layer
1. Create `Tables.kt` with Users table definition
2. Set up database connection in Application.kt
3. Create schema on application startup

### Step 4: Create DTOs
1. Define `RegisterRequest.kt`
2. Define `RegisterResponse.kt` and `UserData.kt`
3. Add `@Serializable` annotations

### Step 5: Implement Utilities
1. Create `PasswordUtils.kt` for BCrypt hashing
2. Create `ValidationUtils.kt` for input validation
3. Add comprehensive validation rules

### Step 6: Implement Service Layer
1. Define `UserService` interface
2. Implement `UserServiceImpl` with database operations
3. Add error handling and transaction management

### Step 7: Create Routes
1. Implement `configureAuthRoutes` function
2. Add POST `/api/auth/register` endpoint
3. Wire up validation, service calls, and responses

### Step 8: Configure Application
1. Set up content negotiation (JSON)
2. Initialize database connection
3. Register routes
4. Start server

### Step 9: Testing
Test the endpoint using curl:
```bash
# Successful registration
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123!"}'

# Duplicate email
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"AnotherPass123!"}'

# Invalid email
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"invalid-email","password":"SecurePass123!"}'

# Weak password
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"user@example.com","password":"weak"}'
```

---

## 9. Security Considerations

### Password Security
- ✅ BCrypt with 12 salt rounds
- ✅ Never log or expose passwords
- ✅ Validate password strength before hashing
- ✅ Limit password length to prevent DoS (BCrypt has 72 byte limit)

### Input Validation
- ✅ Validate email format
- ✅ Normalize email to lowercase
- ✅ Sanitize all inputs
- ✅ Return generic error messages (don't leak info)

### Database Security
- ✅ Use parameterized queries (Exposed handles this)
- ✅ Unique constraint on email
- ✅ Proper error handling to prevent information leakage

### API Security
- ⚠️ Consider rate limiting (implement later)
- ⚠️ Consider email verification (implement later)
- ⚠️ Consider CAPTCHA for production (implement later)

---

## 10. Next Steps

After implementing the create account flow:

1. **Login Flow**
   - Implement POST `/api/auth/login`
   - Generate JWT tokens
   - Return access and refresh tokens

2. **JWT Authentication**
   - Add JWT library dependency
   - Implement token generation
   - Create authentication middleware
   - Protect API endpoints

3. **Password Reset**
   - Implement forgot password flow
   - Email verification
   - Token-based password reset

4. **User Profile**
   - GET `/api/user/profile`
   - Update user information
   - Change password endpoint

5. **Recipe Management**
   - Create recipes table
   - Implement favorite recipes feature
   - Link recipes to users

---

## Dependencies Checklist

- [x] kotlin-stdlib
- [x] ktor-server-core-jvm
- [x] ktor-server-netty-jvm
- [x] ktor-server-content-negotiation-jvm
- [x] ktor-serialization-kotlinx-json-jvm
- [x] exposed-core
- [x] exposed-jdbc
- [x] h2 database
- [ ] jbcrypt (needs to be added)
- [ ] ktor-server-auth-jvm (for JWT, add later)
- [ ] ktor-server-auth-jwt-jvm (for JWT, add later)
