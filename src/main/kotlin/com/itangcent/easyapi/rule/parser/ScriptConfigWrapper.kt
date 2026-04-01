package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.config.ConfigReader

/**
 * Wraps [ConfigReader] to expose the same API as the legacy
 * `StandardJdkRuleParser.Config` class for script compatibility.
 *
 * Legacy methods:
 * - `config.get(name)` → first value
 * - `config.getValues(name)` → all values
 * - `config.resolveProperty(property)` → resolve placeholders
 */
class ScriptConfigWrapper(private val configReader: ConfigReader) {

    fun get(name: String): String? = configReader.getFirst(name)

    fun getValues(name: String): List<String> = configReader.getAll(name)

    fun resolveProperty(property: String): String {
        // Simple placeholder resolution: replace ${key} with config values
        return PLACEHOLDER_PATTERN.replace(property) { match ->
            val key = match.groupValues[1]
            configReader.getFirst(key) ?: match.value
        }
    }

    companion object {
        private val PLACEHOLDER_PATTERN = Regex("\\$\\{([^}]+)}")
    }
}
