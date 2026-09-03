package com.itangcent.easyapi.core.rule

import com.itangcent.easyapi.channel.hoppscotch.HoppscotchRuleKeys
import com.itangcent.easyapi.channel.openapi.OpenApiRuleKeys
import com.itangcent.easyapi.channel.postman.PostmanRuleKeys
import com.itangcent.easyapi.channel.yapi.YapiMetaRuleKeys
import com.itangcent.easyapi.channel.yapi.YapiRuleKeys
import com.itangcent.easyapi.framework.custom.CustomRuleKeys

/**
 * Pure static catalog of every rule key known to the plugin, with no
 * IntelliJ [com.intellij.openapi.project.Project] dependency.
 *
 * Shared by both the in-plugin AI agent (via [RuleKeyRegistry]) and the
 * build-time rule-key/script-context exporters (which live in the test source
 * set, not shipped in the plugin JAR) so that the internal and external views
 * of the rule-key catalog are produced by the same code. The only difference
 * is at which layer enablement filtering is applied:
 * - **Runtime (RuleKeyRegistry)**: reflects the current project's enabled
 *   channels/frameworks.
 * - **Build-time (exporters)**: exports the full catalog with a `source`
 *   field so the external skill can filter by source.
 */
object RuleKeyCatalog {

    private const val SOURCE_GENERAL = "general"
    private const val SOURCE_IMPLICIT = "implicit"

    /**
     * The fixed, framework name label under which [CustomRuleKeys] are grouped
     * in the catalog. Mirrors `CustomApiRecognizer.FRAMEWORK_NAME` (a `const
     * val` = `"Custom"`). Inlined as a literal here — rather than referencing
     * `CustomApiRecognizer.FRAMEWORK_NAME` directly — so [RuleKeyCatalog] stays
     * free of any dependency on the `com.itangcent.easyapi.framework.custom`
     * recognizer class (which itself pulls in `com.intellij.openapi.project.Project`).
     */
    const val FRAMEWORK_NAME = "Custom"

    /**
     * The rule-key sources the static catalog knows about — `(source label,
     * supplier)`, in precedence order (earlier sources win on name collision).
     *
     * This is the single source of truth shared by:
     * 1. The runtime AI agent's L0 rule-key name menu ([SystemPromptBuilder]),
     *    which needs the full static list (not enablement-filtered) so the
     *    agent knows which keys *could* apply before deciding what to fetch.
     * 2. The build-time exporters in the test source set (rule-key scheme
     *    catalog + script-context catalog), which must enumerate exactly the
     *    same sources so their artifacts (`rule-keys.json` / `rule-contexts.json`)
     *    cover identical keys.
     *
     * Keep this aligned with [assembleKeys]'s runtime channel/framework
     * sources. The guard tests (`RuleKeySchemeExporterTest`,
     * `EasyApiAssistantSkillTest`) fail if a registered source is missing here.
     */
    val SOURCES: List<Pair<String, () -> List<RuleKey<*>>>> = listOf(
        "general" to { RuleKey.collectFrom(RuleKeys) },
        "postman" to { RuleKey.collectFrom(PostmanRuleKeys) },
        "hoppscotch" to { RuleKey.collectFrom(HoppscotchRuleKeys) },
        "openapi" to { RuleKey.collectFrom(OpenApiRuleKeys) },
        "yapi" to { RuleKey.collectFrom(YapiRuleKeys) + RuleKey.collectFrom(YapiMetaRuleKeys) },
        FRAMEWORK_NAME to { RuleKey.collectFrom(CustomRuleKeys) },
        "implicit" to { ImplicitConfigKeys.all }
    )

    /**
     * The de-duplicated, sorted-by-name `(RuleKey, source)` assembly of
     * [SOURCES], mirroring [assembleKeys] precedence (earlier sources win).
     */
    fun assembledKeyInfos(): List<Pair<RuleKey<*>, String>> {
        val seen = HashSet<String>()
        val result = mutableListOf<Pair<RuleKey<*>, String>>()
        SOURCES.forEach { (source, supplier) ->
            supplier().forEach { key ->
                if (seen.add(key.name)) {
                    result.add(key to source)
                }
            }
        }
        return result.sortedBy { it.first.name }
    }

