package com.itangcent.easyapi.config

import com.intellij.openapi.components.service

/**
 * Interface for reading configuration values.
 *
 * Configuration can come from multiple sources:
 * - .easy.api.config file in the project
 * - Remote configuration URLs
 * - Built-in default configuration
 *
 * Implementations:
 * - [DefaultConfigReader] - Single source reader
 * - [LayeredConfigReader] - Merges multiple sources
 *
 * ## Usage
 * ```kotlin
 * val configReader = ConfigReader.getInstance(project)
 * 
 * // Get first value for a key
 * val server = configReader.getFirst("server")
 * 
 * // Get all values for a key
 * val tokens = configReader.getAll("token")
 * 
 * // Iterate all config
 * configReader.foreach { key, value ->
 *     println("$key = $value")
 * }
 * ```
 *
 * ## Important: Do NOT cache the instance
 *
 * Tests that share a light project fixture rely on `registerServiceInstance` to swap
 * in different `ConfigReader` implementations per test class. Caching the instance
 * in a `by lazy` property or a long-lived field causes later tests to see a stale
 * reference from an earlier test. Use a computed property (`get()`) instead:
 *
 * ```kotlin
 * // WRONG – captures a stale reference across test runs
 * private val configReader: ConfigReader by lazy { ConfigReader.getInstance(project) }
 *
 * // CORRECT – always resolves the current instance
 * private val configReader: ConfigReader get() = ConfigReader.getInstance(project)
 * ```
 *
 * @see LayeredConfigReader for layered configuration
 * @see ConfigProvider for configuration management
 */
interface ConfigReader {
    /**
     * Gets the first value for the given key.
     *
     * @param key The configuration key
     * @return The first value, or null if not found
     */
    fun getFirst(key: String): String?

    /**
     * Gets all values for the given key.
     *
     * Some configuration keys can have multiple values.
     *
     * @param key The configuration key
     * @return List of all values for the key
     */
    fun getAll(key: String): List<String>

    /**
     * Reloads configuration from sources.
     */
    suspend fun reload()
    
    /**
     * Iterates over all configuration entries.
     *
     * @param action The action to perform for each entry
     */
    fun foreach(action: (String, String) -> Unit) {
        foreach({ true }, action)
    }
    
    /**
     * Iterates over configuration entries matching the filter.
     *
     * @param keyFilter Filter function for keys
     * @param action The action to perform for each matching entry
     */
    fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit)

    companion object {
        fun getInstance(project: com.intellij.openapi.project.Project): ConfigReader =
            project.service<ConfigReader>()
    }
}

internal val DOLLAR_BRACE_PATTERN = Regex("\\$\\{([^}]+)}")
internal val DOUBLE_BRACE_PATTERN = Regex("\\{\\{([^}]+)}}")

fun ConfigReader.resolveVariables(input: String): String {
    var result = DOLLAR_BRACE_PATTERN.replace(input) { match ->
        val key = match.groupValues[1].trim()
        getFirst(key) ?: match.value
    }
    result = DOUBLE_BRACE_PATTERN.replace(result) { match ->
        val key = match.groupValues[1].trim()
        getFirst(key) ?: match.value
    }
    return result
}

