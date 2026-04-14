package com.itangcent.easyapi.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Interface for reading and saving plugin settings.
 *
 * Provides a simple abstraction for settings persistence.
 * Implementations handle the actual storage mechanism.
 *
 * ## Usage
 * ```kotlin
 * // Read settings
 * val settings = settingBinder.read()
 * 
 * // Update settings
 * settingBinder.update {
 *     postmanToken = "new-token"
 * }
 * 
 * // Save settings
 * settingBinder.save(newSettings)
 * ```
 *
 * @see DefaultSettingBinder for the default implementation
 * @see Settings for the settings model
 */
interface SettingBinder {
    /**
     * Reads the current settings.
     *
     * @return The current settings
     */
    fun read(): Settings

    /**
     * Saves the settings.
     *
     * @param settings The settings to save, or null to reset
     */
    fun save(settings: Settings)

    /**
     * Tries to read settings without creating defaults.
     *
     * @return The current settings, or null if not set
     */
    fun tryRead(): Settings?

    companion object {
        fun getInstance(project: Project): SettingBinder = project.service()
    }
}

/**
 * Updates settings using the given updater function.
 *
 * @param updater The function to modify settings
 */
fun SettingBinder.update(updater: Settings.() -> Unit) {
    this.read().also(updater).let { this.save(it) }
}

/**
 * Wraps this SettingBinder with a caching layer.
 *
 * @param cacheTimeoutMillis Cache timeout in milliseconds, defaults to 30000 (30 seconds)
 * @return A cached SettingBinder
 */
fun SettingBinder.lazy(cacheTimeoutMillis: Duration): SettingBinder {
    return CachedSettingBinder(this, cacheTimeoutMillis)
}

/**
 * A cached wrapper for SettingBinder.
 *
 * Caches settings in memory to avoid repeated reads.
 * Thread-safe implementation using volatile and synchronized.
 * Cache expires after [cacheTimeoutMillis] milliseconds (default 30 seconds).
 *
 * Note: The cache returns the same Settings instance to avoid expensive deep copies.
 * Callers should NOT mutate the returned Settings object - use [SettingBinder.update]
 * or call [save] with a modified copy instead. This is safe because:
 * 1. Cache expires every 30 seconds, limiting staleness
 * 2. [save] creates a defensive copy before caching
 *
 * @param delegate The underlying SettingBinder
 * @param cacheTimeoutMillis Cache timeout
 */
class CachedSettingBinder(
    private val delegate: SettingBinder,
    private val cacheTimeoutMillis: Duration
) : SettingBinder {
    @Volatile
    private var cached: Settings? = null

    @Volatile
    private var expireAt: Long = 0L

    private fun isCacheExpired(): Boolean {
        return System.currentTimeMillis() > expireAt
    }

    private fun updateCache(settings: Settings) {
        cached = settings
        expireAt = System.currentTimeMillis() + cacheTimeoutMillis.inWholeMilliseconds
    }

    override fun read(): Settings {
        val cachedResult = cached
        if (cachedResult != null && !isCacheExpired()) {
            return cachedResult
        }
        return synchronized(this) {
            val recheckCache = cached
            if (recheckCache != null && !isCacheExpired()) {
                recheckCache
            } else {
                delegate.read().also {
                    updateCache(it)
                }
            }
        }
    }

    override fun save(settings: Settings) {
        delegate.save(settings)
        updateCache(settings.copy())
    }

    override fun tryRead(): Settings? {
        val cachedResult = cached
        if (cachedResult != null && !isCacheExpired()) {
            return cachedResult
        }
        return synchronized(this) {
            val recheckCache = cached
            if (recheckCache != null && !isCacheExpired()) {
                recheckCache
            } else {
                delegate.tryRead()?.also {
                    updateCache(it)
                }
            }
        }
    }
}
