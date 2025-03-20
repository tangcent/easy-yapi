package com.itangcent.ai

import com.itangcent.ai.AIServiceHealthChecker.isAvailable
import com.itangcent.common.logger.Log
import com.itangcent.http.HttpClient

/**
 * Utility class for discovering and validating LocalLLM servers.
 * This class attempts to find a working LocalLLM server by trying various common API endpoint suffixes.
 *
 * @property httpClient The HTTP client used for making requests
 * @property possibleSuffixes List of possible API endpoint suffixes to try
 */
class LocalLLMServerDiscoverer(
    private val httpClient: HttpClient,
    private val possibleSuffixes: List<String> = DEFAULT_SUFFIXES
) {
    companion object : Log() {
        private val DEFAULT_SUFFIXES = listOf(
            "/v1",
            "/api/v1",
            "/api",
            "/v1/api"
        )
    }

    /**
     * Attempts to discover a working LocalLLM server URL from a base URL.
     * The method will try the base URL first, then attempt various API endpoint suffixes.
     *
     * @param baseUrl The base URL to start searching from (e.g., "http://localhost:8000")
     * @return The working server URL if found, null otherwise
     */
    fun discoverServer(baseUrl: String): String? {
        val trimmedUrl = baseUrl.trimEnd('/')
        if (validateLocalLLMServer(trimmedUrl)) {
            LOG.debug("Found working server at base URL: $trimmedUrl")
            return trimmedUrl
        }

        // Try all possible suffixes
        for (suffix in possibleSuffixes) {
            if (baseUrl.endsWith(suffix)) {
                LOG.debug("Skipping suffix $suffix as it's already in the base URL")
                continue
            }
            val serverUrl = if (suffix.isEmpty()) trimmedUrl else "$trimmedUrl$suffix"
            if (validateLocalLLMServer(serverUrl)) {
                LOG.debug("Found working server at URL: $serverUrl")
                return serverUrl
            }
        }

        LOG.warn("No working LocalLLM server found for base URL: $baseUrl")
        return null
    }

    /**
     * Validates if a given URL points to a working LocalLLM server.
     * A server is considered working if it responds to health checks and supports the required API endpoints.
     *
     * @param serverUrl The URL to validate
     * @return true if the server is working, false otherwise
     */
    private fun validateLocalLLMServer(serverUrl: String): Boolean {
        try {
            val localLLMService = LocalLLMClient(
                serverUrl = serverUrl,
                modelName = "",
                httpClient = httpClient
            )
            return localLLMService.isAvailable()
        } catch (e: Exception) {
            LOG.debug("Server validation failed for $serverUrl: ${e.message}")
            return false
        }
    }
} 