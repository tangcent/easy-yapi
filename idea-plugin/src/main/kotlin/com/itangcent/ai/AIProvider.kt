package com.itangcent.ai

/**
 * Enum representing the supported AI service providers and their models
 */
enum class AIProvider(val displayName: String, val models: List<AIModel>) {
    /**
     * OpenAI service
     */
    OPENAI(
        "OpenAI", listOf(
            AIModel("gpt-3.5-turbo", "GPT-3.5 Turbo"),
            AIModel("gpt-4", "GPT-4"),
            AIModel("gpt-4-turbo", "GPT-4 Turbo"),
            AIModel("o3-mini", "O3 Mini"),
            AIModel("o1", "O1"),
            AIModel("o1-mini", "O1 Mini"),
            AIModel("gpt-4o", "GPT-4o")
        )
    ),

    /**
     * DeepSeek service
     */
    DEEPSEEK(
        "DeepSeek", listOf(
            AIModel("deepseek-chat", "DeepSeek-V3"),
            AIModel("deepseek-reasoner", "DeepSeek-R1")
        )
    ),

    /**
     * Local LLM service
     */
    LOCALLM(
        "LocalLLM", emptyList()
    );

    companion object {
        /**
         * Get AIProvider by its display name (case-insensitive)
         */
        fun fromDisplayName(name: String?): AIProvider? {
            return entries.find { it.displayName.equals(name, ignoreCase = true) }
        }

        /**
         * Get default model for a specific AI provider
         */
        fun getDefaultModel(aiProvider: AIProvider): AIModel? {
            return aiProvider.models.firstOrNull()
        }
    }
}

/**
 * Data class representing an AI model
 * @param id The model identifier used in API requests
 * @param displayName The human-readable name for display in UI
 */
data class AIModel(val id: String, val displayName: String) 