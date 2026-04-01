package com.itangcent.easyapi.recommend

/**
 * Represents a recommended configuration preset.
 *
 * Presets are predefined configuration snippets that can be enabled
 * to quickly set up common API documentation patterns.
 *
 * @param code The unique identifier for the preset
 * @param description A human-readable description
 * @param content The configuration content
 * @param defaultEnabled Whether this preset is enabled by default
 */
data class RecommendPreset(
    val code: String,
    val description: String,
    val content: String = "",
    val defaultEnabled: Boolean = false
)

/**
 * Registry for recommended configuration presets.
 *
 * Loads and manages predefined configuration snippets from
 * `.recommend.easy.api.config` resource file. Presets provide
 * quick setup for common API documentation patterns.
 *
 * ## Preset Format
 * ```
 * #[PresetCode]*        # * means default enabled
 * # Description line
 * config.key=value
 * ```
 *
 * ## Usage
 * ```kotlin
 * // Get all presets
 * val presets = RecommendPresetRegistry.allPresets()
 *
 * // Build config from selected presets
 * val config = RecommendPresetRegistry.buildRecommendConfig("spring,mvc")
 *
 * // Get default preset codes
 * val defaults = RecommendPresetRegistry.defaultCodes()
 * ```
 */
object RecommendPresetRegistry {
    private var presets: List<RecommendPreset> = emptyList()
    private var rawConfig: String = ""

    init {
        loadRecommendConfig()
    }

    private fun loadRecommendConfig() {
        val configName = ".recommend.easy.api.config"
        val config = javaClass.classLoader.getResourceAsStream(configName)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: ""
        rawConfig = config
        parseRecommendConfig(config)
    }

    private fun parseRecommendConfig(config: String) {
        val presetList = mutableListOf<RecommendPreset>()
        var code: String? = null
        var content = StringBuilder()
        var default = false
        var description = ""

        for (line in config.lines()) {
            if (line.startsWith("#[")) {
                if (code != null) {
                    presetList.add(RecommendPreset(code, description, content.toString().trimEnd('\n'), default))
                    content = StringBuilder()
                    default = false
                    description = ""
                }
                if (line.endsWith("]*")) {
                    default = true
                    code = line.removeSurrounding("#[", "]*")
                } else {
                    code = line.removeSurrounding("#[", "]")
                }
                description = code ?: ""
            } else if (line.startsWith("#") && code != null && content.isEmpty()) {
                if (description.isNotEmpty() && !description.startsWith("#")) {
                    description = line.removePrefix("#").trim()
                }
            } else {
                if (content.isNotEmpty() || line.isNotBlank()) {
                    content.append(line).append("\n")
                }
            }
        }

        if (code != null) {
            presetList.add(RecommendPreset(code, description, content.toString().trimEnd('\n'), default))
        }

        presets = presetList
    }

    fun getPreset(code: String): RecommendPreset? = presets.find { it.code == code }

    fun allPresets(): List<RecommendPreset> = presets

    fun buildRecommendConfig(codes: String, separator: CharSequence = "\n"): String {
        val set = codes.split(",").toSet()
        return presets
            .filter { set.contains(it.code) || (it.defaultEnabled && !set.contains("-${it.code}")) }
            .joinToString(separator) { it.content }
    }

    fun codes(): Array<String> = presets.map { it.code }.toTypedArray()

    fun selectedCodes(codes: String): Array<String> {
        val set = codes.split(",").toSet()
        return presets
            .filter { set.contains(it.code) || (it.defaultEnabled && !set.contains("-${it.code}")) }
            .map { it.code }
            .toTypedArray()
    }

    fun defaultCodes(): String {
        return presets
            .filter { it.defaultEnabled }
            .joinToString(",") { it.code }
    }

    fun addSelectedConfig(codes: String, vararg code: String): String {
        val set = LinkedHashSet(codes.split(","))
        set.addAll(code)
        code.map { "-$it" }.forEach { set.remove(it) }
        return set.joinToString(",")
    }

    fun removeSelectedConfig(codes: String, vararg code: String): String {
        val set = LinkedHashSet(codes.split(","))
        set.removeAll(code)
        code.map { "-$it" }.forEach { set.add(it) }
        return set.joinToString(",")
    }

    fun plaint(): String = rawConfig
}
