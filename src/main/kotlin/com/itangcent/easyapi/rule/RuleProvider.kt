package com.itangcent.easyapi.rule

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.ConfigReloadListener
import com.itangcent.easyapi.logging.IdeaLog
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Project-level service that provides cached configuration rules for [RuleEngine].
 *
 * This service bridges [ConfigReader] and [RuleEngine], providing:
 * - **Caching**: Rules are cached by key name with a 30-second TTL
 * - **Auto-invalidation**: Cache is cleared when configuration is reloaded
 * - **Thread safety**: Uses [ConcurrentHashMap] for concurrent access
 *
 * ## Cache Invalidation
 * The cache is invalidated when:
 * 1. Configuration is reloaded ([ConfigReloadListener.onConfigReloaded] is called)
 * 2. Cache entry TTL expires (30 seconds)
 *
 * ## Usage
 * ```kotlin
 * val ruleProvider = RuleProvider.getInstance(project)
 * val rules = ruleProvider.getRules(RuleKeys.API_NAME)
 * for (rule in rules) {
 *     if (rule.filter == null || evaluateFilter(rule.filter)) {
 *         evaluateExpression(rule.expression)
 *     }
 * }
 * ```
 *
 * ## Architecture
 * ```
 * ConfigReader → RuleProvider (cached) → RuleEngine
 *                     ↑
 *              ConfigReloadListener (clears cache)
 * ```
 *
 * @see ConfigRule
 * @see com.itangcent.easyapi.rule.engine.RuleEngine
 * @see ConfigReloadListener
 */
@Service(Service.Level.PROJECT)
class RuleProvider(
    private val project: Project
) : ConfigReloadListener, Disposable, IdeaLog {

    private val configReader: ConfigReader get() = ConfigReader.getInstance(project)

    private val cache = ConcurrentHashMap<String, CachedRules>()

    private val connection = project.messageBus.connect(this)

    init {
        connection.subscribe(ConfigReloadListener.TOPIC, this)
    }

    /**
     * Gets all rules for the given [RuleKey].
     *
     * Results are cached for 30 seconds. If the cache is valid, returns cached rules.
     * Otherwise, loads rules from [ConfigReader] and caches them.
     *
     * @param key The rule key to look up
     * @return List of [ConfigRule] instances for the key (may be empty)
     */
    fun getRules(key: RuleKey<*>): List<ConfigRule> {
        val now = System.currentTimeMillis()
        val cached = cache[key.name]
        if (cached != null && !cached.isExpired(now)) {
            return cached.rules
        }
        val rules = loadRules(key)
        cache[key.name] = CachedRules(rules, now + CACHE_TTL_MS)
        return rules
    }

    /**
     * Loads rules from [ConfigReader] for the given key.
     *
     * This method scans both direct rules and indexed rules (with filter expressions),
     * preserving the original order from configuration files.
     *
     * @param key The rule key to load
     * @return List of [ConfigRule] instances
     */
    private fun loadRules(key: RuleKey<*>): List<ConfigRule> {
        val result = ArrayList<ConfigRule>()
        for (k in key.allNames) {
            configReader.foreach(
                { cfgKey -> cfgKey == k || cfgKey.startsWith("$k[") },
                { cfgKey, value ->
                    val filterExp = if (cfgKey == k) {
                        null
                    } else {
                        cfgKey.removePrefix(k).removeSurrounding("[", "]")
                    }
                    result.add(ConfigRule(value, filterExp))
                }
            )
        }
        return result
    }

    /**
     * Called when configuration is reloaded.
     * Clears all cached rules to ensure fresh data on next access.
     */
    override fun onConfigReloaded() {
        LOG.info("RuleProvider: clearing cache due to config reload")
        clearCache()
    }

    /**
     * Clears all cached rules.
     * Called internally on config reload or disposal.
     */
    private fun clearCache() {
        cache.clear()
    }

    /**
     * Cleans up resources when the service is disposed.
     */
    override fun dispose() {
        clearCache()
    }

    /**
     * Cached rules with expiration time.
     */
    private data class CachedRules(
        val rules: List<ConfigRule>,
        val expireTime: Long
    ) {
        fun isExpired(now: Long): Boolean = now >= expireTime
    }

    companion object {
        /**
         * Cache time-to-live in milliseconds (30 seconds).
         */
        private val CACHE_TTL_MS = 30.seconds.inWholeMilliseconds

        /**
         * Gets the [RuleProvider] instance for the given project.
         */
        fun getInstance(project: Project): RuleProvider = project.service()
    }
}
