package services.claude

interface ClaudeClient {
    suspend fun generateRecipe(prompt: String): String
}