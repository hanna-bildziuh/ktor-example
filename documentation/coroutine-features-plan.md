# Plan: Add Coroutine Pattern Features + Tests

## Context
The WhatToEat Ktor app currently has working auth flows but uses coroutines minimally (only `suspend` + `newSuspendedTransaction` in repositories). The goal is to add features that demonstrate real coroutine patterns (`async`, `launch`, `withContext`, `coroutineScope`, `withTimeoutOrNull`) and test them with `kotlinx-coroutines-test`.

---

## Feature 1: Coroutine-Aware Password Hashing
**Pattern:** `withContext(Dispatchers.Default)` for CPU-bound work
**Why genuine:** BCrypt with 12 salt rounds takes ~250ms and currently blocks the Ktor event loop thread.

**Modify:** `src/main/kotlin/utils/PasswordUtils.kt`
- Make `hashPassword` and `verifyPassword` suspend functions
- Wrap BCrypt calls in `withContext(dispatcher)` with an injectable `CoroutineDispatcher` parameter (defaults to `Dispatchers.Default`)

---

## Feature 2: Parallel Registration Processing
**Pattern:** `coroutineScope { async { } }` for structured concurrency
**Why genuine:** `emailExists()` (DB query) and `hashPassword()` (CPU work) are independent — running them in parallel saves ~250ms per registration.

**Modify:** `src/main/kotlin/repositories/services/UserRepositoryImplementation.kt`
- Refactor `createUser` to run `emailExists` and `hashPassword` concurrently via `async`
- Use `coroutineScope` to maintain structured concurrency

---

## Feature 3: Background Notification Service
**Pattern:** `scope.launch` for fire-and-forget background work
**Why genuine:** Welcome email after registration should not block the HTTP response.

**Create:**
- `src/main/kotlin/services/NotificationService.kt` — interface
- `src/main/kotlin/services/NotificationServiceImplementation.kt` — uses injected `CoroutineScope` + `launch` to simulate async email sending with `delay`

**Modify:**
- `src/main/kotlin/routes/AuthRoutes.kt` — accept `NotificationService`, call `sendWelcomeNotification` after successful registration
- `src/main/kotlin/WhatToEat.kt` — create `NotificationServiceImplementation` with application coroutine scope, pass to routes

---

## Feature 4: Enhanced Parallel Health Check
**Pattern:** `coroutineScope { async { } }` + `withTimeoutOrNull` for resilient parallel checks
**Why genuine:** Production health endpoints check multiple subsystems concurrently with timeouts.

**Create:**
- `src/main/kotlin/services/HealthService.kt` — interface + `HealthStatus`/`ComponentHealth` data classes
- `src/main/kotlin/services/HealthServiceImplementation.kt` — parallel DB + JWT checks with `async` and `withTimeoutOrNull(5000)`

**Modify:**
- `src/main/kotlin/routes/GenericRoute.kt` — accept `HealthService`, return structured JSON instead of plain "OK"
- `src/main/kotlin/WhatToEat.kt` — create `HealthServiceImplementation`, pass to generic routes

---

## Feature 5: Parallel Login Token Generation
**Pattern:** `coroutineScope { async { } }` in request handler
**Why:** Mostly pedagogical (JWT signing is fast), but demonstrates `async`/`await` in a different context.

**Modify:** `src/main/kotlin/routes/AuthRoutes.kt`
- After password verification in login handler, generate access + refresh tokens in parallel with `async`
- Store refresh token concurrently

---

## Feature 6: Coroutine Tests
**Pattern:** `runTest`, `TestScope`, `StandardTestDispatcher`, `backgroundScope`, `advanceUntilIdle`

**Add dependency to `pom.xml`:**
- `kotlinx-coroutines-test:1.10.1` (test scope)

**Create test files:**

| File | Tests |
|------|-------|
| `src/test/kotlin/utils/PasswordUtilsTest.kt` | Suspend hashing works, verify returns true/false correctly, runs on provided dispatcher |
| `src/test/kotlin/services/NotificationServiceTest.kt` | Launch doesn't block, `advanceUntilIdle` completes notification, cancellation works |
| `src/test/kotlin/services/HealthServiceTest.kt` | Parallel checks return structured status, timeout produces "down" |
| `src/test/kotlin/repositories/UserRepositoryTest.kt` | Parallel email check + hash in createUser, duplicate email failure |
| `src/test/kotlin/routes/AuthRoutesTest.kt` | Integration tests with `testApplication` for register (201, 409) and login (200, 401) |
| `src/test/kotlin/routes/HealthRouteTest.kt` | Integration test for `/health` JSON response |

---

## Implementation Order
1. `pom.xml` — add `kotlinx-coroutines-test`
2. Feature 1 — PasswordUtils (other features depend on this)
3. Feature 2 — Parallel registration (depends on Feature 1)
4. Feature 3 — NotificationService + wiring
5. Feature 4 — HealthService + wiring
6. Feature 5 — Login refactor
7. Feature 6 — All tests

## Files Summary

**Modify (6):** `pom.xml`, `PasswordUtils.kt`, `UserRepositoryImplementation.kt`, `AuthRoutes.kt`, `GenericRoute.kt`, `WhatToEat.kt`
**Create (10):** 4 service files + 6 test files

## Coroutine Pattern Coverage

| Pattern | Feature | Genuine? |
|---------|---------|----------|
| `withContext(Dispatchers.Default)` | PasswordUtils | Yes — unblocks event loop |
| `coroutineScope { async {} }` | Parallel registration | Yes — saves ~250ms |
| `scope.launch` (fire-and-forget) | NotificationService | Yes — background work |
| `async` + `withTimeoutOrNull` | Health checks | Yes — resilient parallel checks |
| `coroutineScope { async {} }` | Login tokens | Pedagogical |
| `runTest` / `TestScope` / `backgroundScope` | All tests | Yes — proper coroutine testing |

## Verification
1. `mvn compile` — project builds
2. `mvn test` — all tests pass
3. Manual: start server, `curl POST /auth/register` — returns 201, server logs show background notification
4. Manual: `curl GET /health` — returns JSON with component statuses and response times