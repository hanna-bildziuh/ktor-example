# Login Flow & JWT Authentication - Implementation Plan

## Overview

This document outlines the implementation plan for user login, JWT token management, and protected endpoints in the Ktor application. This builds on the completed create account flow.

---

## Implementation Status

| Component | Status |
|---|---|
| Maven dependencies (ktor-server-auth, ktor-server-auth-jwt) | DONE |
| RefreshTokens database table | DONE |
| JWT utility (JwtUtils) | DONE |
| Token repository (interface + implementation) | DONE |
| Ktor Authentication plugin config | DONE |
| POST /auth/login endpoint | DONE |
| POST /auth/refresh endpoint | DONE |
| GET /user/profile endpoint (protected) | DONE |
| Application wiring (WhatToEat.kt) | DONE |

---

## 1. Dependencies

Add to `pom.xml` after existing Ktor dependencies:

```xml
<!-- JWT Authentication -->
<dependency>
    <groupId>io.ktor</groupId>
    <artifactId>ktor-server-auth-jvm</artifactId>
    <version>${ktor.version}</version>
</dependency>
<dependency>
    <groupId>io.ktor</groupId>
    <artifactId>ktor-server-auth-jwt-jvm</artifactId>
    <version>${ktor.version}</version>
</dependency>
```

`ktor-server-auth-jwt-jvm` transitively includes `com.auth0:java-jwt`.

---

## 2. Database Schema

### RefreshTokens Table

**File:** `src/main/kotlin/repositories/database/RefreshTokens.kt`

```kotlin
object RefreshTokens : Table("refresh_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val token = varchar("token", 512).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at").clientDefault { Instant.now() }
    val revoked = bool("revoked").default(false)

    override val primaryKey = PrimaryKey(id)
}
```

**Fields:**
- `id`: Auto-incrementing primary key
- `userId`: Foreign key to Users table
- `token`: The JWT refresh token string (unique indexed for fast lookup)
- `expiresAt`: Absolute expiration time
- `createdAt`: When the token was issued
- `revoked`: Allows token revocation without deletion

---

## 3. JWT Token Strategy

### Token Types

| Token | Expiry | Purpose |
|---|---|---|
| Access Token | 15 minutes | Authenticates API requests via `Authorization: Bearer <token>` header |
| Refresh Token | 7 days | Used to obtain new access + refresh tokens when access token expires |

### JWT Claims

Both tokens include:
- `userId` (Int) - User's database ID
- `email` (String) - User's email
- `type` (String) - `"access"` or `"refresh"` to prevent token type confusion
- `iss` - Issuer: `"whattoeat-api"`
- `aud` - Audience: `"whattoeat-users"`
- `exp` - Expiration timestamp

### Token Rotation

Refresh tokens are single-use. When a refresh token is used:
1. Old refresh token is revoked in the database
2. New access + refresh tokens are issued
3. New refresh token is stored in the database

This limits the damage if a refresh token is leaked.

### JwtUtils Implementation

**File:** `src/main/kotlin/utils/JwtUtils.kt`

```kotlin
object JwtUtils {
    private const val SECRET = "whattoeat-jwt-secret-key-change-in-production"
    private const val ISSUER = "whattoeat-api"
    private const val AUDIENCE = "whattoeat-users"

    const val ACCESS_TOKEN_EXPIRY_MS = 15L * 60 * 1000       // 15 minutes
    const val REFRESH_TOKEN_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000  // 7 days
    const val ACCESS_TOKEN_EXPIRY_SECONDS = 900

    fun generateAccessToken(userId: Int, email: String): String
    fun generateRefreshToken(userId: Int, email: String): String
    fun verifyToken(token: String): DecodedJWT?   // null on failure
}
```

---

## 4. API Endpoints

### POST /auth/login

**Request:**
```json
{
  "email": "user@example.com",
  "password": "SecurePass123!"
}
```

**Success Response (200 OK):**
```json
{
  "userId": 1,
  "email": "user@example.com",
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 900
}
```

**Error Responses:**

401 Unauthorized - Invalid credentials:
```json
{
  "type": "https://whattoeat.example.com/problems/authentication-failed",
  "title": "Authentication Failed",
  "status": 401,
  "detail": "Invalid email or password"
}
```

400 Bad Request - Invalid input:
```json
{
  "type": "https://whattoeat.example.com/problems/validation-error",
  "title": "Validation Error",
  "status": 400,
  "detail": "Invalid email format"
}
```

**Logic Flow:**
1. Receive and validate `LoginRequest`
2. Look up user by email via `UserRepository.getUserByEmail()`
3. Verify password via `PasswordUtils.verifyPassword()`
4. Generate access + refresh tokens
5. Store refresh token in database
6. Return `AuthData`

### POST /auth/refresh

**Request:**
```json
{
  "refreshToken": "eyJ..."
}
```

