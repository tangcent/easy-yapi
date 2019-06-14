package com.itangcent.idea.plugin.settings

class Settings {

    var pullNewestDataBefore: Boolean? = null

    var postmanToken: String? = null

    var readGetter: Boolean? = null

    var inferEnable: Boolean? = null

    var inferMaxDeep: Int? = null

    var yapiServer: String? = null

    var yapiTokens: String? = null

    //unit:s
    var httpTimeOut: Int? = null

    //enable to use recommend config:
    //like jackson/gson
    var useRecommendConfig: Boolean? = true

    var logLevel: Int? = null

    fun copy(): Settings {
        val newSetting = Settings()
        newSetting.postmanToken = this.postmanToken
        newSetting.pullNewestDataBefore = this.pullNewestDataBefore
        newSetting.readGetter = this.readGetter
        newSetting.inferEnable = this.inferEnable
        newSetting.inferMaxDeep = this.inferMaxDeep
        newSetting.yapiServer = this.yapiServer
        newSetting.yapiTokens = this.yapiTokens
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
        if (postmanToken != other.postmanToken) return false
        if (readGetter != other.readGetter) return false
        if (inferEnable != other.inferEnable) return false
        if (inferMaxDeep != other.inferMaxDeep) return false
        if (yapiServer != other.yapiServer) return false
        if (yapiTokens != other.yapiTokens) return false
        if (httpTimeOut != other.httpTimeOut) return false
        if (useRecommendConfig != other.useRecommendConfig) return false
        if (logLevel != other.logLevel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pullNewestDataBefore?.hashCode() ?: 0
        result = 31 * result + (postmanToken?.hashCode() ?: 0)
        result = 31 * result + (readGetter?.hashCode() ?: 0)
        result = 31 * result + (inferEnable?.hashCode() ?: 0)
        result = 31 * result + (inferMaxDeep ?: 0)
        result = 31 * result + (httpTimeOut ?: 0)
        result = 31 * result + (yapiServer?.hashCode() ?: 0)
        result = 31 * result + (yapiTokens?.hashCode() ?: 0)
        result = 31 * result + (useRecommendConfig?.hashCode() ?: 0)
        result = 31 * result + (logLevel ?: 0)
        return result
    }

    companion object {
        const val DEFAULT_INFER_MAX_DEEP = 3
    }
}