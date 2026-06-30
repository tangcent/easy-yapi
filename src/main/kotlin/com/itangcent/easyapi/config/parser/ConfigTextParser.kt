package com.itangcent.easyapi.config.parser

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.resource.ConfigResourceLoader
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.settings
import com.itangcent.easyapi.util.text.KeyValueLineParser

/**
 * Parses configuration text into a sequence of [ConfigEntry] objects.
 *
 * Supports:
 * - Key-value pairs with `=` or `:` separators
 * - Multi-line values using triple backticks
 * - Directive lines starting with `###`
 * - Additional file includes via `###include` (preferred) or the legacy
 *   `properties.additional` key
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
 * ###include ./additional.properties
 * # Legacy form, kept for backward compatibility:
 * properties.additional=./additional.properties
 * ```
 *
 * Both include forms accept local file paths and remote URLs
 * (`http://` / `https://`). Resolution is delegated to [ConfigResourceLoader].
 * The included file is parsed with the same [DirectiveState] as the including
 * file, so directive settings (e.g. `ignoreUnresolved`) carry into it.
 *
 * This is a project-level service. Settings are obtained from
 * [SettingBinder] on each [parse] call (so directive conditions always
 * reflect the latest settings), and the resource loader from
 * [ConfigResourceLoader], both resolved from the [project].
 */
@Service(Service.Level.PROJECT)
class ConfigTextParser(
    private val project: Project
) : IdeaLog {

    private val resourceLoader: ConfigResourceLoader get() = ConfigResourceLoader.getInstance(project)

    suspend fun parse(text: String, sourceId: String, baseDir: String? = null): Sequence<ConfigEntry> {
        return parseLines(text.lines(), sourceId, baseDir, DirectiveState(), project.settings).asSequence()
    }

    private suspend fun parseLines(
        lines: List<String>,
        sourceId: String,
        baseDir: String?,
        state: DirectiveState,
        settings: Settings
    ): List<ConfigEntry> {
        val directiveParser = DirectiveParser(state, settings)
        val result = ArrayList<ConfigEntry>()

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trim()
            i++

            if (line.isEmpty()) continue
            if (line.startsWith("#") && !line.startsWith("###")) continue

            // `###include <path-or-url>` — handled before generic directive
            // parsing so it isn't swallowed by DirectiveParser. Respects the
            // active conditional state, like any content-affecting line.
            if (line.startsWith(INCLUDE_DIRECTIVE)) {
                if (state.isActive()) {
                    val path = line.removePrefix(INCLUDE_DIRECTIVE).trim()
                    include(path, sourceId, baseDir, state, settings, result)
                }
                continue
            }

            if (directiveParser.handle(line)) continue
            if (!state.isActive()) continue

            val kv = KeyValueLineParser.splitKeyValue(line) ?: continue
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

            // Legacy include form, kept for backward compatibility.
            if (key == "properties.additional") {
                include(value, sourceId, baseDir, state, settings, result)
                continue
            }

            result.add(ConfigEntry(key, value, sourceId, state.snapshot()))
        }

        return result
    }

    /**
     * Loads the resource at [pathOrUrl] and parses it into [result], reusing
     * the caller's [state] so directive settings carry into the included file.
     *
     * Throws [IllegalStateException] when the resource cannot be resolved,
     * unless `ignoreNotFoundFile` is set on [state].
     */
    private suspend fun include(
        pathOrUrl: String,
        sourceId: String,
        baseDir: String?,
        state: DirectiveState,
        settings: Settings,
        result: MutableList<ConfigEntry>
    ) {
        val loaded = resourceLoader.load(pathOrUrl, baseDir)
        if (loaded != null) {
            result.addAll(
                parseLines(
                    loaded.content.lines(),
                    sourceId,
                    loaded.baseDir,
                    state,
                    settings
                )
            )
        } else if (!state.ignoreNotFoundFile) {
            throw IllegalStateException("Cannot resolve include: $pathOrUrl")
        }
    }

    companion object {
        private const val INCLUDE_DIRECTIVE = "###include"

        fun getInstance(project: Project): ConfigTextParser = project.service()
    }
}
