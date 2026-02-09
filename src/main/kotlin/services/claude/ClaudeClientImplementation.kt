package services.claude

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ClaudeMessage(
    val role: String,
    val content: String
)

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val messages: List<ClaudeMessage>
)

@Serializable
private data class ClaudeContentBlock(
    val type: String,
    val text: String = ""
)

@Serializable
private data class ClaudeResponse(
    val content: List<ClaudeContentBlock>
)

class ClaudeClientImplementation(
    private val httpClient: HttpClient = defaultHttpClient(),
    private val apiKey: String = System.getenv("ANTHROPIC_API_KEY") ?: "",
    private val model: String = "claude-haiku-4-5-20251001",
    private val maxConcurrentRequests: Int = 5,
    private val timeoutMs: Long = 30_000
) : ClaudeClient {

    private val semaphore = Semaphore(maxConcurrentRequests)

    override suspend fun generateRecipe(prompt: String): String {
        return semaphore.withPermit {
            withTimeout(timeoutMs) {
                val request = ClaudeRequest(
                    model = model,
                    maxTokens = 1024,
                    messages = listOf(ClaudeMessage(role = "user", content = prompt))
                )

                val response = httpClient.post("https://api.anthropic.com/v1/messages") {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                    setBody(request)
                }.body<ClaudeResponse>()

                response.content.firstOrNull { it.type == "text" }?.text
                    ?: throw RuntimeException("No text content in Claude response")
            }
        }
    }

    companion object {
        fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
        }
    }
}