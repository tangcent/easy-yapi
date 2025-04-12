package com.itangcent.idea.plugin.settings

import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper
import com.itangcent.idea.plugin.settings.helper.RecommendConfigLoader
import com.itangcent.idea.plugin.settings.xml.ApplicationSettingsSupport
import com.itangcent.idea.plugin.settings.xml.ProjectSettingsSupport
import com.itangcent.idea.utils.Charsets

class Settings : ProjectSettingsSupport, ApplicationSettingsSupport {

    override var methodDocEnable: Boolean = false

    override var genericEnable: Boolean = false

    override var feignEnable: Boolean = false

    override var jaxrsEnable: Boolean = true

    override var actuatorEnable: Boolean = false

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

    override var unsafeSsl: Boolean = false

    override var httpClient: String = "Apache"

    //endregion

    /**
     * enable to use recommend config
     */
    override var useRecommendConfig: Boolean = true

    override var recommendConfigs: String = RecommendConfigLoader.defaultCodes()

    override var logLevel: Int = 50

    /**
     * Type of logger to use for displaying logs
     * @see com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper.LoggerConsoleType
     */
    override var loggerConsoleType: String = CommonSettingsHelper.LoggerConsoleType.SINGLE_CONSOLE.name

    /**
     * Charset for output file
     */
    override var outputCharset: String = Charsets.UTF_8.displayName()

    // markdown

    override var outputDemo: Boolean = true

    override var markdownFormatType: String = MarkdownFormatType.SIMPLE.name

    override var builtInConfig: String? = null

    override var remoteConfig: Array<String> = emptyArray()

    //region AI integration--------------------------

    /**
     * AI service provider (e.g., OpenAI, DeepSeek, etc.)
     */
    override var aiProvider: String? = null

    /**
     * AI service API token
     */
    override var aiToken: String? = null

    /**
     * Local LLM server URL (for LocalLLM provider)
     */
    override var aiLocalServerUrl: String? = null

    /**
     * Enable AI integration
     */
    override var aiEnable: Boolean = false

    /**
     * AI model to use (e.g., gpt-3.5-turbo, gpt-4, etc.)
     */
    override var aiModel: String? = null

    /**
     * Enable caching of AI API responses
     */
    override var aiEnableCache: Boolean = false

    /**
     * Enable API translation feature
     */
    override var aiTranslationEnabled: Boolean = false

    /**
     * Target language for API translation
     */
    override var aiTranslationTargetLanguage: String? = null

    /**
     * Enable AI for method return type inference
     */
    override var aiMethodInferEnabled: Boolean = false

    //endregion AI integration--------------------------

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
        if (actuatorEnable != other.actuatorEnable) return false
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
        if (unsafeSsl != other.unsafeSsl) return false
        if (httpClient != other.httpClient) return false
        if (!trustHosts.contentEquals(other.trustHosts)) return false
        if (useRecommendConfig != other.useRecommendConfig) return false
        if (recommendConfigs != other.recommendConfigs) return false
        if (logLevel != other.logLevel) return false
        if (loggerConsoleType != other.loggerConsoleType) return false
        if (outputDemo != other.outputDemo) return false
        if (outputCharset != other.outputCharset) return false
        if (markdownFormatType != other.markdownFormatType) return false
        if (builtInConfig != other.builtInConfig) return false
        if (!remoteConfig.contentEquals(other.remoteConfig)) return false
        if (aiProvider != other.aiProvider) return false
        if (aiToken != other.aiToken) return false
        if (aiLocalServerUrl != other.aiLocalServerUrl) return false
        if (aiEnable != other.aiEnable) return false
        if (aiModel != other.aiModel) return false
        if (aiEnableCache != other.aiEnableCache) return false
        if (aiTranslationEnabled != other.aiTranslationEnabled) return false
        if (aiTranslationTargetLanguage != other.aiTranslationTargetLanguage) return false
        if (aiMethodInferEnabled != other.aiMethodInferEnabled) return false

