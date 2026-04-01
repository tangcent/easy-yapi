package com.itangcent.easyapi.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

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
    fun save(settings: Settings?)
    
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
 * @return A cached SettingBinder
 */
fun SettingBinder.lazy(): SettingBinder {
    return CachedSettingBinder(this)
}

/**
 * A cached wrapper for SettingBinder.
 *
 * Caches settings in memory to avoid repeated reads.
 * Thread-safe implementation using volatile and synchronized.
 *
 * @param delegate The underlying SettingBinder
 */
class CachedSettingBinder(
    private val delegate: SettingBinder
) : SettingBinder {
    @Volatile
    private var cached: Settings? = null

    override fun read(): Settings {
        return cached?.copy() ?: synchronized(this) {
            cached?.copy() ?: delegate.read().copy().also { cached = it }
        }
    }

    override fun save(settings: Settings?) {
        delegate.save(settings)
        cached = settings?.copy()
    }

    override fun tryRead(): Settings? {
        return cached?.copy() ?: delegate.tryRead()?.copy()?.also { cached = it }
    }
}
