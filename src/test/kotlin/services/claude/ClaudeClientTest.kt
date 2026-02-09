package services.claude

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class FakeClaudeClient(
    private val response: String = "Fake recipe response"
) : ClaudeClient {
    var lastPrompt: String? = null
        private set
    var callCount = AtomicInteger(0)
        private set

    override suspend fun generateRecipe(prompt: String): String {
        lastPrompt = prompt
        callCount.incrementAndGet()
        return response
    }
}

class ClaudeClientTest {

    @Test
    fun `fake client returns expected text`() = runTest {
        val fake = FakeClaudeClient("test recipe")

        val result = fake.generateRecipe("make a salad")

        assertEquals("test recipe", result)
        assertEquals("make a salad", fake.lastPrompt)
        assertEquals(1, fake.callCount.get())
    }

    @Test
    fun `withTimeout throws TimeoutCancellationException when API is slow`() = runTest {
        val slowEngine = MockEngine { _ ->
            delay(5_000)
            respond(
                content = """{"content":[{"type":"text","text":"slow"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = ClaudeClientImplementation(
            httpClient = mockHttpClient(slowEngine),
            apiKey = "test-key",
            timeoutMs = 100
        )

        assertFailsWith<TimeoutCancellationException> {
            client.generateRecipe("test prompt")
        }
    }

    @Test
    fun `semaphore limits concurrent requests`() = runTest {
        val currentConcurrency = AtomicInteger(0)
        val maxObservedConcurrency = AtomicInteger(0)

        val trackingEngine = MockEngine { _ ->
            val current = currentConcurrency.incrementAndGet()
            maxObservedConcurrency.updateAndGet { max -> maxOf(max, current) }
            delay(200)
            currentConcurrency.decrementAndGet()
            respond(
                content = """{"content":[{"type":"text","text":"recipe"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = ClaudeClientImplementation(
            httpClient = mockHttpClient(trackingEngine),
            apiKey = "test-key",
            maxConcurrentRequests = 2,
            timeoutMs = 10_000
        )

        withContext(Dispatchers.Default) {
            coroutineScope {
                val jobs = (1..5).map { i ->
                    async { client.generateRecipe("prompt $i") }
                }
                jobs.forEach { it.await() }
            }
        }

        assertTrue(
            maxObservedConcurrency.get() <= 2,
            "Expected max concurrency <= 2, but was ${maxObservedConcurrency.get()}"
        )
    }

    @Test
    fun `successful API call returns text content`() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = """{"content":[{"type":"text","text":"Chicken stir fry recipe"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = ClaudeClientImplementation(
            httpClient = mockHttpClient(engine),
            apiKey = "test-key"
        )

        val result = withContext(Dispatchers.Default) {
            client.generateRecipe("make chicken stir fry")
        }

        assertEquals("Chicken stir fry recipe", result)
    }

    private fun mockHttpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
}