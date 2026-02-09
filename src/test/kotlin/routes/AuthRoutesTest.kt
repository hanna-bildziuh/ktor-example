package routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import module
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import models.dto.LoginRequest
import models.dto.RefreshTokenRequest
import models.dto.RegisterRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AuthRoutesTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    @Test
    fun `POST register succeeds with valid data`() = testApplication {
        application { module() }
        val client = jsonClient()
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "new@example.com", password = "MyPass1!"))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("new@example.com", body["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST register returns 400 for invalid email`() = testApplication {
        application { module() }
        val client = jsonClient()
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "bademail", password = "MyPass1!"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST register returns 400 for weak password`() = testApplication {
        application { module() }
        val client = jsonClient()
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "user@example.com", password = "short"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST register returns 409 for duplicate email`() = testApplication {
        application { module() }
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "dup@example.com", password = "MyPass1!"))
        }
        val response = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "dup@example.com", password = "MyPass1!"))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `POST login succeeds with valid credentials`() = testApplication {
        application { module() }
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "login@example.com", password = "MyPass1!"))
        }
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "login@example.com", password = "MyPass1!"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["access_token"]?.jsonPrimitive?.content)
        assertNotNull(body["refresh_token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST login returns 401 for wrong password`() = testApplication {
        application { module() }
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "wrong@example.com", password = "MyPass1!"))
        }
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "wrong@example.com", password = "WrongPass1!"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST login returns 401 for nonexistent user`() = testApplication {
        application { module() }
        val client = jsonClient()
        val response = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "ghost@example.com", password = "MyPass1!"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST refresh returns new tokens for valid refresh token`() = testApplication {
        application { module() }
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = "refresh@example.com", password = "MyPass1!"))
        }
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = "refresh@example.com", password = "MyPass1!"))
        }
        val loginBody = Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject
        val refreshToken = loginBody["refresh_token"]!!.jsonPrimitive.content

        // Wait so the new JWT gets a different exp timestamp (second-level precision)
        Thread.sleep(1000)

        val response = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken = refreshToken))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertNotNull(body["access_token"]?.jsonPrimitive?.content)
        assertNotNull(body["refresh_token"]?.jsonPrimitive?.content)
    }

    @Test
    fun `POST refresh returns 401 for invalid token`() = testApplication {
        application { module() }
        val client = jsonClient()
        val response = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken = "invalid.token.here"))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
