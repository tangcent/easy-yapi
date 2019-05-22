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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Settings

        if (pullNewestDataBefore != other.pullNewestDataBefore) return false
        if (postmanToken != other.postmanToken) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pullNewestDataBefore?.hashCode() ?: 0
        result = 31 * result + (postmanToken?.hashCode() ?: 0)
        return result
    }

}