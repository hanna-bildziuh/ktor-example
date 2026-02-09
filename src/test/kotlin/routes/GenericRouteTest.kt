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

class GenericRouteTest {

    @Test
    fun `GET root returns welcome message`() = testApplication {
        application { module() }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Welcome to WhatToEat API! Visit /swagger for API documentation.", response.bodyAsText())
    }

    @Test
    fun `GET health returns structured JSON with status up`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("up", body["status"]?.jsonPrimitive?.content)
    }
}
