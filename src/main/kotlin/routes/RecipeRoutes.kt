package routes

import exceptions.ValidationException
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import models.dto.RecipeSearchRequest
import services.RecipeService

fun Route.configureRecipeRoutes(recipeService: RecipeService) {
    route("/recipes") {
        authenticate("auth-jwt") {
            post("/search") {
                val request = call.receive<RecipeSearchRequest>()

                if (request.ingredients.isEmpty()) {
                    throw ValidationException("Ingredients list must not be empty")
                }

                val response = recipeService.searchRecipe(
                    ingredients = request.ingredients,
                    dietaryRestrictions = request.dietaryRestrictions.orEmpty()
                )
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}
