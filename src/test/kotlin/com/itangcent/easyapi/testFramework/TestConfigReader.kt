package com.itangcent.easyapi.testFramework

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.ConfigReloadListener
import com.itangcent.easyapi.core.config.parser.PropertyResolver
import com.itangcent.easyapi.core.extension.ExtensionConfigParser

class TestConfigReader(
    private var entries: List<Pair<String, String>> = emptyList(),
    private val project: Project
) : ConfigReader {

    private fun valuesByKey(): Map<String, List<String>> {
        return entries.groupBy({ it.first }, { it.second })
    }

    override fun getFirst(key: String): String? = valuesByKey()[key]?.firstOrNull()

    override fun getAll(key: String): List<String> = valuesByKey()[key].orEmpty()

    override suspend fun reload() {
        project.messageBus.syncPublisher(ConfigReloadListener.TOPIC).onConfigReloaded()
    }

    override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
        entries.forEach { (key, value) ->
            if (keyFilter(key)) {
                action(key, value)
            }
        }
    }

    companion object {
        fun empty(project: Project) = TestConfigReader(emptyList(), project)

        fun fromRules(project: Project, vararg rules: Pair<String, String>): TestConfigReader {
            return TestConfigReader(rules.toList(), project)
        }

        fun fromConfigText(project: Project, configText: String): TestConfigReader {
            val entries = mutableListOf<Pair<String, String>>()
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
                entries.add(key to value)
            }
            // Resolve `${key}` placeholders, mirroring LayeredConfigReader's
            // production pipeline. The stub above parses verbatim, so without
            // this pass templated rules like swagger3's `${resolve_parameter}`
            // would reach the rule engine as a literal Groovy syntax error
            // (silently swallowed under IGNORE_ERROR event mode) and the
            // export.after script could never fire in integration tests.
            return TestConfigReader(resolvePlaceholders(entries), project)
        }

        /**
         * Resolves `${key}` references in entry values against the other
         * parsed entries (FIRST on multi-value). Faithful to
         * [com.itangcent.easyapi.core.config.LayeredConfigReader].
         */
        private fun resolvePlaceholders(entries: List<Pair<String, String>>): List<Pair<String, String>> {
            val byKey = entries.groupBy({ it.first }, { it.second })
            val resolver = PropertyResolver(lookup = { k -> byKey[k].orEmpty() })
            return entries.map { (k, v) -> k to resolver.resolve(v) }
        }

        fun fromMap(project: Project, map: Map<String, Any?>): TestConfigReader {
            val entries = mutableListOf<Pair<String, String>>()
            map.forEach { (key, value) ->
                when (value) {
                    is List<*> -> value.forEach { entries.add(key to it.toString()) }
                    else -> entries.add(key to value.toString())
                }
            }
            return TestConfigReader(entries, project)
        }
    }
}
