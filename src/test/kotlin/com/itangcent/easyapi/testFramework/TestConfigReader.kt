package com.itangcent.easyapi.testFramework

import com.itangcent.easyapi.config.ConfigReader

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
            configText.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .forEach { line ->
                    val idx = line.indexOf('=')
                    if (idx > 0) {
                        val key = line.substring(0, idx).trim()
                        val value = line.substring(idx + 1).trim()
                        config[key] = config.getOrDefault(key, emptyList()) + value
                    }
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
