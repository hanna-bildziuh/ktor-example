package routes

import io.ktor.server.application.call
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.configureGenericRoutes() {
    get("/") {
        call.respondText("Welcome to WhatToEat API! Visit /swagger for API documentation.")
    }

    get("/health") {
        call.respondText("OK")
    }

    // Swagger UI endpoint - serves interactive API documentation
    swaggerUI(path = "swagger", swaggerFile = "openapi.yaml") {
        version = "5.10.3"
    }
}