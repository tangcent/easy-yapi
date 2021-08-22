package com.itangcent.idea.plugin.settings.xml

interface ProjectSettingsSupport {

    var postmanWorkspace: String?

    fun copyTo(newSetting: ProjectSettingsSupport) {
        newSetting.postmanWorkspace = this.postmanWorkspace
    }
}

class ProjectSettings : ProjectSettingsSupport {

    override var postmanWorkspace: String? = null

    fun copy(): ProjectSettings {
        val projectSettings = ProjectSettings()
        this.copyTo(projectSettings)
        return projectSettings
    }
}