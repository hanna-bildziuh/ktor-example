package routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HealthRouteTest {

    @Test
    fun `GET health returns 200 with JSON content type`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.contentType()?.match(ContentType.Application.Json) == true)
    }

    @Test
    fun `GET health returns status and components`() = testApplication {
        application { module() }
        val response = client.get("/health")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals("up", body["status"]?.jsonPrimitive?.content)
        val components = body["components"]?.jsonObject
        assertNotNull(components)
        assertNotNull(components["database"])
        assertNotNull(components["jwt"])
    }

    @Test
    fun `GET health components include status and responseTimeMs`() = testApplication {
        application { module() }
        val response = client.get("/health")
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val db = body["components"]?.jsonObject?.get("database")?.jsonObject

        assertNotNull(db)
        assertEquals("up", db["status"]?.jsonPrimitive?.content)
        assertNotNull(db["responseTimeMs"])
    }
}