package com.itangcent.idea.plugin.settings

import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.utils.Charsets
import com.itangcent.idea.utils.ConfigurableLogger

class Settings {

    var pullNewestDataBefore: Boolean = false

    var methodDocEnable: Boolean = false

    //postman

    var postmanToken: String? = null

    var wrapCollection: Boolean = false

    var autoMergeScript: Boolean = false

    //intelligent

    var formExpanded: Boolean = true

    var readGetter: Boolean = false

    var inferEnable: Boolean = true

    var inferMaxDeep: Int = DEFAULT_INFER_MAX_DEEP

    //unit:s
    var httpTimeOut: Int = 5

    //enable to use recommend config
    var useRecommendConfig: Boolean = true

    var recommendConfigs: String = RecommendConfigReader.RECOMMEND_CONFIG_CODES.joinToString(",")

    var logLevel: Int = ConfigurableLogger.CoarseLogLevel.LOW.getLevel()

    var outputDemo: Boolean = true

    var outputCharset: String = Charsets.UTF_8.displayName()

    fun copy(): Settings {
        val newSetting = Settings()
        newSetting.postmanToken = this.postmanToken
        newSetting.wrapCollection = this.wrapCollection
        newSetting.autoMergeScript = this.autoMergeScript
        newSetting.pullNewestDataBefore = this.pullNewestDataBefore
        newSetting.methodDocEnable = this.methodDocEnable
        newSetting.formExpanded = this.formExpanded
        newSetting.readGetter = this.readGetter
        newSetting.inferEnable = this.inferEnable
        newSetting.inferMaxDeep = this.inferMaxDeep
        newSetting.httpTimeOut = this.httpTimeOut
        newSetting.useRecommendConfig = this.useRecommendConfig
        newSetting.recommendConfigs = this.recommendConfigs
        newSetting.logLevel = this.logLevel
        newSetting.outputDemo = this.outputDemo
        newSetting.outputCharset = this.outputCharset
        return newSetting
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Settings

        if (pullNewestDataBefore != other.pullNewestDataBefore) return false
        if (methodDocEnable != other.methodDocEnable) return false
        if (postmanToken != other.postmanToken) return false
        if (wrapCollection != other.wrapCollection) return false
        if (autoMergeScript != other.autoMergeScript) return false
        if (formExpanded != other.formExpanded) return false
        if (readGetter != other.readGetter) return false
        if (inferEnable != other.inferEnable) return false
        if (inferMaxDeep != other.inferMaxDeep) return false
        if (httpTimeOut != other.httpTimeOut) return false
        if (useRecommendConfig != other.useRecommendConfig) return false
        if (recommendConfigs != other.recommendConfigs) return false
        if (logLevel != other.logLevel) return false
        if (outputDemo != other.outputDemo) return false
        if (outputCharset != other.outputCharset) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pullNewestDataBefore.hashCode()
        result = 31 * result + methodDocEnable.hashCode()
        result = 31 * result + (postmanToken?.hashCode() ?: 0)
        result = 31 * result + wrapCollection.hashCode()
        result = 31 * result + autoMergeScript.hashCode()
        result = 31 * result + formExpanded.hashCode()
        result = 31 * result + readGetter.hashCode()
        result = 31 * result + inferEnable.hashCode()
        result = 31 * result + inferMaxDeep
        result = 31 * result + httpTimeOut
        result = 31 * result + useRecommendConfig.hashCode()
        result = 31 * result + recommendConfigs.hashCode()
        result = 31 * result + logLevel
        result = 31 * result + outputDemo.hashCode()
        result = 31 * result + outputCharset.hashCode()
        return result
    }

    companion object {
        const val DEFAULT_INFER_MAX_DEEP = 4
    }
}