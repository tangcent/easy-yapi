package com.itangcent.easyapi.core.ai

/**
 * Provider-neutral AI service abstraction.
 *
 * All high-level plugin code depends only on this interface — never on
 * LangChain4j types or provider-specific classes.
 *
 * Implementations:
 * - [LangChain4jAIService] — the production implementation.
 * - Mock/fake implementations in tests.
 */
interface AIService {

    /**
     * Send a chat request to the LLM and await its response.
     *
     * Implementations should dispatch blocking SDK calls to [kotlinx.coroutines.Dispatchers.IO]
     * and enforce the configured request timeout.
     *
     * @param request The chat request (messages + tools).
     * @return The model's response.
     */
    suspend fun chat(request: AiChatRequest): AiChatResponse

    /**
     * Verify that the configured provider / API key / model are reachable.
     *
     * @return A [Result] containing a short success message on success,
     * or an exception on failure.
     */
    suspend fun testConnection(): Result<String>
}
