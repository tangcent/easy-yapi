package com.itangcent.easyapi.testFramework

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.extension.ExtensionConfigParser

class TestConfigReader(
    private val config: Map<String, List<String>> = emptyMap()
) : ConfigReader {

    override fun getFirst(key: String): String? = config[key]?.firstOrNull()

    override fun getAll(key: String): List<String> = config[key].orEmpty()

    override suspend fun reload() {}

    override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
        config.forEach { (key, values) ->
            if (keyFilter(key)) {
                values.forEach { value ->
                    action(key, value)
                }
            }
        }
    }

    companion object {
        val EMPTY = TestConfigReader()

        fun fromRules(vararg rules: Pair<String, String>): TestConfigReader {
            val config = mutableMapOf<String, List<String>>()
            rules.forEach { (key, value) ->
                config[key] = config.getOrDefault(key, emptyList()) + value
            }
            return TestConfigReader(config)
        }

        fun fromConfigText(configText: String): TestConfigReader {
            val config = mutableMapOf<String, List<String>>()
            val strippedContent = ExtensionConfigParser.stripYamlFrontMatter(configText)
            val lines = strippedContent.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                i++
                if (line.isBlank() || line.startsWith("#")) continue
                val idx = line.indexOf('=')
                if (idx <= 0) continue
                val key = line.substring(0, idx).trim()
                var value = line.substring(idx + 1).trim()
                if (value == "```" || value.endsWith("```")) {
                    val prefix = if (value.endsWith("```") && value != "```") {
                        value.dropLast(3)
                    } else {
                        ""
                    }
                    val sb = StringBuilder()
                    while (i < lines.size) {
                        val next = lines[i]
                        i++
                        if (next.trim() == "```") break
                        if (sb.isNotEmpty()) sb.append('\n')
                        sb.append(next)
                    }
                    value = prefix + sb.toString()
                }
                config[key] = config.getOrDefault(key, emptyList()) + value
            }
            return TestConfigReader(config)
        }

        fun fromMap(map: Map<String, Any?>): TestConfigReader {
            val config = mutableMapOf<String, List<String>>()
            map.forEach { (key, value) ->
                when (value) {
                    is List<*> -> config[key] = value.map { it.toString() }
                    is String -> config[key] = listOf(value)
                    else -> config[key] = listOf(value.toString())
                }
            }
            return TestConfigReader(config)
        }
    }
}
