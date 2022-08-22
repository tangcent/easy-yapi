package com.itangcent.idea.plugin.settings

import com.itangcent.idea.plugin.settings.helper.RecommendConfigLoader
import com.itangcent.idea.plugin.settings.xml.ApplicationSettingsSupport
import com.itangcent.idea.plugin.settings.xml.ProjectSettingsSupport
import com.itangcent.idea.utils.Charsets

class Settings : ProjectSettingsSupport, ApplicationSettingsSupport {

    override var methodDocEnable: Boolean = false

    override var genericEnable: Boolean = false

    override var feignEnable: Boolean = false

    override var jaxrsEnable: Boolean = true

    //postman

    override var pullNewestDataBefore: Boolean = false

    override var postmanToken: String? = null

    override var postmanWorkspace: String? = null

    override var postmanExportMode: String? = PostmanExportMode.COPY.name

    override var postmanCollections: String? = null

    override var postmanBuildExample: Boolean = true

    override var wrapCollection: Boolean = false

    override var autoMergeScript: Boolean = false

    override var postmanJson5FormatType: String = PostmanJson5FormatType.EXAMPLE_ONLY.name

    //region intelligent--------------------------

    override var queryExpanded: Boolean = true

    override var formExpanded: Boolean = true

    override var readGetter: Boolean = false

    override var readSetter: Boolean = false

    override var inferEnable: Boolean = false

    override var inferMaxDeep: Int = DEFAULT_INFER_MAX_DEEP

    override var selectedOnly: Boolean = false

    //endregion

    //region yapi--------------------------

    override var yapiServer: String? = null

    override var yapiTokens: String? = null

    override var enableUrlTemplating: Boolean = true

    override var switchNotice: Boolean = true

    override var loginMode: Boolean = false

    override var yapiExportMode: String = YapiExportMode.ALWAYS_UPDATE.name

    override var yapiReqBodyJson5: Boolean = false

    override var yapiResBodyJson5: Boolean = false

    //endregion

    //region http--------------------------

    //unit:s
    override var httpTimeOut: Int = 5

    override var trustHosts: Array<String> = DEFAULT_TRUST_HOSTS

    //endregion

    /**
     * enable to use recommend config
     */
    override var useRecommendConfig: Boolean = true

    override var recommendConfigs: String = RecommendConfigLoader.defaultCodes()

    override var logLevel: Int = 50

    /**
     * Charset for out put file
     */
    override var logCharset: String = Charsets.UTF_8.displayName()

    // markdown

    override var outputDemo: Boolean = true

    /**
     * Charset for out put file
     */
    override var outputCharset: String = Charsets.UTF_8.displayName()

    override var markdownFormatType: String = MarkdownFormatType.SIMPLE.name

    override var builtInConfig: String? = null

    fun copy(): Settings {
        val newSetting = Settings()
        this.copyTo(newSetting as ProjectSettingsSupport)
        this.copyTo(newSetting as ApplicationSettingsSupport)
        return newSetting
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Settings

        if (methodDocEnable != other.methodDocEnable) return false
        if (genericEnable != other.genericEnable) return false
        if (feignEnable != other.feignEnable) return false
        if (jaxrsEnable != other.jaxrsEnable) return false
        if (pullNewestDataBefore != other.pullNewestDataBefore) return false
        if (postmanToken != other.postmanToken) return false
        if (postmanWorkspace != other.postmanWorkspace) return false
        if (postmanExportMode != other.postmanExportMode) return false
        if (postmanCollections != other.postmanCollections) return false
        if (postmanBuildExample != other.postmanBuildExample) return false
        if (wrapCollection != other.wrapCollection) return false
        if (autoMergeScript != other.autoMergeScript) return false
        if (postmanJson5FormatType != other.postmanJson5FormatType) return false
        if (queryExpanded != other.queryExpanded) return false
        if (formExpanded != other.formExpanded) return false
        if (readGetter != other.readGetter) return false
        if (readSetter != other.readSetter) return false
        if (inferEnable != other.inferEnable) return false
        if (inferMaxDeep != other.inferMaxDeep) return false
        if (selectedOnly != other.selectedOnly) return false
        if (yapiServer != other.yapiServer) return false
        if (yapiTokens != other.yapiTokens) return false
        if (enableUrlTemplating != other.enableUrlTemplating) return false
        if (switchNotice != other.switchNotice) return false
        if (loginMode != other.loginMode) return false
        if (yapiExportMode != other.yapiExportMode) return false
        if (yapiReqBodyJson5 != other.yapiReqBodyJson5) return false
        if (yapiResBodyJson5 != other.yapiResBodyJson5) return false
        if (httpTimeOut != other.httpTimeOut) return false
        if (!trustHosts.contentEquals(other.trustHosts)) return false
        if (useRecommendConfig != other.useRecommendConfig) return false
        if (recommendConfigs != other.recommendConfigs) return false
        if (logLevel != other.logLevel) return false
        if (logCharset != other.logCharset) return false
        if (outputDemo != other.outputDemo) return false
        if (outputCharset != other.outputCharset) return false
        if (markdownFormatType != other.markdownFormatType) return false
        if (builtInConfig != other.builtInConfig) return false

        return true
    }

