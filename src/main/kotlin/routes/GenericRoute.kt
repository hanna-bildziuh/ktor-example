package routes

import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import services.HealthService

fun Route.configureGenericRoutes(healthService: HealthService) {
    get("/") {
        call.respondText("Welcome to WhatToEat API! Visit /swagger for API documentation.")
    }

    get("/health") {
        val status = healthService.checkHealth()
        call.respond(status)
    }

    // Swagger UI endpoint - serves interactive API documentation
    swaggerUI(path = "swagger", swaggerFile = "openapi.yaml") {
        version = "5.10.3"
    }
}