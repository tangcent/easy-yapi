package com.itangcent.easyapi.settings.state

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings

/**
 * XML-based settings binder that combines application and project settings.
 * 
 * Reads and writes settings from both application-level and project-level
 * persistent state components. Project settings override application settings
 * where applicable.
 * 
 * @param project The IntelliJ project, or null for application-level only
 */
class XmlSettingBinder(
    private val project: Project?
) : SettingBinder {
    private val projectSettingsState: ProjectSettingsState? by lazy {
        project?.getService(ProjectSettingsState::class.java)
    }

    private val applicationSettingsState: ApplicationSettingsState by lazy {
        ApplicationManager.getApplication().getService(ApplicationSettingsState::class.java)
    }

    /**
     * Reads settings, combining application and project settings.
     * Project settings override application settings.
     * 
     * @return Combined settings object
     */
    override fun read(): Settings {
        return tryRead() ?: Settings()
    }

    /**
     * Saves settings to both application and project state.
     * Splits settings appropriately between the two levels.
     * 
     * @param settings The settings to save, or null to reset
     */
    override fun save(settings: Settings?) {
        if (settings == null) {
            projectSettingsState?.loadState(ProjectSettingsState.State())
            applicationSettingsState.loadState(ApplicationSettingsState.State())
            return
        }

        val applicationState = applicationSettingsState.state
        settings.copyTo(applicationState)
        applicationSettingsState.loadState(applicationState)

        val projectState = projectSettingsState?.state ?: ProjectSettingsState.State()
        settings.copyTo(projectState)
        projectSettingsState?.loadState(projectState)
    }

    /**
     * Attempts to read settings without creating defaults.
     * 
     * @return Combined settings, or null if not initialized
     */
    override fun tryRead(): Settings? {
        val projectState = projectSettingsState?.state
        val applicationState = applicationSettingsState.state
        val settings = Settings()
        applicationState.copyTo(settings)
        projectState?.copyTo(settings)
        return settings
    }
}
