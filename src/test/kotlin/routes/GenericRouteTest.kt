package routes

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
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
    fun `GET health returns OK`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }
}
