package com.itangcent.easyapi.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.settings.state.XmlSettingBinder

/**
 * Default implementation of [SettingBinder] for project-level settings.
 *
 * A project-level service that provides access to plugin settings.
 * Uses [XmlSettingBinder] internally with caching for performance.
 *
 * ## Usage
 * ```kotlin
 * val settingBinder = SettingBinder.getInstance(project)
 * val settings = settingBinder.read()
 * ```
 *
 * @see SettingBinder for the interface
 * @see XmlSettingBinder for the underlying XML storage
 */
@Service(Service.Level.PROJECT)
class DefaultSettingBinder(
    private val project: Project
) : SettingBinder {
    companion object {
        /**
         * Gets the DefaultSettingBinder instance for the project.
         *
         * @param project The project
         * @return The DefaultSettingBinder instance
         */
        fun getInstance(project: Project): DefaultSettingBinder = project.service()
    }

    private val cachedSettingBinder by lazy { XmlSettingBinder(project).lazy(30_000L) }

    override fun read(): Settings {
        return cachedSettingBinder.read()
    }

    override fun save(settings: Settings?) {
        cachedSettingBinder.save(settings)
        project.messageBus.syncPublisher(SettingsChangeListener.TOPIC).settingsChanged()
    }

    override fun tryRead(): Settings? {
        return cachedSettingBinder.tryRead()
    }
}
