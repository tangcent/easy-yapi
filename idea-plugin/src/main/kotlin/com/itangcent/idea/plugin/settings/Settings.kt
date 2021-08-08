package com.itangcent.idea.plugin.settings

import com.itangcent.idea.plugin.settings.helper.RecommendConfigLoader
import com.itangcent.idea.utils.Charsets

class Settings {

    var methodDocEnable: Boolean = false

    var genericEnable: Boolean = false

    //postman

    var pullNewestDataBefore: Boolean = false

    var postmanToken: String? = null

    var postmanWorkspaces: String? = null

    var wrapCollection: Boolean = false

    var autoMergeScript: Boolean = false

    var postmanJson5FormatType: String = PostmanJson5FormatType.EXAMPLE_ONLY.name

    //region intelligent

    var queryExpanded: Boolean = true

    var formExpanded: Boolean = true

    var readGetter: Boolean = false

    var readSetter: Boolean = false

    var inferEnable: Boolean = false

    var inferMaxDeep: Int = DEFAULT_INFER_MAX_DEEP

    //endregion

    //yapi

    var yapiServer: String? = null

    var yapiTokens: String? = null

    var enableUrlTemplating: Boolean = true

    var switchNotice: Boolean = true

    var loginMode: Boolean = false

    var yapiReqBodyJson5: Boolean = false

    var yapiResBodyJson5: Boolean = false

    //region http--------------------------

    //unit:s
    var httpTimeOut: Int = 5

    var trustHosts: Array<String> = DEFAULT_TRUST_HOSTS

    //endregion

    //enable to use recommend config
    var useRecommendConfig: Boolean = true

    var recommendConfigs: String = RecommendConfigLoader.defaultCodes()

    var logLevel: Int = 50

    // markdown

    var outputDemo: Boolean = true

    var outputCharset: String = Charsets.UTF_8.displayName()

    var markdownFormatType: String = MarkdownFormatType.SIMPLE.name

    var builtInConfig: String? = null


    fun copy(): Settings {
        val newSetting = Settings()
        newSetting.postmanToken = this.postmanToken
        newSetting.postmanWorkspaces = this.postmanWorkspaces
        newSetting.wrapCollection = this.wrapCollection
        newSetting.autoMergeScript = this.autoMergeScript
        newSetting.postmanJson5FormatType = this.postmanJson5FormatType
        newSetting.pullNewestDataBefore = this.pullNewestDataBefore
        newSetting.methodDocEnable = this.methodDocEnable
        newSetting.genericEnable = this.genericEnable
        newSetting.queryExpanded = this.queryExpanded
        newSetting.formExpanded = this.formExpanded
        newSetting.readGetter = this.readGetter
        newSetting.readSetter = this.readSetter
        newSetting.inferEnable = this.inferEnable
        newSetting.inferMaxDeep = this.inferMaxDeep
        newSetting.yapiServer = this.yapiServer
        newSetting.yapiTokens = this.yapiTokens
        newSetting.enableUrlTemplating = this.enableUrlTemplating
        newSetting.switchNotice = this.switchNotice
        newSetting.loginMode = this.loginMode
        newSetting.yapiReqBodyJson5 = this.yapiReqBodyJson5
        newSetting.yapiResBodyJson5 = this.yapiResBodyJson5
        newSetting.httpTimeOut = this.httpTimeOut
        newSetting.useRecommendConfig = this.useRecommendConfig
        newSetting.recommendConfigs = this.recommendConfigs
        newSetting.logLevel = this.logLevel
        newSetting.outputDemo = this.outputDemo
        newSetting.outputCharset = this.outputCharset
        newSetting.markdownFormatType = this.markdownFormatType
        newSetting.builtInConfig = this.builtInConfig
        newSetting.trustHosts = this.trustHosts
        return newSetting
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Settings

        if (pullNewestDataBefore != other.pullNewestDataBefore) return false
        if (methodDocEnable != other.methodDocEnable) return false
        if (genericEnable != other.genericEnable) return false
        if (postmanToken != other.postmanToken) return false
        if (postmanWorkspaces != other.postmanWorkspaces) return false
        if (wrapCollection != other.wrapCollection) return false
        if (autoMergeScript != other.autoMergeScript) return false
        if (postmanJson5FormatType != other.postmanJson5FormatType) return false
        if (queryExpanded != other.queryExpanded) return false
        if (formExpanded != other.formExpanded) return false
        if (readGetter != other.readGetter) return false
        if (readSetter != other.readSetter) return false
        if (inferEnable != other.inferEnable) return false
        if (inferMaxDeep != other.inferMaxDeep) return false
        if (yapiServer != other.yapiServer) return false
        if (yapiTokens != other.yapiTokens) return false
        if (enableUrlTemplating != other.enableUrlTemplating) return false
        if (switchNotice != other.switchNotice) return false
        if (loginMode != other.loginMode) return false
        if (yapiReqBodyJson5 != other.yapiReqBodyJson5) return false
        if (yapiResBodyJson5 != other.yapiResBodyJson5) return false
        if (httpTimeOut != other.httpTimeOut) return false
        if (useRecommendConfig != other.useRecommendConfig) return false
        if (recommendConfigs != other.recommendConfigs) return false
        if (logLevel != other.logLevel) return false
        if (outputDemo != other.outputDemo) return false
        if (outputCharset != other.outputCharset) return false
        if (markdownFormatType != other.markdownFormatType) return false
        if (builtInConfig != other.builtInConfig) return false
        if (!trustHosts.contentEquals(other.trustHosts)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pullNewestDataBefore.hashCode()
        result = 31 * result + methodDocEnable.hashCode()
        result = 31 * result + genericEnable.hashCode()
        result = 31 * result + (postmanToken?.hashCode() ?: 0)
        result = 31 * result + wrapCollection.hashCode()
        result = 31 * result + autoMergeScript.hashCode()
        result = 31 * result + postmanJson5FormatType.hashCode()
        result = 31 * result + queryExpanded.hashCode()
        result = 31 * result + formExpanded.hashCode()
        result = 31 * result + readGetter.hashCode()
        result = 31 * result + readSetter.hashCode()
        result = 31 * result + inferEnable.hashCode()
        result = 31 * result + inferMaxDeep
        result = 31 * result + (yapiServer?.hashCode() ?: 0)
        result = 31 * result + (yapiTokens?.hashCode() ?: 0)
        result = 31 * result + enableUrlTemplating.hashCode()
        result = 31 * result + switchNotice.hashCode()
        result = 31 * result + loginMode.hashCode()
        result = 31 * result + yapiReqBodyJson5.hashCode()
        result = 31 * result + yapiResBodyJson5.hashCode()
        result = 31 * result + httpTimeOut
        result = 31 * result + useRecommendConfig.hashCode()
        result = 31 * result + recommendConfigs.hashCode()
        result = 31 * result + logLevel
        result = 31 * result + outputDemo.hashCode()
        result = 31 * result + outputCharset.hashCode()
        result = 31 * result + markdownFormatType.hashCode()
        result = 31 * result + builtInConfig.hashCode()
        result = 31 * result + trustHosts.hashCode()
        return result
    }

    companion object {
        const val DEFAULT_INFER_MAX_DEEP = 4

        val DEFAULT_TRUST_HOSTS: Array<String> =
                arrayOf("https://raw.githubusercontent.com/tangcent",
                        "https://api.getpostman.com",
                        "https://localhost",
                        "http://localhost",
                        "https://127.0.0.1",
                        "http://127.0.0.1",
                )
    }
}