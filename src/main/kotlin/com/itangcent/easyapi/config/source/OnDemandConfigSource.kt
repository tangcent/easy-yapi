package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.logging.IdeaLog

/**
 * A [ConfigSource] that conditionally loads configuration based on runtime conditions.
 *
 * On-demand config sources check whether they should be active before loading any configuration.
 * This is useful for framework-specific configurations that should only be loaded when the
 * corresponding framework is detected in the project.
 *
 * ## Implementation Pattern
 *
 * Subclasses should implement:
 * - [isEnabled]: Check if this config source should be active (e.g., framework detected, setting enabled)
 * - [loadConfig]: Load and parse the configuration entries when enabled
 * - [priority]: Define the order relative to other config sources
 * - [sourceId]: Unique identifier for this config source
 *
 * ## Example
 *
 * ```kotlin
 * class MyFrameworkConfigSource(private val project: Project) : OnDemandConfigSource() {
 *     override val priority: Int = 3
 *     override val sourceId: String = "my-framework"
 *
 *     override fun isEnabled(): Boolean {
 *         // Check if framework is present in project
 *         return findFrameworkAnnotation() != null
 *     }
 *
 *     override suspend fun loadConfig(): Sequence<ConfigEntry> {
 *         // Load configuration from resources
 *         return parseConfig(loadResource("my-framework.config"))
 *     }
 * }
 * ```
 *
 * ## Thread Safety
 *
 * - [isEnabled] may be called from any thread and should be thread-safe
 * - [loadConfig] is a suspend function and may be called from a coroutine context
 * - PSI operations in [isEnabled] should use [com.itangcent.easyapi.core.threading.readSync]
 *
 * @see ConfigSource
 * @see SwaggerOnDemandConfigSource
 * @see Swagger3OnDemandConfigSource
 */
abstract class OnDemandConfigSource : ConfigSource, IdeaLog {

    abstract override val priority: Int
    abstract override val sourceId: String

    /**
     * Collects configuration entries if this source is enabled.
     *
     * This method implements the [ConfigSource.collect] contract by first checking
     * [isEnabled] and only loading configuration when active.
     *
     * @return A sequence of [ConfigEntry] if enabled, empty sequence otherwise
     */
    override suspend fun collect(): Sequence<ConfigEntry> {
        if (!isEnabled()) {
            return emptySequence()
        }
        return loadConfig()
    }

    /**
     * Checks whether this config source should be active.
     *
     * Implementations should check:
     * 1. User settings (e.g., feature toggle)
     * 2. Project state (e.g., framework annotations present)
     *
     * This method may be called frequently and should be efficient.
     * PSI operations should be wrapped in [com.itangcent.easyapi.core.threading.readSync].
     *
     * @return `true` if this config source should load configuration, `false` otherwise
     */
    protected abstract fun isEnabled(): Boolean

    /**
     * Loads and parses configuration entries.
     *
     * This method is only called when [isEnabled] returns `true`.
     * Implementations should:
     * 1. Load configuration from resources or files
     * 2. Parse the configuration using [com.itangcent.easyapi.config.parser.ConfigTextParser]
     * 3. Return the parsed entries as a sequence
     *
     * @return A sequence of [ConfigEntry] parsed from the configuration
     */
    protected abstract suspend fun loadConfig(): Sequence<ConfigEntry>
}
