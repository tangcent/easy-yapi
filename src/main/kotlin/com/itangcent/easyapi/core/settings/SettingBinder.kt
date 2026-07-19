package com.itangcent.easyapi.core.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlin.reflect.KClass

/**
 * Generic read/save of any [Settings].
 *
 * Reflects on each module's `@StorageScope`-annotated properties and routes
 * reads/writes to the unified [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState]
 * (for [Scope.APPLICATION] fields) or [com.itangcent.easyapi.core.settings.state.UnifiedProjectSettingsState]
 * (for [Scope.PROJECT] fields), with values serialized as `String` and coerced
 * back to the property type on read.
 *
 * ## Usage
 * ```kotlin
 * val binder = SettingBinder.getInstance(project)
 *
 * // Read a module
 * val postman = binder.read<PostmanSettings>()
 *
 * // Update a module
 * binder.update(PostmanSettings::class) {
 *     postmanToken = "new-token"
 * }
 *
 * // Save a module
 * binder.save(postman)
 * ```
 *
 * @see DefaultSettingBinder for the default implementation
 * @see Settings
 * @see StorageScope
 */
interface SettingBinder {
    /**
     * Reads the current settings for the given module type.
     *
     * Missing/unrecognized state yields the type's defaults (no crash — R-A-5).
     *
     * @param type the settings module class
     * @return the populated settings instance
     */
    fun <T : Settings> read(type: KClass<T>): T

    /**
     * Saves the settings, persisting each annotated property to its scoped
     * `PersistentStateComponent`. Fires [SettingsChangeListener] after save.
     *
     * @param settings the settings to persist
     */
    fun <T : Settings> save(settings: T)

    /**
     * Tries to read settings without creating defaults.
     *
     * @param type the settings module class
     * @return the current settings, or null if not set
     */
    fun <T : Settings> tryRead(type: KClass<T>): T?

    companion object {
        fun getInstance(project: Project): SettingBinder = project.service()
    }
}

/**
 * Reads settings for the reified type.
 *
 * Convenience for `binder.read(T::class)`.
 */
inline fun <reified T : Settings> SettingBinder.read(): T = read(T::class)

/**
 * Tries to read settings for the reified type.
 */
inline fun <reified T : Settings> SettingBinder.tryRead(): T? = tryRead(T::class)

/**
 * Updates settings using the given updater function.
 *
 * Reads, applies the updater, and saves.
 */
fun <T : Settings> SettingBinder.update(type: KClass<T>, updater: T.() -> Unit) {
    this.read(type).also(updater).let { this.save(it) }
}

/**
 * Typed convenience accessor: `project.settings<PostmanSettings>()`.
 *
 * Returns the module's current settings via [SettingBinder].
 */
inline fun <reified T : Settings> Project.settings(): T =
    SettingBinder.getInstance(this).read(T::class)
