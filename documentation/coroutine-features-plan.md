# Plan: Coroutine Improvements & New Patterns

## Context

Steps 0–5 from the original plan are **implemented**. The app now demonstrates `withContext`, `coroutineScope`/`async`, `scope.launch`, `withTimeoutOrNull`, `Semaphore`, and `Mutex`. See `documentation/review.md` for a full review of the current implementation.

This plan covers:
- **Part A** — Fixes for coroutine bugs found in the review
- **Part B** — New coroutine patterns that improve performance and demonstrate advanced usage

---

## Part A: Fix Existing Coroutine Issues

### Step A1: Fix NotificationService scope leak

**Problem:** `SupervisorJob()` without a parent creates a root job. Application shutdown does not cancel pending notifications.

**Modify:** `WhatToEat.kt`
```kotlin
// Before:
CoroutineScope(coroutineContext + SupervisorJob())
// After:
CoroutineScope(coroutineContext + SupervisorJob(coroutineContext.job))
```

**Tests — update:** `NotificationServiceTest.kt`
- Verify that cancelling the parent scope cancels pending notifications

**Verify:** `mvn test`

---

### Step A2: Remove unnecessary async for JWT generation

**Problem:** `JwtUtils.generateAccessToken` and `generateRefreshToken` are synchronous, CPU-light functions (~microseconds). Wrapping them in `async` adds coroutine overhead with zero concurrency benefit.

**Modify:** `AuthRoutes.kt` — replace `coroutineScope { async {} ... async {} }` in login handler with direct sequential calls:
```kotlin
val accessToken = JwtUtils.generateAccessToken(user.id, user.email)
val refreshToken = JwtUtils.generateRefreshToken(user.id, user.email)
```

**Tests — update:** `AuthRoutesTest.kt` — login tests still pass

**Verify:** `mvn test`

---

### Step A3: Fix RecipeCache mutexMap memory leak

**Problem:** `mutexMap: ConcurrentHashMap<String, Mutex>` grows unbounded. Mutex entries persist after Caffeine evicts the corresponding cache entry.

**Modify:** `RecipeCache.kt` — remove mutex from map after computation completes:
```kotlin
return mutex.withLock {
    cache.getIfPresent(key)?.let { return@withLock it }
    val result = compute()
    cache.put(key, result)
    mutexMap.remove(key)
    result
}
```

**Tests — update:** `RecipeCacheTest.kt` — add test that verifies mutexMap doesn't grow after repeated getOrCompute calls with different keys

**Verify:** `mvn test`

---

### Step A4: Fix RecipeService cached flag accuracy

**Problem:** When check 1 (`recipeCache.get`) misses but `getOrCompute` returns a cached value (populated by a concurrent request), the result is reported as `cached = false`.

**Modify:** `RecipeService.kt` — use a single code path:
```kotlin
suspend fun searchRecipe(...): RecipeResponse {
    val cacheKey = buildCacheKey(ingredients, dietaryRestrictions)
    val prompt = buildPrompt(ingredients, dietaryRestrictions)
    var wasComputed = false

    val result = recipeCache.getOrCompute(cacheKey) {
        wasComputed = true
        claudeClient.generateRecipe(prompt)
    }

    return parseRecipeResponse(result, cached = !wasComputed)
}
```

**Tests — create:** `RecipeServiceTest.kt` — verify `cached` flag is `true` on second call with same ingredients

**Verify:** `mvn test`

---

### Step A5: Use monotonic clock in HealthService

**Problem:** `System.currentTimeMillis()` is wall-clock time, subject to NTP adjustments. Elapsed time measurement should use a monotonic clock.

**Modify:** `HealthService.kt` — use `kotlin.time.measureTimedValue`:
```kotlin
val (result, duration) = measureTimedValue {
    withTimeoutOrNull(timeoutMs) { ... }
}
return ComponentHealth(
    status = if (result == true) "up" else "down",
    responseTimeMs = duration.inWholeMilliseconds
)
```

**Verify:** `mvn test`

---

## Part B: New Coroutine Patterns

### Step B1: Flow-Based Recipe Streaming (SSE)

**Pattern:** `Flow`, `channelFlow`, Server-Sent Events
**Why genuine:** The Claude API supports streaming responses. Currently the app waits for the full response (~2-5s) before sending anything to the client. Streaming tokens as they arrive reduces perceived latency to ~200ms (time to first token).

