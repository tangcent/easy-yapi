package com.itangcent.easyapi.core.rule

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.framework.spi.FrameworkRegistry

/**
 * Project-level registry of every rule key known to the plugin.
 *
 * Single source of truth that combines four sources:
 *
 * 1. **General/shared keys** — declared in [RuleKeys], reflected via [RuleKey.collectFrom].
 * 2. **Channel-specific keys** — contributed by each registered
 *    [com.itangcent.easyapi.channel.spi.Channel] via
 *    [com.itangcent.easyapi.channel.spi.Channel.ruleKeys].
 * 3. **Framework-specific keys** — contributed by each registered
 *    [com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer] via
 *    [com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer.ruleKeys].
 * 4. **Implicit keys** — read by name via `configReader.getFirst("…")`,
 *    enumerated in [ImplicitConfigKeys].
 *
 * The pure assembly and enablement logic lives in [RuleKeyCatalog] so it can
 * be shared by the build-time exporter without depending on an IntelliJ [Project].
 *
 * ## Consumers
 * - [com.itangcent.easyapi.core.ai.tools.ListRuleKeysTool] — exposes the catalog to the AI agent.
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
     * @see RuleKeyCatalog.RuleKeyInfo
     */
    data class RuleKeyInfo(
        val key: RuleKey<*>,
        val source: String
    )

    /**
     * All known rule keys (general + channel + framework + implicit),
     * de-duplicated by primary name.
     */
    fun allKeys(): List<RuleKeyInfo> {
        val channelKeys = ChannelRegistry.getInstance(project)
            .allChannels().map { it.id to it.ruleKeys() }
        val frameworkKeys = CompositeApiClassRecognizer.getInstance(project)
            .allRecognizers().map { it.frameworkName to it.ruleKeys() }
        return RuleKeyCatalog.assembleKeys(channelKeys, frameworkKeys).map { info ->
            RuleKeyInfo(info.key, info.source)
        }
    }

    /**
     * The subset of [allKeys] whose owning channel/framework is enabled.
     */
    fun enabledKeys(): List<RuleKeyInfo> {
        val channelRegistry = ChannelRegistry.getInstance(project)
        val allChannels = channelRegistry.allChannels()
        val enabledChannelIds = allChannels
            .filter { channelRegistry.isEnabled(it) }
            .map { it.id }
            .toSet()
        val allChannelIds = allChannels.map { it.id }.toSet()

        val allRecognizers = CompositeApiClassRecognizer.getInstance(project).allRecognizers()
        val allFrameworkIds = allRecognizers.map { it.frameworkName }.toSet()
        val enabledFrameworkIds = allRecognizers
            .filter { FrameworkRegistry.getInstance(project).isEnabled(it) }
            .map { it.frameworkName }
            .toSet()

        return allKeys().filter {
            RuleKeyCatalog.isEnabledSource(
                RuleKeyCatalog.RuleKeyInfo(it.key, it.source),
                enabledChannelIds, allChannelIds, enabledFrameworkIds, allFrameworkIds
            )
        }
    }

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
        /**
         * Pure assembly of the rule-key catalog. Delegates to [RuleKeyCatalog.assembleKeys].
         */
        internal fun assembleKeys(
            channelKeys: List<Pair<String, List<RuleKey<*>>>>,
            frameworkKeys: List<Pair<String, List<RuleKey<*>>>> = emptyList()
        ): List<RuleKeyInfo> =
            RuleKeyCatalog.assembleKeys(channelKeys, frameworkKeys).map { info ->
                RuleKeyInfo(info.key, info.source)
            }

        /**
         * Pure enablement resolution. Delegates to [RuleKeyCatalog.isEnabledSource].
         */
        internal fun isEnabledSource(
            info: RuleKeyInfo,
            enabledChannelIds: Set<String>,
            allChannelIds: Set<String>,
            enabledFrameworkIds: Set<String>,
            allFrameworkIds: Set<String>
        ): Boolean = RuleKeyCatalog.isEnabledSource(
            RuleKeyCatalog.RuleKeyInfo(info.key, info.source),
            enabledChannelIds, allChannelIds, enabledFrameworkIds, allFrameworkIds
        )

        fun getInstance(project: Project): RuleKeyRegistry = project.service()
    }
}