    /**
     * A rule key plus the [source] that contributed it.
     */
    data class RuleKeyInfo(
        val key: RuleKey<*>,
        val source: String
    )

    /**
     * Pure assembly of the rule-key catalog from four sources:
     * 1. [RuleKeys] (reflected via [RuleKey.collectFrom])
     * 2. [channelKeys] — pairs of `(channelId, keys)` from each registered channel
     * 3. [frameworkKeys] — pairs of `(frameworkName, keys)` from each registered framework
     * 4. [ImplicitConfigKeys.all] (fixed config keys read by name)
     *
     * @param channelKeys pairs of `(channelId, keys)` for each registered channel.
     *   Pass `emptyList()` when no channel keys are available (e.g. in tests).
     * @param frameworkKeys pairs of `(frameworkName, keys)` for each registered
     *   framework. Defaults to `emptyList()`.
     */
    fun assembleKeys(
        channelKeys: List<Pair<String, List<RuleKey<*>>>>,
        frameworkKeys: List<Pair<String, List<RuleKey<*>>>> = emptyList()
    ): List<RuleKeyInfo> {
        val seen = HashSet<String>()
        val result = mutableListOf<RuleKeyInfo>()

        RuleKey.collectFrom(RuleKeys).forEach { key ->
            if (seen.add(key.name)) result.add(RuleKeyInfo(key, SOURCE_GENERAL))
        }
        channelKeys.forEach { (source, keys) ->
            keys.forEach { key ->
                if (seen.add(key.name)) result.add(RuleKeyInfo(key, source))
            }
        }
        frameworkKeys.forEach { (source, keys) ->
            keys.forEach { key ->
                if (seen.add(key.name)) result.add(RuleKeyInfo(key, source))
            }
        }
        ImplicitConfigKeys.all.forEach { key ->
            if (seen.add(key.name)) result.add(RuleKeyInfo(key, SOURCE_IMPLICIT))
        }
        return result
    }

    /**
     * Pure resolution rule for whether a [RuleKeyInfo] should be visible
     * to the AI agent (i.e. its owning channel/framework is enabled).
     *
     * Resolution order (design C4a + AC-S4 prefix check):
     * 1. **General/implicit keys** → enabled unless the key name belongs
     *    to a disabled channel via prefix matching.
     * 2. **Channel-sourced keys** → enabled iff `info.source` ∈ [enabledChannelIds].
     * 3. **Framework-sourced keys** → enabled iff `info.source` ∈ [enabledFrameworkIds].
     * 4. **Unknown source kind** → enabled (never filter).
     *
     * @param info the rule key info to check
     * @param enabledChannelIds channel ids that are currently enabled
     * @param allChannelIds all registered channel ids (for prefix matching
     *     on general/implicit keys + source resolution)
     * @param enabledFrameworkIds framework names that are currently enabled
     * @param allFrameworkIds all registered framework names (for source
     *     resolution; uses the unfiltered `allRecognizers()` view)
     */
    fun isEnabledSource(
        info: RuleKeyInfo,
        enabledChannelIds: Set<String>,
        allChannelIds: Set<String>,
        enabledFrameworkIds: Set<String>,
        allFrameworkIds: Set<String>
    ): Boolean {
        val keyName = info.key.name

        // 1. General/implicit keys: check channel prefix (AC-S4)
        if (info.source == SOURCE_GENERAL || info.source == SOURCE_IMPLICIT) {
            for (channelId in allChannelIds) {
                if (keyName.startsWith("$channelId.") && channelId !in enabledChannelIds) {
                    return false
                }
            }
            return true
        }

        // 2. Channel-sourced key
        if (info.source in allChannelIds) {
            return info.source in enabledChannelIds
        }

        // 3. Framework-sourced key
        if (info.source in allFrameworkIds) {
            return info.source in enabledFrameworkIds
        }

        // 4. Unknown source kind: don't filter
        return true
    }

