package com.itangcent.easyapi.rule

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.exporter.channel.ChannelRegistry

/**
 * Project-level registry of every rule key known to the plugin.
 *
 * Single source of truth that combines three sources:
 *
 * 1. **General/shared keys** — declared in [RuleKeys], reflected via [RuleKey.collectFrom].
 * 2. **Channel-specific keys** — contributed by each registered
 *    [com.itangcent.easyapi.exporter.channel.Channel] via
 *    [com.itangcent.easyapi.exporter.channel.Channel.ruleKeys]. The channel
 *    mix differs per repo (easy-api registers hoppscotch; easy-yapi registers
 *    yapi), so the registry's output reflects whichever channels are loaded.
 * 3. **Implicit keys** — read by name via `configReader.getFirst("…")`
 *    somewhere in the codebase but not declared as a [RuleKey] constant.
 *    Enumerated in [IMPLICIT_KEY_NAMES] so [RuleProposalValidator] and
 *    [com.itangcent.easyapi.ai.tools.ListRuleKeysTool] surface them too.
 *
 * ## Sources consumed
 * - [RuleKeys] (general)
 * - [ChannelRegistry.allChannels] → [com.itangcent.easyapi.exporter.channel.Channel.ruleKeys]
 * - [IMPLICIT_KEY_NAMES] (static)
 *
 * ## Consumers
 * - [com.itangcent.easyapi.ai.tools.ListRuleKeysTool] — exposes the full
 *   catalog to the AI agent.
 * - [RuleProposalValidator] — rejects proposals that use unknown keys.
 *
 * ## Usage
 * ```kotlin
 * val registry = RuleKeyRegistry.getInstance(project)
 * val allKeys = registry.allKeys()              // List<RuleKeyInfo>
 * val knownNames = registry.allKeyNames()       // Set<String> for O(1) lookup
 * val info = registry.findKey("api.name")       // RuleKeyInfo?
 * ```
 */
@Service(Service.Level.PROJECT)
class RuleKeyRegistry(private val project: Project) {

    /**
     * A rule key plus the [source] that contributed it.
     *
     * @param key the underlying [RuleKey]
     * @param source `"general"` for [RuleKeys]; the channel id (e.g.
     *     `"hoppscotch"`, `"yapi"`) for channel-specific keys; `"implicit"`
     *     for keys read by name only.
     */
    data class RuleKeyInfo(
        val key: RuleKey<*>,
        val source: String
    )

    /**
     * All known rule keys (general + channel + implicit), de-duplicated by
     * primary name. General keys take precedence over channel/implicit keys
     * with the same name (a channel should not re-declare a general key, but
     * the guard prevents confusing duplicates if one slips in).
     */
    fun allKeys(): List<RuleKeyInfo> = assembleKeys(
        ChannelRegistry.getInstance(project).allChannels().map { it.id to it.ruleKeys() }
    )

    /**
     * The set of every known rule key name (primary + aliases), for O(1)
     * validation lookup. Used by [RuleProposalValidator] to reject unknown keys.
     */
    fun allKeyNames(): Set<String> =
        allKeys().flatMap { it.key.allNames }.toSet()

    /** Finds the [RuleKeyInfo] for [name] (primary or alias), or `null`. */
    fun findKey(name: String): RuleKeyInfo? =
        allKeys().firstOrNull { name in it.key.allNames }

    companion object {
        private const val SOURCE_GENERAL = "general"
        private const val SOURCE_IMPLICIT = "implicit"

        /**
         * Rule keys read by name via `configReader.getFirst("…")` but not
         * declared as a `RuleKey` constant in [RuleKeys].
         *
         * Each entry documents the call site that reads it. Add new entries
         * here whenever a new `configReader.getFirst("fixed.name")` call is
         * introduced — this keeps the AI tooling and validator in sync without
         * forcing the call site to import [RuleKeys].
         *
         * Dynamic key scans (e.g. `MockRuleLoader` scanning `mock[...]` keys
         * by prefix) are NOT enumerated here — those are open-ended prefixes,
         * not fixed key names.
         */
        internal val IMPLICIT_KEYS: List<RuleKey<*>> = listOf(
            // DefaultPsiClassHelper.maxDeep() / maxElements()
            RuleKey.string("max.deep"),
            RuleKey.string("max.elements"),
            // MarkdownChannel — remote template fetcher tuning
            RuleKey.string("markdown.template.url.ttl.seconds"),
            RuleKey.string("markdown.template.url.max.bytes")
        )

        /**
         * Pure assembly of the rule-key catalog from three sources:
         * 1. [RuleKeys] (reflected via [RuleKey.collectFrom])
         * 2. [channelKeys] — pairs of `(channelId, keys)` from each registered channel
         * 3. [IMPLICIT_KEYS]
         *
         * Extracted from [allKeys] so it can be unit-tested without a real
         * IntelliJ [Project] (the only project dependency is
         * [ChannelRegistry], which the caller supplies via [channelKeys]).
         */
        internal fun assembleKeys(channelKeys: List<Pair<String, List<RuleKey<*>>>>): List<RuleKeyInfo> {
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
            IMPLICIT_KEYS.forEach { key ->
                if (seen.add(key.name)) result.add(RuleKeyInfo(key, SOURCE_IMPLICIT))
            }
            return result
        }

        fun getInstance(project: Project): RuleKeyRegistry = project.service()
    }
}
