# Testing Plan — WhatToEat Ktor App

## Goal

Establish baseline test coverage for all existing code before adding coroutine features.

## Test Dependencies

Already in `pom.xml`:
- `kotlin-test-junit5` (Kotlin 2.2.20)
- `junit-jupiter` (5.10.0)
- `ktor-server-test-host-jvm` (Ktor 3.0.3)

Added:
- `ktor-client-content-negotiation-jvm` (Ktor 3.0.3) — needed for JSON content negotiation in test client

## Test Files

| # | File | Tests | Type | Description |
|---|------|-------|------|-------------|
| 1 | `utils/PasswordUtilsTest.kt` | 4 | Unit | BCrypt hashing and verification |
| 2 | `utils/ValidationUtilsTest.kt` | 12+ | Unit | Email and password validation rules |
| 3 | `utils/JwtUtilsTest.kt` | 7 | Unit | JWT generation, verification, claims |
| 4 | `repositories/UserRepositoryTest.kt` | 5 | Integration (H2) | User CRUD operations |
| 5 | `repositories/TokenRepositoryTest.kt` | 6 | Integration (H2) | Refresh token storage and revocation |
| 6 | `routes/GenericRouteTest.kt` | 2 | Ktor testApplication | Health check and root endpoint |
| 7 | `routes/AuthRoutesTest.kt` | 8+ | Ktor testApplication + DB | Register, login, refresh endpoints |
| 8 | `routes/UserRoutesTest.kt` | 3 | Ktor testApplication + DB + JWT | Protected profile endpoint |

## Testing Approach

### Unit Tests (1-3)
Pure function tests — no database or server needed. Test input/output directly.

### Integration Tests (4-5)
Use H2 in-memory database with `@BeforeEach` setup to create fresh schema per test. Test repository implementations against real SQL.

### Route Tests (6-8)
Use Ktor `testApplication {}` API with configured modules (serialization, auth, routing). Test HTTP status codes, response bodies, and error handling.

## Running Tests

```bash
mvn test
```
