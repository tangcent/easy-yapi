package com.itangcent.easyapi.config

import com.intellij.util.messages.Topic

/**
 * Listener interface for configuration reload events.
 *
 * Published via [Topic] when configuration is reloaded, allowing services
 * to invalidate caches or refresh their state.
 *
 * ## Usage
 * Subscribe to [TOPIC] via the project's message bus:
 * ```kotlin
 * project.messageBus.connect().subscribe(ConfigReloadListener.TOPIC, object : ConfigReloadListener {
 *     override fun onConfigReloaded() {
 *         // Clear caches, refresh state, etc.
 *     }
 * })
 * ```
 *
 * ## Publishers
 * - [DefaultConfigReader.reload] publishes this event after loading new configuration
 *
 * ## Subscribers
 * - [com.itangcent.easyapi.rule.RuleProvider] clears its rule cache
 *
 * @see DefaultConfigReader
 * @see com.itangcent.easyapi.rule.RuleProvider
 */
interface ConfigReloadListener {

    /**
     * Called when configuration has been reloaded.
     *
     * Implementations should invalidate any cached data derived from configuration.
     */
    fun onConfigReloaded()

    companion object {
        /**
         * Topic for configuration reload events.
         */
        val TOPIC: Topic<ConfigReloadListener> = Topic.create(
            "EasyAPI Config Reloaded",
            ConfigReloadListener::class.java
        )
    }
}
