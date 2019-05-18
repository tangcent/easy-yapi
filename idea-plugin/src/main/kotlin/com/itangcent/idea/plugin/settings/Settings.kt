package com.itangcent.idea.plugin.settings

class Settings {

    var pullNewestDataBefore: Boolean? = null

    var postmanToken: String? = null

    fun copy(): Settings {
        val newSetting = Settings()
        newSetting.postmanToken = this.postmanToken
        newSetting.pullNewestDataBefore = this.pullNewestDataBefore
        return newSetting
    }

}