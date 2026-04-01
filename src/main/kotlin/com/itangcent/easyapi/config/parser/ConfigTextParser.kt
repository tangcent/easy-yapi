package com.itangcent.easyapi.config.parser

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.settings.Settings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Parses configuration text into a sequence of [ConfigEntry] objects.
 *
 * Supports:
 * - Key-value pairs with `=` or `:` separators
 * - Multi-line values using triple backticks
 * - Directive lines starting with `###`
 * - Additional file includes via `properties.additional`
 *
 * ## Format
 * ```
 * # Simple key-value
 * api.name=my-api
 *
 * # Multi-line value
 * api.description=```
 * This is a
 * multi-line description.
 * ```
 *
 * # Directives
 * ###set resolveProperty=false
 * ###if builtInConfig==true
 * config.enabled=true
 * ###endif
 *
 * # Include additional file
 * properties.additional=./additional.properties
 * ```
 *
 * @param settings Settings for directive evaluation
 */
class ConfigTextParser(
    private val settings: Settings?
) {
    fun parse(text: String, sourceId: String, baseDir: String? = null): Sequence<ConfigEntry> {
        return parseLines(text.lines(), sourceId, baseDir, DirectiveState())
    }

    private fun parseLines(lines: List<String>, sourceId: String, baseDir: String?, state: DirectiveState): Sequence<ConfigEntry> = sequence {
        val directiveParser = DirectiveParser(state, settings)

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()
            i++

            if (line.isEmpty()) continue
            if (line.startsWith("#") && !line.startsWith("###")) continue

            if (directiveParser.handle(line)) continue
            if (!state.isActive()) continue

            val kv = splitKeyValue(line) ?: continue
            val key = kv.first
            var value = kv.second

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

            if (key == "properties.additional") {
                val additional = resolveAdditionalPath(value, baseDir)
                if (additional != null) {
                    val loaded = runCatching { Files.readString(additional, Charsets.UTF_8) }.getOrNull()
                    if (loaded != null) {
                        yieldAll(parseLines(loaded.lines(), sourceId, additional.parent?.toString(), DirectiveState()))
                    } else if (!state.ignoreNotFoundFile) {
                        throw IllegalStateException("Cannot read additional properties: $additional")
                    }
                } else if (!state.ignoreNotFoundFile) {
                    throw IllegalStateException("Cannot resolve additional properties: $value")
                }
                continue
            }

            yield(ConfigEntry(key, value, sourceId, state.snapshot()))
        }
    }

    private fun splitKeyValue(line: String): Pair<String, String>? {
        val eq = line.indexOf('=')
        val colon = line.indexOf(':')
        val idx = when {
            eq > 0 && (colon < 0 || eq < colon) -> eq
            colon > 0 -> colon
            else -> -1
        }
        if (idx <= 0) return null
        val key = line.substring(0, idx).trim()
        val value = line.substring(idx + 1).trim()
        return if (key.isEmpty()) null else key to value
    }

    private fun resolveAdditionalPath(path: String, baseDir: String?): Path? {
        val p = path.trim().trim('"', '\'')
        if (p.startsWith("http://") || p.startsWith("https://")) return null
        val resolved = when {
            p.startsWith("~/") -> Paths.get(System.getProperty("user.home")).resolve(p.removePrefix("~/"))
            Paths.get(p).isAbsolute -> Paths.get(p)
            baseDir != null -> Paths.get(baseDir).resolve(p)
            else -> Paths.get(p)
        }
        return resolved.normalize()
    }
}