        return true
    }

    override fun hashCode(): Int {
        var result = methodDocEnable.hashCode()
        result = 31 * result + genericEnable.hashCode()
        result = 31 * result + feignEnable.hashCode()
        result = 31 * result + jaxrsEnable.hashCode()
        result = 31 * result + actuatorEnable.hashCode()
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
        result = 31 * result + unsafeSsl.hashCode()
        result = 31 * result + httpClient.hashCode()
        result = 31 * result + trustHosts.contentHashCode()
        result = 31 * result + useRecommendConfig.hashCode()
        result = 31 * result + recommendConfigs.hashCode()
        result = 31 * result + logLevel
        result = 31 * result + loggerConsoleType.hashCode()
        result = 31 * result + outputDemo.hashCode()
        result = 31 * result + outputCharset.hashCode()
        result = 31 * result + markdownFormatType.hashCode()
        result = 31 * result + (builtInConfig?.hashCode() ?: 0)
        result = 31 * result + remoteConfig.contentHashCode()
        result = 31 * result + (aiProvider?.hashCode() ?: 0)
        result = 31 * result + (aiToken?.hashCode() ?: 0)
        result = 31 * result + (aiLocalServerUrl?.hashCode() ?: 0)
        result = 31 * result + aiEnable.hashCode()
        result = 31 * result + (aiModel?.hashCode() ?: 0)
        result = 31 * result + aiEnableCache.hashCode()
        result = 31 * result + aiTranslationEnabled.hashCode()
        result = 31 * result + (aiTranslationTargetLanguage?.hashCode() ?: 0)
        result = 31 * result + aiMethodInferEnabled.hashCode()
        return result
    }

    override fun toString(): String {
        return "Settings(methodDocEnable=$methodDocEnable, genericEnable=$genericEnable, " +
                "feignEnable=$feignEnable, " +
                "jaxrsEnable=$jaxrsEnable, actuatorEnable=$actuatorEnable, " +
                "pullNewestDataBefore=$pullNewestDataBefore, " +
                "postmanToken=$postmanToken, postmanWorkspace=$postmanWorkspace, " +
                "postmanExportMode=$postmanExportMode, " +
                "postmanCollections=$postmanCollections, postmanBuildExample=$postmanBuildExample, " +
                "wrapCollection=$wrapCollection, autoMergeScript=$autoMergeScript, " +
                "postmanJson5FormatType='$postmanJson5FormatType', " +
                "queryExpanded=$queryExpanded, formExpanded=$formExpanded, " +
                "readGetter=$readGetter, readSetter=$readSetter, " +
                "inferEnable=$inferEnable, inferMaxDeep=$inferMaxDeep, " +
                "selectedOnly=$selectedOnly, yapiServer=$yapiServer, " +
                "apiTokens=$yapiTokens, enableUrlTemplating=$enableUrlTemplating, " +
                "switchNotice=$switchNotice, loginMode=$loginMode, " +
                "yapiExportMode='$yapiExportMode', yapiReqBodyJson5=$yapiReqBodyJson5, " +
                "yapiResBodyJson5=$yapiResBodyJson5, " +
                "httpTimeOut=$httpTimeOut, unsafeSsl=$unsafeSsl, " +
                "httpClient='$httpClient', trustHosts=${trustHosts.contentToString()}," +
                "useRecommendConfig=$useRecommendConfig, " +
                "recommendConfigs='$recommendConfigs', logLevel=$logLevel, " +
                "loggerType=$loggerConsoleType, " +
                "outputDemo=$outputDemo, " +
                "outputCharset='$outputCharset', markdownFormatType='$markdownFormatType', " +
                "builtInConfig=$builtInConfig, " +
                "remoteConfig=${remoteConfig.contentToString()}, " +
                "aiProvider=$aiProvider, " +
                "aiToken=$aiToken, " +
                "aiLocalServerUrl=$aiLocalServerUrl, " +
                "aiEnable=$aiEnable, " +
                "aiModel=$aiModel, " +
                "aiEnableCache=$aiEnableCache, " +
                "aiTranslationEnabled=$aiTranslationEnabled, " +
                "aiTranslationTargetLanguage=$aiTranslationTargetLanguage, " +
                "aiMethodInferEnabled=$aiMethodInferEnabled)"
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
                "https://api.deepseek.com",
                "https://api.openai.com",
            )
    }
}