**Modify:** `ClaudeClient.kt` — add streaming method:
```kotlin
interface ClaudeClient {
    suspend fun generateRecipe(prompt: String): String
    fun generateRecipeStream(prompt: String): Flow<String>
}
```

**Modify:** `ClaudeClientImplementation.kt` — implement streaming with `channelFlow`:
```kotlin
override fun generateRecipeStream(prompt: String): Flow<String> = channelFlow {
    semaphore.withPermit {
        withTimeout(timeoutMs) {
            httpClient.preparePost("https://api.anthropic.com/v1/messages") {
                header("x-api-key", apiKey)
                setBody(ClaudeRequest(model = model, maxTokens = 1024, stream = true, ...))
            }.execute { response ->
                val channel: ByteReadChannel = response.body()
                // Parse SSE lines and emit text deltas
                while (!channel.isClosedForRead) {
                    val line = channel.readUTF8Line() ?: break
                    parseSSEDelta(line)?.let { send(it) }
                }
            }
        }
    }
}
```

**Create:** `RecipeRoutes.kt` — add `GET /recipes/stream` SSE endpoint:
```kotlin
get("/stream") {
    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
        recipeService.searchRecipeStream(ingredients, restrictions)
            .collect { chunk ->
                write("data: $chunk\n\n")
                flush()
            }
    }
}
```

**Tests — create:** `ClaudeClientStreamTest.kt`
- Flow emits chunks in order
- Flow completes after last chunk
- Timeout cancels the flow
- Semaphore limits concurrent streams

**Verify:** `mvn test`

**Coroutine patterns demonstrated:**
- `channelFlow {}` — bridge between callback/channel-based API and Flow
- `Flow.collect {}` — consuming a cold stream
- Backpressure — Flow suspends producer when consumer is slow
- Cancellation propagation — closing SSE connection cancels the Flow and the HTTP call

---

### Step B2: Channel-Based Notification Queue

**Pattern:** `Channel`, producer-consumer, `consumeEach`
**Why genuine:** The current `scope.launch` approach has no backpressure — 10,000 registrations spawn 10,000 coroutines simultaneously. A Channel-based queue bounds concurrency and provides ordered processing.

**Modify:** `NotificationService.kt`:
```kotlin
class NotificationService(private val scope: CoroutineScope) {
    private val notificationChannel = Channel<NotificationEvent>(capacity = 100)

    init {
        // Single consumer processes notifications sequentially
        scope.launch {
            notificationChannel.consumeEach { event ->
                processNotification(event)
            }
        }
    }

    fun sendWelcomeNotification(userId: Int, email: String) {
        notificationChannel.trySend(NotificationEvent.Welcome(userId, email))
    }

    private suspend fun processNotification(event: NotificationEvent) {
        when (event) {
            is NotificationEvent.Welcome -> {
                delay(100) // simulate email API call
                logger.info("Welcome email sent to {} (userId={})", event.email, event.userId)
            }
        }
    }

    fun close() { notificationChannel.close() }
}
```

**Tests — update:** `NotificationServiceTest.kt`
- Channel buffers notifications (trySend succeeds up to capacity)
- Consumer processes in FIFO order
- Closing the channel stops the consumer
- Cancelling scope cancels the consumer coroutine

**Verify:** `mvn test`

**Coroutine patterns demonstrated:**
- `Channel(capacity)` — bounded producer-consumer queue
- `trySend` — non-blocking send (returns failure instead of suspending)
- `consumeEach` — consuming until channel is closed
- Backpressure — when buffer is full, `trySend` returns `ChannelResult.failure`

---

### Step B3: supervisorScope for Batch Recipe Search

**Pattern:** `supervisorScope`, partial failure handling
**Why genuine:** A "meal plan" feature could search for breakfast, lunch, and dinner recipes in parallel. If one fails (e.g., Claude timeout for a complex recipe), the others should still return.

**Create:** `RecipeService.kt` — add batch search:
```kotlin
suspend fun searchBatch(
    requests: List<RecipeSearchInput>
): List<RecipeResult> = supervisorScope {
    requests.map { input ->
        async {
            try {
                val response = searchRecipe(input.ingredients, input.dietaryRestrictions)
                RecipeResult.Success(input.label, response)
            } catch (e: Exception) {
                RecipeResult.Failure(input.label, e.message ?: "Unknown error")
            }
        }
    }.awaitAll()
}
```

