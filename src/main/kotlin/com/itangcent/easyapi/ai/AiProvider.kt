package com.itangcent.easyapi.ai

/**
 * Supported AI providers for the rule-generation assistant.
 *
 * The list mirrors the most popular providers supported by
 * [LiteLLM](https://docs.litellm.ai/docs/providers). The vast majority of these
 * expose an OpenAI-compatible Chat Completions endpoint, so they are served by
 * the OpenAI client in [AIServiceFactory] with a provider-specific [defaultBaseUrl].
 * Providers with a bespoke SDK (Anthropic, Gemini, Azure, Ollama) are handled
 * by their dedicated LangChain4j model in the factory.
 *
 * @property displayName Human-readable name for the settings combo box.
 * @property defaultBaseUrl Default API base URL, or `null` if the provider
 * SDK handles the endpoint internally (Anthropic, Gemini, Azure).
 * @property defaultModel Default model identifier, or `null` if none.
 * @property requiresApiKey Whether an API key is mandatory to use this provider.
 * @property openAiCompatible Whether the provider speaks the OpenAI
 * Chat Completions protocol and can be served by the OpenAI client.
 * @property contextWindow Approximate context window (in tokens) of the
 * provider's [defaultModel], used to derive the agent's token budget (see
 * `MessageTokens.contextWindowToBudget`). These are conservative defaults
 * for each provider's *typical* model; a per-model lookup table could refine
 * them in future. When in doubt we err low: underestimating is safe (the
 * agent just sends less history), while overestimating risks a provider 400,
 * so unknown/local hosts lean conservative.
 */
enum class AiProvider(
    val displayName: String,
    val defaultBaseUrl: String?,
    val defaultModel: String?,
    val requiresApiKey: Boolean,
    val openAiCompatible: Boolean = false,
    val contextWindow: Int = DEFAULT_CONTEXT_WINDOW
) {
    // --- Providers with a dedicated LangChain4j SDK ---
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-5.4-mini", true, openAiCompatible = true, contextWindow = 128_000),
    ANTHROPIC("Anthropic", null, "claude-haiku-4-5", true, contextWindow = 200_000),
    GEMINI("Google Gemini", null, "gemini-3.5-flash", true, contextWindow = 1_000_000),
    AZURE_OPENAI("Azure OpenAI", null, null, true, contextWindow = 128_000),
    OLLAMA("Ollama (local)", "http://localhost:11434/v1", "llama3", false, contextWindow = 8_192),

    // --- Popular OpenAI-compatible providers (served by the OpenAI client) ---
    AZURE_AI("Azure AI Foundry", "https://models.inference.ai.azure.com", "gpt-5.4-mini", true, openAiCompatible = true, contextWindow = 128_000),
    GITHUB_MODELS("GitHub Models", "https://models.github.ai/inference", "gpt-5.4-mini", true, openAiCompatible = true, contextWindow = 128_000),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-5.4-mini", true, openAiCompatible = true, contextWindow = 128_000),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", true, openAiCompatible = true, contextWindow = 128_000),
    GROQ("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true, openAiCompatible = true, contextWindow = 128_000),
    MISTRAL("Mistral AI", "https://api.mistral.ai/v1", "mistral-small-latest", true, openAiCompatible = true, contextWindow = 32_000),
    XAI("xAI (Grok)", "https://api.x.ai/v1", "grok-4.3", true, openAiCompatible = true, contextWindow = 128_000),
    TOGETHER_AI("Together AI", "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo", true, openAiCompatible = true, contextWindow = 128_000),
    FIREWORKS_AI("Fireworks AI", "https://api.fireworks.ai/inference/v1", "accounts/fireworks/models/llama-v3p1-70b-instruct", true, openAiCompatible = true, contextWindow = 128_000),
    DEEPINFRA("DeepInfra", "https://api.deepinfra.com/v1/openai", "meta-llama/Meta-Llama-3.1-70B-Instruct", true, openAiCompatible = true, contextWindow = 128_000),
    PERPLEXITY("Perplexity AI", "https://api.perplexity.ai", "sonar", true, openAiCompatible = true, contextWindow = 128_000),
    CEREBRAS("Cerebras", "https://api.cerebras.ai/v1", "llama-3.3-70b", true, openAiCompatible = true, contextWindow = 128_000),
    MOONSHOT("Moonshot AI", "https://api.moonshot.cn/v1", "moonshot-v1-8k", true, openAiCompatible = true, contextWindow = 8_192),
    ZHIPU("Z.AI (Zhipu AI)", "https://api.z.ai/api/paas/v4", "glm-4-flash", true, openAiCompatible = true, contextWindow = 128_000),
    NEBIUS("Nebius AI Studio", "https://api.studio.nebius.com/v1", "meta-llama/Meta-Llama-3.1-70B-Instruct", true, openAiCompatible = true, contextWindow = 128_000),
    SAMBANOVA("SambaNova", "https://api.sambanova.ai/v1", "Meta-Llama-3.1-70B-Instruct", true, openAiCompatible = true, contextWindow = 128_000),

    // --- Local OpenAI-compatible servers ---
    LM_STUDIO("LM Studio (local)", "http://localhost:1234/v1", null, false, openAiCompatible = true, contextWindow = 8_192),

    // --- Generic escape hatch (e.g. LiteLLM proxy or any OpenAI-compatible host) ---
    CUSTOM("Custom (OpenAI-compatible)", "http://localhost:4000/v1", null, false, openAiCompatible = true, contextWindow = 128_000);

    companion object {
        /**
         * Fallback context window for providers whose model is unknown or
         * user-supplied. Defaults to a 128k-class assumption.
         */
        const val DEFAULT_CONTEXT_WINDOW: Int = 128_000
    }
}
