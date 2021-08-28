package com.itangcent.idea.plugin.settings.xml

interface ProjectSettingsSupport {

    var postmanWorkspace: String?

    var yapiTokens: String?

    fun copyTo(newSetting: ProjectSettingsSupport) {
        newSetting.postmanWorkspace = this.postmanWorkspace
        this.yapiTokens?.let { newSetting.yapiTokens = it }
    }
}

class ProjectSettings : ProjectSettingsSupport {

    override var postmanWorkspace: String? = null

    override var yapiTokens: String? = null

    fun copy(): ProjectSettings {
        val projectSettings = ProjectSettings()
        this.copyTo(projectSettings)
        return projectSettings
    }
}