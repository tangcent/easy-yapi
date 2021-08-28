package com.itangcent.idea.plugin.settings

import com.google.inject.Inject
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.settings.xml.ApplicationSettings
import com.itangcent.idea.plugin.settings.xml.ApplicationSettingsComponent
import com.itangcent.idea.plugin.settings.xml.ProjectSettings
import com.itangcent.idea.plugin.settings.xml.ProjectSettingsComponent

class XmlSettingBinder : SettingBinder {

    @Inject
    private var project: Project? = null

    private val projectSettingsComponent: ProjectSettingsComponent? by lazy {
        project?.let { ServiceManager.getService(it, ProjectSettingsComponent::class.java) }
    }

    private val applicationSettingsComponent: ApplicationSettingsComponent by lazy {
        ServiceManager.getService(ApplicationSettingsComponent::class.java)
    }

    override fun read(): Settings {
        return tryRead() ?: Settings()
    }

    override fun save(t: Settings?) {
        if (t == null) {
            projectSettingsComponent?.loadState(null)
            applicationSettingsComponent.loadState(null)
            return
        }

        val applicationSettings = applicationSettingsComponent.state ?: ApplicationSettings()
        t.copyTo(applicationSettings)
        applicationSettingsComponent.loadState(applicationSettings)

        val projectSettings = projectSettingsComponent?.state ?: ProjectSettings()
        t.copyTo(projectSettings)
        projectSettingsComponent?.loadState(projectSettings)
    }

    override fun tryRead(): Settings? {
        val projectSettings = projectSettingsComponent?.state
        val applicationSettings = applicationSettingsComponent.state
        if (projectSettings == null && applicationSettings == null) {
            return null
        }

        val settings = Settings()
        applicationSettings?.copyTo(settings)
        projectSettings?.copyTo(settings)
        return settings
    }
}