# Plan: Add Coroutine Pattern Features + Tests

## Context
The WhatToEat Ktor app currently has working auth flows but uses coroutines minimally (only `suspend` + `newSuspendedTransaction` in repositories). The goal is to add features that demonstrate real coroutine patterns (`async`, `launch`, `withContext`, `coroutineScope`, `withTimeoutOrNull`) and test them with `kotlinx-coroutines-test`.

---

## Step 0: Add Test Dependency

**Add dependency to `pom.xml`:**
- `kotlinx-coroutines-test:1.10.1` (test scope)

**Verify:** `mvn compile` passes.

---

## Step 1: Coroutine-Aware Password Hashing
**Pattern:** `withContext(Dispatchers.Default)` for CPU-bound work
**Why genuine:** BCrypt with 12 salt rounds takes ~250ms and currently blocks the Ktor event loop thread.

**Modify:** `src/main/kotlin/utils/PasswordUtils.kt`
- Make `hashPassword` and `verifyPassword` suspend functions
- Wrap BCrypt calls in `withContext(dispatcher)` with an injectable `CoroutineDispatcher` parameter (defaults to `Dispatchers.Default`)

**Tests — update:** `src/test/kotlin/utils/PasswordUtilsTest.kt`
- Suspend hashing works correctly
- Verify returns true/false correctly
- Runs on provided dispatcher (inject `StandardTestDispatcher`)

**Verify:** `mvn test` — all tests pass.

---

## Step 2: Parallel Registration Processing
**Pattern:** `coroutineScope { async { } }` for structured concurrency
**Why genuine:** `emailExists()` (DB query) and `hashPassword()` (CPU work) are independent — running them in parallel saves ~250ms per registration.

**Modify:** `src/main/kotlin/repositories/services/UserRepositoryImplementation.kt`
- Refactor `createUser` to run `emailExists` and `hashPassword` concurrently via `async`
- Use `coroutineScope` to maintain structured concurrency

**Tests — update:** `src/test/kotlin/repositories/UserRepositoryTest.kt`
- Parallel email check + hash in `createUser`
- Duplicate email failure still works

**Verify:** `mvn test` — all tests pass.

---

## Step 3: Background Notification Service
**Pattern:** `scope.launch` for fire-and-forget background work
**Why genuine:** Welcome email after registration should not block the HTTP response.

**Create:**
- `src/main/kotlin/services/NotificationService.kt` — interface
- `src/main/kotlin/services/NotificationServiceImplementation.kt` — uses injected `CoroutineScope` + `launch` to simulate async email sending with `delay`

**Modify:**
- `src/main/kotlin/routes/AuthRoutes.kt` — accept `NotificationService`, call `sendWelcomeNotification` after successful registration
- `src/main/kotlin/WhatToEat.kt` — create `NotificationServiceImplementation` with application coroutine scope, pass to routes

**Tests — create:** `src/test/kotlin/services/NotificationServiceTest.kt`
- Launch doesn't block caller
- `advanceUntilIdle` completes the notification
- Cancellation of scope cancels pending notifications

**Tests — update:** `src/test/kotlin/routes/AuthRoutesTest.kt`
- Register (201) still works with notification wired in
- Register (409) for duplicate email still works

**Verify:** `mvn test` — all tests pass.

---

## Step 4: Enhanced Parallel Health Check
**Pattern:** `coroutineScope { async { } }` + `withTimeoutOrNull` for resilient parallel checks
**Why genuine:** Production health endpoints check multiple subsystems concurrently with timeouts.

**Create:**
- `src/main/kotlin/services/HealthService.kt` — interface + `HealthStatus`/`ComponentHealth` data classes
- `src/main/kotlin/services/HealthServiceImplementation.kt` — parallel DB + JWT checks with `async` and `withTimeoutOrNull(5000)`

**Modify:**
- `src/main/kotlin/routes/GenericRoute.kt` — accept `HealthService`, return structured JSON instead of plain "OK"
- `src/main/kotlin/WhatToEat.kt` — create `HealthServiceImplementation`, pass to generic routes

**Tests — create:** `src/test/kotlin/services/HealthServiceTest.kt`
- Parallel checks return structured status
- Timeout produces "down" component status

**Tests — create:** `src/test/kotlin/routes/HealthRouteTest.kt`
- Integration test for `/health` JSON response

**Verify:** `mvn test` — all tests pass.

---

## Step 5: Parallel Login Token Generation
**Pattern:** `coroutineScope { async { } }` in request handler
**Why:** Mostly pedagogical (JWT signing is fast), but demonstrates `async`/`await` in a different context.

**Modify:** `src/main/kotlin/routes/AuthRoutes.kt`
- After password verification in login handler, generate access + refresh tokens in parallel with `async`
- Store refresh token concurrently

**Tests — update:** `src/test/kotlin/routes/AuthRoutesTest.kt`
- Login (200) still returns valid tokens
- Login (401) for wrong password still works

**Verify:** `mvn test` — all tests pass.

---

## Files Summary

**Modify (6):** `pom.xml`, `PasswordUtils.kt`, `UserRepositoryImplementation.kt`, `AuthRoutes.kt`, `GenericRoute.kt`, `WhatToEat.kt`
**Create (4):** `NotificationService.kt`, `NotificationServiceImplementation.kt`, `HealthService.kt`, `HealthServiceImplementation.kt`
**Create tests (3):** `NotificationServiceTest.kt`, `HealthServiceTest.kt`, `HealthRouteTest.kt`
**Update tests (3):** `PasswordUtilsTest.kt`, `UserRepositoryTest.kt`, `AuthRoutesTest.kt`

## Coroutine Pattern Coverage

| Pattern | Step | Genuine? |
|---------|------|----------|
| `withContext(Dispatchers.Default)` | 1 — PasswordUtils | Yes — unblocks event loop |
| `coroutineScope { async {} }` | 2 — Parallel registration | Yes — saves ~250ms |
| `scope.launch` (fire-and-forget) | 3 — NotificationService | Yes — background work |
| `async` + `withTimeoutOrNull` | 4 — Health checks | Yes — resilient parallel checks |
| `coroutineScope { async {} }` | 5 — Login tokens | Pedagogical |
| `runTest` / `TestScope` / `backgroundScope` | 1–5 (each step) | Yes — proper coroutine testing |

## Verification
After each step: `mvn test` — all tests pass.

Final:
1. `mvn test` — full suite green
2. Manual: start server, `curl POST /auth/register` — returns 201, server logs show background notification
3. Manual: `curl GET /health` — returns JSON with component statuses and response times