**Create:** `RecipeRoutes.kt` — add `POST /recipes/batch`:
```kotlin
post("/batch") {
    val request = call.receive<BatchSearchRequest>()
    val results = recipeService.searchBatch(request.recipes)
    call.respond(HttpStatusCode.OK, BatchSearchResponse(results))
}
```

**Tests — create:** `RecipeServiceBatchTest.kt`
- All succeed — returns 3 success results
- One fails — returns 2 success + 1 failure (no cancellation of siblings)
- Timeout on one — other results still returned

**Verify:** `mvn test`

**Coroutine patterns demonstrated:**
- `supervisorScope` — failure of one child doesn't cancel siblings
- Difference from `coroutineScope` (which cancels all children on first failure)
- `async` + `awaitAll` — waiting for all parallel results
- Exception isolation — each async catches its own exceptions

---

### Step B4: Retry with Exponential Backoff

**Pattern:** `delay` with computed intervals, suspend lambda retry
**Why genuine:** Claude API calls can fail transiently (429 rate limit, 503 overloaded). Retrying with backoff is a standard resilience pattern that uses coroutine `delay` (non-blocking sleep).

**Create:** `utils/RetryUtils.kt`:
```kotlin
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 500,
    maxDelayMs: Long = 5000,
    factor: Double = 2.0,
    retryOn: (Exception) -> Boolean = { true },
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            if (attempt == maxRetries - 1 || !retryOn(e)) throw e
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
        }
    }
    error("Unreachable")
}
```

**Modify:** `ClaudeClientImplementation.kt` — wrap API call with retry:
```kotlin
override suspend fun generateRecipe(prompt: String): String {
    return semaphore.withPermit {
        retryWithBackoff(
            maxRetries = 3,
            retryOn = { it is IOException || (it is ClientRequestException && it.response.status.value == 429) }
        ) {
            withTimeout(timeoutMs) { /* existing HTTP call */ }
        }
    }
}
```

**Tests — create:** `RetryUtilsTest.kt`
- Succeeds on first try — no delay
- Succeeds on third try — delays increase exponentially
- Exceeds max retries — throws original exception
- Non-retryable exception — throws immediately
- Cancellation during delay — throws CancellationException (no retry)

**Verify:** `mvn test`

