package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.ConfigTextParser

/**
 * Configuration source for built-in default configuration.
 *
 * Loads configuration from a bundled resource file (`config/builtin.easyapi.config`)
 * or from custom text provided at construction.
 *
 * This source has the lowest priority (1) and provides default values
 * that can be overridden by other sources.
 *
 * @param enabled Whether to load built-in configuration
 * @param configTextParser Parser for configuration text
 * @param customText Optional custom configuration text (overrides resource)
 * @param resourcePath Path to the built-in configuration resource
 */
class BuiltInConfigSource(
    private val enabled: Boolean,
    private val configTextParser: ConfigTextParser,
    private val customText: String? = null,
    private val resourcePath: String = "config/builtin.easyapi.config"
) : ConfigSource {
    override val priority: Int = 1
    override val sourceId: String = "builtin"

    override suspend fun collect(): Sequence<ConfigEntry> {
        if (!enabled) return emptySequence()
        val content = customText?.takeIf { it.isNotBlank() }
            ?: javaClass.classLoader.getResourceAsStream(resourcePath)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
            ?: return emptySequence()
        return configTextParser.parse(content, sourceId, null)
    }
}
