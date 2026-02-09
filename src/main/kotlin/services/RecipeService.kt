package services

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import models.dto.RecipeResponse
import services.claude.ClaudeClient

@Serializable
private data class ParsedRecipe(
    val title: String = "",
    val ingredients: List<String> = emptyList(),
    val instructions: String = "",
    @SerialName("preparation_time")
    val preparationTime: String = "",
    val servings: Int = 1
)

class RecipeService(
    private val claudeClient: ClaudeClient,
    private val recipeCache: RecipeCache
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun searchRecipe(
        ingredients: List<String>,
        dietaryRestrictions: List<String>
    ): RecipeResponse {
        val cacheKey = buildCacheKey(ingredients, dietaryRestrictions)

        val cachedValue = recipeCache.get(cacheKey)
        if (cachedValue != null) {
            return parseRecipeResponse(cachedValue, cached = true)
        }

        val prompt = buildPrompt(ingredients, dietaryRestrictions)
        val result = recipeCache.getOrCompute(cacheKey) {
            claudeClient.generateRecipe(prompt)
        }

        return parseRecipeResponse(result, cached = false)
    }

    internal fun buildCacheKey(
        ingredients: List<String>,
        dietaryRestrictions: List<String>
    ): String {
        val normalizedIngredients = ingredients.map { it.lowercase().trim() }.sorted()
        val normalizedRestrictions = dietaryRestrictions
            .map { it.lowercase().trim() }
            .sorted()
        return "${normalizedIngredients.joinToString(",")}|${normalizedRestrictions.joinToString(",")}"
    }

    internal fun buildPrompt(
        ingredients: List<String>,
        dietaryRestrictions: List<String>
    ): String {
        val restrictionsText = if (dietaryRestrictions.isNotEmpty()) {
            "\nDietary restrictions: ${dietaryRestrictions.joinToString(", ")}"
        } else {
            ""
        }

        return """
            |Suggest a recipe using these ingredients: ${ingredients.joinToString(", ")}$restrictionsText
            |
            |Respond ONLY with a JSON object (no markdown, no code fences) with these fields:
            |{
            |  "title": "Recipe Name",
            |  "ingredients": ["ingredient 1 with quantity", "ingredient 2 with quantity"],
            |  "instructions": "Step-by-step instructions as a single string",
            |  "preparation_time": "estimated time",
            |  "servings": number
            |}
        """.trimMargin()
    }

    internal fun parseRecipeResponse(text: String, cached: Boolean): RecipeResponse {
        return try {
            val cleaned = text
                .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
                .replace(Regex("^```\\s*$", RegexOption.MULTILINE), "")
                .trim()
            val parsed = json.decodeFromString<ParsedRecipe>(cleaned)
            RecipeResponse(
                title = parsed.title,
                ingredients = parsed.ingredients,
                instructions = parsed.instructions,
                preparationTime = parsed.preparationTime,
                servings = parsed.servings,
                cached = cached
            )
        } catch (_: Exception) {
            RecipeResponse(
                title = "Recipe",
                instructions = text,
                cached = cached
            )
        }
    }
}