    /**
     * Serializable view of one rule key's scheme — the single shared entry
     * shape used by BOTH the in-plugin `list_rule_keys` tool and the build-time
     * rule-key scheme exporter (`rule-keys.json`).
     *
     * This is the parity anchor of spec D4.3: the built-in agent and the
     * external skill's `rule-keys.json` are produced from the *same* entry
     * type, so their field sets can never drift apart — only enablement
     * filtering differs (runtime filters by channel/framework, export is full).
     */
    data class SchemeEntry(
        /** The primary key name. */
        val name: String,
        /** Source category: `"general"`, `"implicit"`, or a channel/framework id. */
        val source: String,
        /** The scheme summary. */
        val summary: String?,
        /** The context kind ids (e.g. `["class", "method"]`), or empty. */
        val contextKinds: List<String> = emptyList(),
        /** The output shape, or `null`. */
        val outputShape: String? = null,
        /** Whether the key is dry-runnable. */
        val dryRunnable: Boolean = false,
        /** Whether the key is a static configuration value. */
        val staticConfiguration: Boolean = false,
        /** Aliases for this key. */
        val aliases: List<String> = emptyList(),
        /** The rule key value type (`StringKey` / `BooleanKey` / `EventKey` / `IntKey`). */
        val type: String = "RuleKey",
        /** The aggregation/execution mode name (`SINGLE` / `MERGE` / `ANY` / …). */
        val mode: String = "?",
        /** The declared additional bindings, each `{name, kind}`. */
        val additionalBindings: List<RuleBinding> = emptyList(),
        /** True when a plain (non-Groovy) value must be a valid JSON object. */
        val jsonValue: Boolean = false,
        /** Extra guidance surfaced to the AI. */
        val notes: List<String> = emptyList()
    )

    /**
     * Produces a [SchemeEntry] for each [RuleKeyInfo] in [infos].
     * The rendering is shared between the in-plugin `list_rule_keys` tool
     * and the build-time rule-key scheme exporter.
     */
    fun schemeEntries(infos: List<RuleKeyInfo>): List<SchemeEntry> = infos.map { info ->
        val s = info.key.scheme
        SchemeEntry(
            name = info.key.name,
            source = info.source,
            summary = s.summary,
            contextKinds = s.contextKinds.map { it.name },
            outputShape = s.outputShape?.name,
            dryRunnable = s.dryRunnable,
            staticConfiguration = s.staticConfiguration,
            aliases = info.key.aliases,
            type = info.key::class.simpleName ?: "RuleKey",
            mode = info.key.mode::class.simpleName ?: "?",
            additionalBindings = s.additionalBindings,
            jsonValue = s.jsonValue,
            notes = s.notes
        )
    }

    /**
     * Renders a compact L0 key-name menu for the opening system prompt
     * (spec D2.5). One line per key, grouped by source, with the summary
     * truncated to a single short phrase.
     *
     * This is the **menu**, not the full scheme — the agent browses names
     * here and pulls the complete self-describing scheme via `list_rule_keys`
     * (which files the entries into the Knowledge State block). Kept short
     * (~1K tokens) so it costs little to include in every opening message.
     *
     * @param entries the scheme entries to list (already assembled + de-duped).
     */
    fun renderKeyMenu(entries: List<SchemeEntry>): String {
        val header = "Known rule keys (browse names here; call `list_rule_keys` " +
            "to load the full self-describing scheme into the Knowledge State):"
        if (entries.isEmpty()) {
            return "$header\n(none)"
        }
        val sb = StringBuilder(header).append("\n")
        entries.groupBy { it.source }.forEach { (source, grouped) ->
            sb.append("\n").append(source).append(":\n")
            grouped.forEach { e ->
                sb.append("- ").append(e.name)
                e.summary?.let { sb.append(" — ").append(it) }
                sb.append("\n")
            }
        }
        return sb.toString().trimEnd()
    }
}