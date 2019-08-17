package com.itangcent.idea.plugin.settings

import com.itangcent.idea.utils.ConfigurableLogger

class Settings {

    var pullNewestDataBefore: Boolean = false

    var methodDocEnable: Boolean = false

    var postmanToken: String? = null

    var readGetter: Boolean = false

    var inferEnable: Boolean = true

    var inferMaxDeep: Int = DEFAULT_INFER_MAX_DEEP

    //unit:s
    var httpTimeOut: Int = 5

    //enable to use recommend config
    var useRecommendConfig: Boolean = true

    var logLevel: Int = ConfigurableLogger.CoarseLogLevel.LOW.getLevel()

    fun copy(): Settings {
        val newSetting = Settings()
        newSetting.postmanToken = this.postmanToken
        newSetting.pullNewestDataBefore = this.pullNewestDataBefore
        newSetting.methodDocEnable = this.methodDocEnable
        newSetting.readGetter = this.readGetter
        newSetting.inferEnable = this.inferEnable
        newSetting.inferMaxDeep = this.inferMaxDeep
        newSetting.httpTimeOut = this.httpTimeOut
        newSetting.useRecommendConfig = this.useRecommendConfig
        newSetting.logLevel = this.logLevel
        return newSetting
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Settings

        if (pullNewestDataBefore != other.pullNewestDataBefore) return false
        if (methodDocEnable != other.methodDocEnable) return false
        if (postmanToken != other.postmanToken) return false
        if (readGetter != other.readGetter) return false
        if (inferEnable != other.inferEnable) return false
        if (inferMaxDeep != other.inferMaxDeep) return false
        if (httpTimeOut != other.httpTimeOut) return false
        if (useRecommendConfig != other.useRecommendConfig) return false
        if (logLevel != other.logLevel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pullNewestDataBefore.hashCode()
        result = 31 * result + methodDocEnable.hashCode()
        result = 31 * result + (postmanToken?.hashCode() ?: 0)
        result = 31 * result + readGetter.hashCode()
        result = 31 * result + inferEnable.hashCode()
        result = 31 * result + inferMaxDeep
        result = 31 * result + httpTimeOut
        result = 31 * result + useRecommendConfig.hashCode()
        result = 31 * result + logLevel
        return result
    }


    companion object {
        const val DEFAULT_INFER_MAX_DEEP = 4
    }
}