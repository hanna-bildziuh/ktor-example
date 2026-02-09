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
import models.dto.RegisterRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class UserRoutesTest {

    private fun ApplicationTestBuilder.jsonClient() = createClient {
        install(ContentNegotiation) { json() }
    }

    private suspend fun ApplicationTestBuilder.registerAndLogin(
        email: String = "profile@example.com",
        password: String = "MyPass1!"
    ): String {
        val client = jsonClient()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = password))
        }
        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(email = email, password = password))
        }
        val body = Json.parseToJsonElement(loginResponse.bodyAsText()).jsonObject
        return body["access_token"]!!.jsonPrimitive.content
    }

    @Test
    fun `GET profile returns 401 without token`() = testApplication {
        application { module() }
        val response = client.get("/user/profile")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET profile returns 401 with invalid token`() = testApplication {
        application { module() }
        val response = client.get("/user/profile") {
            header(HttpHeaders.Authorization, "Bearer invalid.token.value")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `GET profile succeeds with valid access token`() = testApplication {
        application { module() }
        val accessToken = registerAndLogin()
        val client = jsonClient()
        val response = client.get("/user/profile") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("profile@example.com", body["email"]?.jsonPrimitive?.content)
    }
}
