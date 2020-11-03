package com.itangcent.idea.plugin.settings

import com.itangcent.idea.plugin.config.RecommendConfigLoader
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

    var yapiServer: String? = null

    var yapiTokens: String? = null

    var enableUrlTemplating: Boolean = true

    var switchNotice: Boolean = true

    //unit:s
    var httpTimeOut: Int = 5

    //enable to use recommend config
    var useRecommendConfig: Boolean = true

    var recommendConfigs: String = RecommendConfigLoader.defaultCodes()

    var logLevel: Int = ConfigurableLogger.CoarseLogLevel.LOW.getLevel()

    // markdown

    var outputDemo: Boolean = true

    var outputCharset: String = Charsets.UTF_8.displayName()

    var markdownFormatType: String = MarkdownFormatType.SIMPLE.name

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
        newSetting.yapiServer = this.yapiServer
        newSetting.yapiTokens = this.yapiTokens
        newSetting.enableUrlTemplating = this.enableUrlTemplating
        newSetting.switchNotice = this.switchNotice
        newSetting.httpTimeOut = this.httpTimeOut
        newSetting.useRecommendConfig = this.useRecommendConfig
        newSetting.recommendConfigs = this.recommendConfigs
        newSetting.logLevel = this.logLevel
        newSetting.outputDemo = this.outputDemo
        newSetting.outputCharset = this.outputCharset
        newSetting.markdownFormatType = this.markdownFormatType
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
        if (yapiServer != other.yapiServer) return false
        if (yapiTokens != other.yapiTokens) return false
        if (enableUrlTemplating != other.enableUrlTemplating) return false
        if (switchNotice != other.switchNotice) return false
        if (httpTimeOut != other.httpTimeOut) return false
        if (useRecommendConfig != other.useRecommendConfig) return false
        if (recommendConfigs != other.recommendConfigs) return false
        if (logLevel != other.logLevel) return false
        if (outputDemo != other.outputDemo) return false
        if (outputCharset != other.outputCharset) return false
        if (markdownFormatType != other.markdownFormatType) return false

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
        result = 31 * result + (yapiServer?.hashCode() ?: 0)
        result = 31 * result + (yapiTokens?.hashCode() ?: 0)
        result = 31 * result + enableUrlTemplating.hashCode()
        result = 31 * result + switchNotice.hashCode()
        result = 31 * result + httpTimeOut
        result = 31 * result + useRecommendConfig.hashCode()
        result = 31 * result + recommendConfigs.hashCode()
        result = 31 * result + logLevel
        result = 31 * result + outputDemo.hashCode()
        result = 31 * result + outputCharset.hashCode()
        result = 31 * result + markdownFormatType.hashCode()
        return result
    }


    companion object {
        const val DEFAULT_INFER_MAX_DEEP = 4
    }
}