    override fun hashCode(): Int {
        var result = methodDocEnable.hashCode()
        result = 31 * result + genericEnable.hashCode()
        result = 31 * result + feignEnable.hashCode()
        result = 31 * result + jaxrsEnable.hashCode()
        result = 31 * result + pullNewestDataBefore.hashCode()
        result = 31 * result + (postmanToken?.hashCode() ?: 0)
        result = 31 * result + (postmanWorkspace?.hashCode() ?: 0)
        result = 31 * result + (postmanExportMode?.hashCode() ?: 0)
        result = 31 * result + (postmanCollections?.hashCode() ?: 0)
        result = 31 * result + postmanBuildExample.hashCode()
        result = 31 * result + wrapCollection.hashCode()
        result = 31 * result + autoMergeScript.hashCode()
        result = 31 * result + postmanJson5FormatType.hashCode()
        result = 31 * result + queryExpanded.hashCode()
        result = 31 * result + formExpanded.hashCode()
        result = 31 * result + readGetter.hashCode()
        result = 31 * result + readSetter.hashCode()
        result = 31 * result + inferEnable.hashCode()
        result = 31 * result + inferMaxDeep
        result = 31 * result + selectedOnly.hashCode()
        result = 31 * result + (yapiServer?.hashCode() ?: 0)
        result = 31 * result + (yapiTokens?.hashCode() ?: 0)
        result = 31 * result + enableUrlTemplating.hashCode()
        result = 31 * result + switchNotice.hashCode()
        result = 31 * result + loginMode.hashCode()
        result = 31 * result + yapiExportMode.hashCode()
        result = 31 * result + yapiReqBodyJson5.hashCode()
        result = 31 * result + yapiResBodyJson5.hashCode()
        result = 31 * result + httpTimeOut
        result = 31 * result + trustHosts.contentHashCode()
        result = 31 * result + useRecommendConfig.hashCode()
        result = 31 * result + recommendConfigs.hashCode()
        result = 31 * result + logLevel
        result = 31 * result + logCharset.hashCode()
        result = 31 * result + outputDemo.hashCode()
        result = 31 * result + outputCharset.hashCode()
        result = 31 * result + markdownFormatType.hashCode()
        result = 31 * result + (builtInConfig?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Settings(methodDocEnable=$methodDocEnable, genericEnable=$genericEnable, feignEnable=$feignEnable, jaxrsEnable=$jaxrsEnable, pullNewestDataBefore=$pullNewestDataBefore, postmanToken=$postmanToken, postmanWorkspace=$postmanWorkspace, postmanExportMode=$postmanExportMode, postmanCollections=$postmanCollections, wrapCollection=$wrapCollection, autoMergeScript=$autoMergeScript, postmanJson5FormatType='$postmanJson5FormatType', queryExpanded=$queryExpanded, formExpanded=$formExpanded, readGetter=$readGetter, readSetter=$readSetter, inferEnable=$inferEnable, inferMaxDeep=$inferMaxDeep, selectedOnly=$selectedOnly, yapiServer=$yapiServer, yapiTokens=$yapiTokens, enableUrlTemplating=$enableUrlTemplating, switchNotice=$switchNotice, loginMode=$loginMode, yapiExportMode=$yapiExportMode, yapiReqBodyJson5=$yapiReqBodyJson5, yapiResBodyJson5=$yapiResBodyJson5, httpTimeOut=$httpTimeOut, trustHosts=${trustHosts.contentToString()}, useRecommendConfig=$useRecommendConfig, recommendConfigs='$recommendConfigs', logLevel=$logLevel, logCharset='$logCharset', outputDemo=$outputDemo, outputCharset='$outputCharset', markdownFormatType='$markdownFormatType', builtInConfig=$builtInConfig)"
    }

    companion object {
        const val DEFAULT_INFER_MAX_DEEP = 4

        val DEFAULT_TRUST_HOSTS: Array<String> =
            arrayOf(
                "https://raw.githubusercontent.com/tangcent",
                "https://api.getpostman.com",
                "https://localhost",
                "http://localhost",
                "https://127.0.0.1",
                "http://127.0.0.1",
            )
    }
}