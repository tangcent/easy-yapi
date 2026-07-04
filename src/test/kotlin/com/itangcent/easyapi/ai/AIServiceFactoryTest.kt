package com.itangcent.easyapi.ai

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class AIServiceFactoryTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testCreateOpenAI() {
        val service = AIServiceFactory.create(
            AiRuntimeConfig(
                provider = AiProvider.OPENAI,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "test-key",
                model = "gpt-4o-mini",
                requestTimeoutSec = 30,
                maxRequests = 8
            )
        )
        assertNotNull("factory should produce a service", service)
        assertTrue("should be LangChain4jAIService", service is LangChain4jAIService)
    }

    fun testCreateAnthropic() {
        val service = AIServiceFactory.create(
            AiRuntimeConfig(
                provider = AiProvider.ANTHROPIC,
                baseUrl = "",
                apiKey = "test-key",
                model = "claude-3-5-haiku-latest",
                requestTimeoutSec = 30,
                maxRequests = 8
            )
        )
        assertNotNull(service)
        assertTrue(service is LangChain4jAIService)
    }

    fun testCreateGemini() {
        val service = AIServiceFactory.create(
            AiRuntimeConfig(
                provider = AiProvider.GEMINI,
                baseUrl = "",
                apiKey = "test-key",
                model = "gemini-1.5-flash",
                requestTimeoutSec = 30,
                maxRequests = 8
            )
        )
        assertNotNull(service)
        assertTrue(service is LangChain4jAIService)
    }

    fun testCreateOllama() {
        val service = AIServiceFactory.create(
            AiRuntimeConfig(
                provider = AiProvider.OLLAMA,
                baseUrl = "http://localhost:11434/v1",
                apiKey = "",
                model = "llama3",
                requestTimeoutSec = 30,
                maxRequests = 8
            )
        )
        assertNotNull(service)
        assertTrue(service is LangChain4jAIService)
    }

    fun testCreateAzureOpenAI() {
        val service = AIServiceFactory.create(
            AiRuntimeConfig(
                provider = AiProvider.AZURE_OPENAI,
                baseUrl = "https://my-resource.openai.azure.com",
                apiKey = "test-key",
                model = "my-deployment",
                requestTimeoutSec = 30,
                maxRequests = 8
            )
        )
        assertNotNull(service)
        assertTrue(service is LangChain4jAIService)
    }

    fun testCreateCustomUsesDummyKey() {
        val service = AIServiceFactory.create(
            AiRuntimeConfig(
                provider = AiProvider.CUSTOM,
                baseUrl = "http://localhost:4000/v1",
                apiKey = "",
                model = "gpt-4o",
                requestTimeoutSec = 30,
                maxRequests = 8
            )
        )
        assertNotNull(service)
        assertTrue(service is LangChain4jAIService)
    }

    fun testCreateForProjectReturnsNullWhenUnconfigured() {
        val project = project
        val service = AIServiceFactory.createForProject(project)
        assertNull("should return null when AI not configured", service)
    }
}