**Coroutine patterns demonstrated:**
- `delay()` — non-blocking sleep (unlike `Thread.sleep`, doesn't block the thread)
- Cancellation-aware retry — `delay` is a suspension point that respects cancellation
- Composing suspend functions — retry wraps any suspend lambda

---

### Step B5: SharedFlow Event Bus

**Pattern:** `MutableSharedFlow`, `SharedFlow`, event broadcasting
**Why genuine:** Multiple parts of the app care about events (user registered, recipe searched, etc.). A SharedFlow-based event bus decouples producers from consumers without tight coupling.

**Create:** `services/EventBus.kt`:
```kotlin
sealed class AppEvent {
    data class UserRegistered(val userId: Int, val email: String) : AppEvent()
    data class RecipeSearched(val userId: Int, val query: String, val cached: Boolean) : AppEvent()
}

class EventBus {
    private val _events = MutableSharedFlow<AppEvent>(
        extraBufferCapacity = 64
    )
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }
}
```

**Modify:** `WhatToEat.kt` — create EventBus, pass to services
**Modify:** `AuthRoutes.kt` — emit `UserRegistered` after registration
**Modify:** `NotificationService.kt` — subscribe to `UserRegistered` events:
```kotlin
init {
    scope.launch {
        eventBus.events
            .filterIsInstance<AppEvent.UserRegistered>()
            .collect { event ->
                sendWelcomeEmail(event.userId, event.email)
            }
    }
}
```

**Tests — create:** `EventBusTest.kt`
- Multiple subscribers receive the same event
- Slow subscriber doesn't block fast subscriber (`extraBufferCapacity`)
- Events emitted before subscription are not received (hot stream behavior)
- Scope cancellation stops collection

**Verify:** `mvn test`

**Coroutine patterns demonstrated:**
- `MutableSharedFlow` — hot stream, multiple collectors
- `SharedFlow` — read-only view for consumers
- `filterIsInstance` — type-safe event filtering
- Hot vs cold streams — SharedFlow is hot (events fire regardless of collectors)

---

### Step B6: StateFlow-Based Rate Limiter Dashboard

**Pattern:** `MutableStateFlow`, `StateFlow`, state observation
**Why genuine:** Expose real-time metrics (current Claude API concurrency, queue depth, cache hit rate) via a `/metrics` endpoint. `StateFlow` holds the latest state and new collectors immediately get the current value.

**Create:** `services/MetricsService.kt`:
```kotlin
data class AppMetrics(
    val claudeActiveRequests: Int = 0,
    val claudeQueuedRequests: Int = 0,
    val cacheHitRate: Double = 0.0,
    val notificationQueueDepth: Int = 0
)

class MetricsService {
    private val _metrics = MutableStateFlow(AppMetrics())
    val metrics: StateFlow<AppMetrics> = _metrics.asStateFlow()

    fun updateClaudeMetrics(active: Int, queued: Int) {
        _metrics.update { it.copy(claudeActiveRequests = active, claudeQueuedRequests = queued) }
    }
    // ... other update methods
}
```

**Modify:** `GenericRoute.kt` — add `GET /metrics`:
```kotlin
get("/metrics") {
    call.respond(metricsService.metrics.value)
}
```

**Tests — create:** `MetricsServiceTest.kt`
- `StateFlow` always has a current value (initial state)
- Updates are conflated (only latest state is observed)
- Multiple readers see same state

**Verify:** `mvn test`

**Coroutine patterns demonstrated:**
- `MutableStateFlow` — conflated state holder (always has a value)
- `StateFlow.value` — synchronous read of current state
- `.update {}` — atomic state mutation
- StateFlow vs SharedFlow — StateFlow conflates (keeps only latest), SharedFlow buffers

---

## Coroutine Pattern Coverage (Complete)

### Already Implemented (Steps 0–5)

| Pattern | Location | Genuine? |
|---------|----------|----------|
| `withContext(Dispatchers.Default)` | PasswordUtils | Yes — CPU offload |
| `coroutineScope { async {} }` | UserRepositoryImpl, AuthRoutes | Yes — parallel I/O |
| `scope.launch` | NotificationService | Yes — fire-and-forget |
| `async` + `withTimeoutOrNull` | HealthService | Yes — resilient checks |
| `withTimeout` | ClaudeClientImpl | Yes — hard deadline |
| `Semaphore` | ClaudeClientImpl | Yes — rate limiting |
| `Mutex` | RecipeCache | Yes — stampede prevention |
| `newSuspendedTransaction` | Repositories | Yes — async DB access |
| `runTest` / `advanceUntilIdle` | Tests | Yes — coroutine testing |

### New (This Plan)

| Pattern | Step | Genuine? |
|---------|------|----------|
| `channelFlow` + `Flow.collect` | B1 — Recipe streaming | Yes — reduces perceived latency from ~3s to ~200ms |
| `Channel` + `consumeEach` | B2 — Notification queue | Yes — bounded concurrency, ordered processing |
| `supervisorScope` | B3 — Batch recipe search | Yes — partial failure isolation |
| `delay` in retry loop | B4 — Exponential backoff | Yes — resilient external calls |
| `MutableSharedFlow` / `SharedFlow` | B5 — Event bus | Yes — decoupled event broadcasting |
| `MutableStateFlow` / `StateFlow` | B6 — Metrics | Yes — real-time observable state |

---

## Files Summary

### Part A (Fixes)

**Modify (5):** `WhatToEat.kt`, `AuthRoutes.kt`, `RecipeCache.kt`, `RecipeService.kt`, `HealthService.kt`
**Update tests (3):** `AuthRoutesTest.kt`, `RecipeCacheTest.kt`, `NotificationServiceTest.kt`
**Create tests (1):** `RecipeServiceTest.kt`

### Part B (New Features)

**Modify (4):** `ClaudeClient.kt`, `ClaudeClientImplementation.kt`, `NotificationService.kt`, `GenericRoute.kt`
**Create (4):** `RetryUtils.kt`, `EventBus.kt`, `MetricsService.kt`, streaming SSE route additions
**Create tests (5):** `ClaudeClientStreamTest.kt`, `RecipeServiceBatchTest.kt`, `RetryUtilsTest.kt`, `EventBusTest.kt`, `MetricsServiceTest.kt`

---

## Verification

After each step: `mvn test` — all tests pass.

Final:
1. `mvn test` — full suite green
2. Manual: `curl POST /recipes/stream` — SSE events arrive in real-time
3. Manual: `curl POST /recipes/batch` — partial failures return mixed results
4. Manual: `curl GET /metrics` — real-time metrics JSON
