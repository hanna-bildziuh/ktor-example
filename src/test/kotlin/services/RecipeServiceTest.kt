package services

import kotlinx.coroutines.test.runTest
import services.claude.FakeClaudeClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RecipeServiceTest {

    private val validRecipeJson = """
        {
          "title": "Chicken Rice Bowl",
          "ingredients": ["2 chicken breasts", "1 cup rice"],
          "instructions": "1. Cook rice. 2. Grill chicken. 3. Combine.",
          "preparation_time": "30 minutes",
          "servings": 2
        }
    """.trimIndent()

    private fun createService(
        fakeResponse: String = validRecipeJson
    ): Pair<RecipeService, FakeClaudeClient> {
        val fakeClient = FakeClaudeClient(fakeResponse)
        val cache = RecipeCache()
        val service = RecipeService(fakeClient, cache)
        return service to fakeClient
    }

    @Test
    fun `buildCacheKey normalizes and sorts`() {
        val (service, _) = createService()

        val key1 = service.buildCacheKey(listOf("Rice", "chicken"), emptyList())
        val key2 = service.buildCacheKey(listOf("chicken", "Rice"), emptyList())

        assertEquals(key1, key2)
    }

    @Test
    fun `buildCacheKey includes dietary restrictions`() {
        val (service, _) = createService()

        val key1 = service.buildCacheKey(listOf("chicken"), listOf("Gluten-Free", "dairy-free"))
        val key2 = service.buildCacheKey(listOf("chicken"), listOf("dairy-free", "Gluten-Free"))

        assertEquals(key1, key2)
    }

    @Test
    fun `buildPrompt includes ingredients and restrictions`() {
        val (service, _) = createService()

        val prompt = service.buildPrompt(listOf("chicken", "rice"), listOf("gluten-free"))

        assertTrue(prompt.contains("chicken"))
        assertTrue(prompt.contains("rice"))
        assertTrue(prompt.contains("gluten-free"))
    }

    @Test
    fun `buildPrompt excludes restrictions line when none provided`() {
        val (service, _) = createService()

        val prompt = service.buildPrompt(listOf("chicken"), emptyList())

        assertFalse(prompt.contains("Dietary restrictions"))
    }

    @Test
    fun `returns parsed RecipeResponse from Claude JSON`() = runTest {
        val (service, _) = createService()

        val result = service.searchRecipe(listOf("chicken", "rice"), emptyList())

        assertEquals("Chicken Rice Bowl", result.title)
        assertEquals(listOf("2 chicken breasts", "1 cup rice"), result.ingredients)
        assertEquals("1. Cook rice. 2. Grill chicken. 3. Combine.", result.instructions)
        assertEquals("30 minutes", result.preparationTime)
        assertEquals(2, result.servings)
        assertFalse(result.cached!!)
    }

    @Test
    fun `cached is false on first call and true on second`() = runTest {
        val (service, fakeClient) = createService()

        val first = service.searchRecipe(listOf("chicken", "rice"), emptyList())
        val second = service.searchRecipe(listOf("chicken", "rice"), emptyList())

        assertFalse(first.cached!!)
        assertTrue(second.cached!!)
        assertEquals(1, fakeClient.callCount.get())
    }

    @Test
    fun `handles non-JSON Claude response gracefully`() = runTest {
        val (service, _) = createService("Here is a nice chicken recipe with detailed steps...")

        val result = service.searchRecipe(listOf("chicken"), emptyList())

        assertNotNull(result.title)
        assertEquals("Recipe", result.title)
        assertTrue(result.instructions!!.contains("chicken recipe"))
        assertFalse(result.cached!!)
    }

    @Test
    fun `handles markdown code fences in Claude response`() = runTest {
        val wrappedJson = "```json\n$validRecipeJson\n```"
        val (service, _) = createService(wrappedJson)

        val result = service.searchRecipe(listOf("chicken", "rice"), emptyList())

        assertEquals("Chicken Rice Bowl", result.title)
        assertEquals(2, result.servings)
    }
}
