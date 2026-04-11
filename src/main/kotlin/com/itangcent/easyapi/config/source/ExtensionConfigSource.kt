package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.extension.ExtensionConfigRegistry

/**
 * Configuration source for extension configurations.
 *
 * Provides configuration for various framework extensions
 * (Swagger, Jackson, Spring, etc.) based on user selection.
 *
 * This source has priority 3, higher than recommend but lower than built-in.
 *
 * @param selectedCodes The selected extension codes, or null for defaults
 * @param configTextParser Parser for configuration text
 */
class ExtensionConfigSource(
    private val selectedCodes: Array<String>?,
    private val configTextParser: ConfigTextParser
) : ConfigSource {
    override val priority: Int = 3
    override val sourceId: String = "extension"

    /**
     * Collects configuration entries from selected extensions.
     *
     * This method builds configuration text from extension codes and parses it into config entries.
     *
     * @return Sequence of parsed config entries, or empty sequence if no config is available
     */
    override suspend fun collect(): Sequence<ConfigEntry> {
        val config = ExtensionConfigRegistry.buildConfig(selectedCodes ?: emptyArray())
        if (config.isBlank()) return emptySequence()
        return configTextParser.parse(config, sourceId, null)
    }
}