**Success Response (200 OK):**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 900
}
```

**Error Response (401):**
```json
{
  "type": "https://whattoeat.example.com/problems/authentication-failed",
  "title": "Authentication Failed",
  "status": 401,
  "detail": "Invalid or expired refresh token"
}
```

**Logic Flow:**
1. Receive `RefreshTokenRequest`, verify JWT signature
2. Check `type` claim is `"refresh"`
3. Look up token in database - verify not revoked, not expired
4. Revoke old refresh token (token rotation)
5. Generate new access + refresh tokens
6. Store new refresh token
7. Return `TokenData`

### GET /user/profile (Protected)

**Request Headers:**
```
Authorization: Bearer <accessToken>
```

**Success Response (200 OK):**
```json
{
  "userId": 1,
  "email": "user@example.com",
  "createdAt": "2025-11-22T20:15:00Z",
  "updatedAt": "2025-11-22T20:15:00Z"
}
```

**Error Response (401) - Missing/invalid token:**
```json
{
  "type": "https://whattoeat.example.com/problems/authentication-required",
  "title": "Authentication Required",
  "status": 401,
  "detail": "Missing or invalid authentication token"
}
```

---

## 5. Ktor Authentication Plugin

**File:** `src/main/kotlin/plugins/AuthPlugin.kt`

Installs Ktor's `Authentication` plugin with JWT verifier named `"auth-jwt"`:
- Verifies token signature, issuer, audience
- Requires `type=access` claim (so refresh tokens can't be used as access tokens)
- Validates `userId` and `email` claims exist
- Returns `Problem` DTO on 401 challenge

Protected routes use `authenticate("auth-jwt") { ... }` wrapper.

---

## 6. Token Repository

**Interface:** `src/main/kotlin/repositories/TokenRepository.kt`
**Implementation:** `src/main/kotlin/repositories/services/TokenRepositoryImplementation.kt`

```kotlin
interface TokenRepository {
    suspend fun storeRefreshToken(userId: Int, token: String, expiresAtMillis: Long): Result<RefreshToken>
    suspend fun findRefreshToken(token: String): Result<RefreshToken?>
    suspend fun revokeRefreshToken(token: String): Result<Boolean>
    suspend fun revokeAllUserTokens(userId: Int): Result<Boolean>
}
```

Follows same patterns as `UserRepository`/`UserRepositoryImplementation`: `dbQuery()`, `Result<T>`, `rowToModel()`.

---

## 7. Application Wiring

**File:** `src/main/kotlin/WhatToEat.kt`

Changes to `Application.module()`:
```kotlin
fun Application.module() {
    configureCORS()
    configureSerialization()
    configureAuthentication()    // NEW - must be before routing
    configureDatabase()
    configureRouting()
}
```

Changes to `configureDatabase()`:
```kotlin
SchemaUtils.create(Users, RefreshTokens)
```

Changes to `configureRouting()`:
```kotlin
fun Application.configureRouting() {
    val userRepository = UserRepositoryImplementation()
    val tokenRepository = TokenRepositoryImplementation()

    routing {
        configureGenericRoutes()
        configureAuthRoutes(userRepository, tokenRepository)
        configureUserRoutes(userRepository)
    }
}
```

---

## 8. Project Structure After Implementation

```
src/main/kotlin/
├── WhatToEat.kt                                    (modified)
├── plugins/
│   └── AuthPlugin.kt                               (new)
├── repositories/
│   ├── UserRepository.kt
│   ├── TokenRepository.kt                           (new)
│   ├── database/
│   │   ├── Users.kt
│   │   └── RefreshTokens.kt                         (new)
│   └── services/
│       ├── UserRepositoryImplementation.kt
│       └── TokenRepositoryImplementation.kt          (new)
├── routes/
│   ├── AuthRoutes.kt                                (modified)
│   ├── GenericRoute.kt
│   └── UserRoutes.kt                                (new)
└── utils/
    ├── PasswordUtils.kt
    ├── ValidationUtils.kt
    └── JwtUtils.kt                                   (new)
```

---

## 9. Security Considerations

- Use generic "Invalid email or password" error message on login failure (don't reveal which is wrong)
- Refresh token rotation (single-use) limits damage from token leaks
- Access tokens include `type=access` claim; auth plugin rejects refresh tokens used as access tokens
- JWT secret is hardcoded for development -- must be externalized for production
- `revokeAllUserTokens()` available for future password change / logout-all-devices feature

---

## 10. Existing Code Reused

| Code | File | Used For |
|---|---|---|
| `PasswordUtils.verifyPassword()` | `utils/PasswordUtils.kt` | Login password check |
| `UserRepository.getUserByEmail()` | `repositories/UserRepository.kt` | Login user lookup + profile fetch |
| `dbQuery()` | `repositories/services/UserRepositoryImplementation.kt` | Token repository DB operations |
| `Problem` DTO | auto-generated `models.dto` | All error responses |
| `LoginRequest` DTO | auto-generated `models.dto` | Login request body |
| `AuthData` DTO | auto-generated `models.dto` | Login success response |
| `TokenData` DTO | auto-generated `models.dto` | Refresh success response |
| `RefreshTokenRequest` DTO | auto-generated `models.dto` | Refresh request body |
| `UserProfile` DTO | auto-generated `models.dto` | Profile success response |
| `ValidationUtils.validateEmail()` | `utils/ValidationUtils.kt` | Login email validation |

---

## 11. Testing

```bash
# 1. Register
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123!"}'

# 2. Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123!"}'

# 3. Profile (with token)
curl http://localhost:8080/user/profile \
  -H "Authorization: Bearer <accessToken>"

# 4. Profile (no token - should 401)
curl http://localhost:8080/user/profile

# 5. Refresh tokens
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'

# 6. Reuse old refresh token (should 401 - revoked)
curl -X POST http://localhost:8080/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<old-refreshToken>"}'

# 7. Wrong password (should 401)
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"WrongPass123!"}'
```

Also verify with `mvn compile`.