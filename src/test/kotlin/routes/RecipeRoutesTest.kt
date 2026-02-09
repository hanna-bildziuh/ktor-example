package routes

import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.dto.LoginRequest
import models.dto.RecipeSearchRequest
import models.dto.RegisterRequest
import services.claude.FakeClaudeClient
import testModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecipeRoutesTest {

    private val recipeJson = """
        {
          "title": "Chicken Rice Bowl",
          "ingredients": ["2 chicken breasts", "1 cup rice"],
          "instructions": "1. Cook rice. 2. Grill chicken.",
          "preparation_time": "30 minutes",
          "servings": 2
        }
    """.trimIndent()

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(email: String): String {
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = "MyPass1!"))
        }
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email, password = "MyPass1!"))
        }
        val body = Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject
        return body["access_token"]!!.jsonPrimitive.content
    }

    @Test
    fun `returns 401 without JWT token`() = testApplication {
        application { testModule(FakeClaudeClient(recipeJson)) }
        val client = jsonClient()

        val response = client.post("/recipes/search") {
            contentType(ContentType.Application.Json)
            setBody(RecipeSearchRequest(ingredients = listOf("chicken")))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `returns 400 for empty ingredients`() = testApplication {
        application { testModule(FakeClaudeClient(recipeJson)) }
        val token = registerAndLogin("recipe-400@example.com")
        val client = jsonClient()

        val response = client.post("/recipes/search") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(RecipeSearchRequest(ingredients = emptyList()))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `returns 200 with valid token and ingredients`() = testApplication {
        application { testModule(FakeClaudeClient(recipeJson)) }
        val token = registerAndLogin("recipe-200@example.com")
        val client = jsonClient()

        val response = client.post("/recipes/search") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(RecipeSearchRequest(ingredients = listOf("chicken", "rice")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["title"]?.jsonPrimitive?.content)
        assertEquals("Chicken Rice Bowl", body["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `second request returns cached true`() = testApplication {
        application { testModule(FakeClaudeClient(recipeJson)) }
        val token = registerAndLogin("recipe-cached@example.com")
        val client = jsonClient()

        val request = RecipeSearchRequest(ingredients = listOf("chicken", "rice"))

        client.post("/recipes/search") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(request)
        }

        val response = client.post("/recipes/search") {
            contentType(ContentType.Application.Json)
            bearerAuth(token)
            setBody(request)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["cached"]?.jsonPrimitive?.content.toBoolean())
    }
}
