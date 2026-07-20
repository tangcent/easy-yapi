package com.itangcent.easyapi.core.config.parser

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.config.model.ConfigEntry
import com.itangcent.easyapi.core.config.resource.ConfigResourceLoader
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.module.HttpSettings
import com.itangcent.easyapi.core.settings.module.RuleFileSettings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.core.util.text.KeyValueLineParser
import com.itangcent.easyapi.framework.spi.FrameworkRegistry

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
        return parseLines(text.lines(), sourceId, baseDir, DirectiveState(), buildSettingResolver()).asSequence()
    }

    /**
     * Builds the setting-key resolver used by [DirectiveParser] for `###if`
     * conditions. Resolves general-module keys via [settings] reads.
     *
     * Channel-specific directive keys (e.g. `hoppscotchToken`, `yapiServer`)
     * are no longer resolved via the legacy `Settings.extensions` carrier.
     * Known general/HTTP/rule-file/postman keys resolve to their module field
     * values; unknown keys return null (the `###if` condition evaluates to false).
     */
    private fun buildSettingResolver(): (String) -> String? {
        val general = project.settings<GeneralSettings>()
        val http = project.settings<HttpSettings>()
        val ruleFile = project.settings<RuleFileSettings>()
        // postmanToken is read via string-based lookup to avoid importing
        // channel.postman.PostmanSettings (concrete-impl) from core.* (Decision CO3).
        val postmanModuleKey = "com.itangcent.easyapi.channel.postman.PostmanSettings"
        return { key ->
            when (key) {
                "builtInConfig" -> ruleFile.builtInConfig
                "remoteConfig" -> ruleFile.remoteConfig.joinToString("\n")
                "extensionConfig" -> ruleFile.extensionConfigs
                "logLevel" -> general.logLevel.toString()
                "httpTimeOut" -> http.httpTimeOut.toString()
                "unsafeSsl" -> http.unsafeSsl.toString()
                "httpClient" -> http.httpClient
                "postmanToken" -> UnifiedAppSettingsState.getInstance()
                    .getValue(postmanModuleKey, "postmanToken")
                // @Deprecated aliases — resolve via FrameworkRegistry.
                // These rule keys are kept for backward compatibility with existing
                // config files that use `###if feignEnable==true` etc. They now
                // resolve the framework's effective enabled state as a boolean
                // string. Use the unified framework enablement mechanism
                // (`enabledFrameworks`/`disabledFrameworks` arrays in GeneralSettings).
                "feignEnable" -> FrameworkRegistry.getInstance(project).isEnabled("Feign").toString()
                "jaxrsEnable" -> FrameworkRegistry.getInstance(project).isEnabled("JAX-RS").toString()
                "actuatorEnable" -> FrameworkRegistry.getInstance(project).isEnabled("SpringActuator").toString()
                else -> null
            }
        }
    }

    private suspend fun parseLines(
        lines: List<String>,
        sourceId: String,
        baseDir: String?,
        state: DirectiveState,
        resolveSetting: (String) -> String?
    ): List<ConfigEntry> {
        val directiveParser = DirectiveParser(state, resolveSetting)
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
                    include(path, sourceId, baseDir, state, resolveSetting, result)
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
                include(value, sourceId, baseDir, state, resolveSetting, result)
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
        resolveSetting: (String) -> String?,
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
                    resolveSetting
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
