package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.recommend.RecommendPresetRegistry

/**
 * Configuration source for recommended preset configurations.
 *
 * Provides pre-built configuration presets for common frameworks and use cases.
 * Uses [RecommendPresetRegistry] to build configuration from preset codes.
 *
 * This source has priority 2, higher than built-in but lower than remote sources.
 *
 * @param selected The selected preset codes (comma-separated), or null for defaults
 * @param configTextParser Parser for configuration text
 * @param registry Optional custom preset registry
 */
class RecommendConfigSource(
    private val selected: String?,
    private val configTextParser: ConfigTextParser,
    private val registry: Map<String, String> = emptyMap()
) : ConfigSource {
    override val priority: Int = 2
    override val sourceId: String = "recommend"

    /**
     * Collects configuration entries from recommended presets.
     *
     * This method builds configuration text from preset codes and parses it into config entries.
     * The behavior depends on two factors:
     * 1. Whether [selected] is null/blank (use defaults) or contains specific preset codes
     * 2. Whether [registry] is empty (use built-in presets) or contains custom preset mappings
     *
     * Decision matrix:
     * ```
     *                    | registry.isEmpty()      | registry.isNotEmpty()
     * -------------------|-------------------------|---------------------------
     * selected is blank  | built-in defaults       | all custom configs
     * selected provided  | built-in by codes       | custom configs by codes
     * ```
     *
     * @return Sequence of parsed config entries, or empty sequence if no config is available
     */
    override suspend fun collect(): Sequence<ConfigEntry> {
        val config = if (selected.isNullOrBlank()) {
            // No preset selected - use default behavior
            if (registry.isEmpty()) {
                // Use built-in default presets from RecommendPresetRegistry
                RecommendPresetRegistry.buildRecommendConfig(RecommendPresetRegistry.defaultCodes())
            } else {
                // Use all configurations from custom registry
                registry.values.joinToString("\n")
            }
        } else {
            // Specific presets selected by user
            if (registry.isEmpty()) {
                // Build config from preset codes using RecommendPresetRegistry
                // e.g., "spring,springboot" -> combined config for both presets
                RecommendPresetRegistry.buildRecommendConfig(selected)
            } else {
                // Look up each preset code in custom registry and combine them
                // e.g., "custom1,custom2" -> registry["custom1"] + registry["custom2"]
                selected.split(",").mapNotNull { registry[it.trim()] }.joinToString("\n")
            }
        }
        if (config.isBlank()) return emptySequence()
        return configTextParser.parse(config, sourceId, null)
    }
}
