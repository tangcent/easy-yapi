package com.itangcent.easyapi.settings

import com.itangcent.easyapi.recommend.RecommendPresetRegistry
import com.itangcent.easyapi.settings.state.ApplicationSettingsSupport
import com.itangcent.easyapi.settings.state.ProjectSettingsSupport

/**
 * Plugin settings containing all configuration options.
 *
 * This data class holds both project-level and application-level settings:
 * - Framework enablement (Feign, JAX-RS, Actuator)
 * - Postman export configuration
 * - HTTP client settings
 * - Output formatting options
 *
 * Settings are loaded via [DefaultSettingBinder] which merges
 * project and application settings.
 *
 * @see DefaultSettingBinder for settings loading
 * @see ProjectSettingsSupport for project-level settings
 * @see ApplicationSettingsSupport for application-level settings
 */
data class Settings(
    override var feignEnable: Boolean = false,
    override var jaxrsEnable: Boolean = true,
    override var actuatorEnable: Boolean = false,
    override var postmanToken: String? = null,
    override var postmanWorkspace: String? = null,
    override var postmanExportMode: String? = PostmanExportMode.CREATE_NEW.name,
    override var postmanCollections: String? = null,
    override var postmanBuildExample: Boolean = true,
    override var wrapCollection: Boolean = false,
    override var autoMergeScript: Boolean = false,
    override var postmanJson5FormatType: String = PostmanJson5FormatType.EXAMPLE_ONLY.name,
    override var queryExpanded: Boolean = true,
    override var formExpanded: Boolean = true,
    override var pathMulti: String = "ALL",
    override var inferReturnMain: Boolean = true,
    override var enableUrlTemplating: Boolean = true,
    override var switchNotice: Boolean = true,
    override var httpTimeOut: Int = 5,
    override var unsafeSsl: Boolean = false,
    override var httpClient: String = HttpClientType.APACHE.value,
    override var recommendConfigs: String = defaultRecommendCodes(),
    override var logLevel: Int = 50,
    override var outputDemo: Boolean = true,
    override var outputCharset: String = "UTF-8",
    override var markdownFormatType: String = MarkdownFormatType.SIMPLE.name,
    override var builtInConfig: String? = null,
    override var remoteConfig: Array<String> = emptyArray()
) : ProjectSettingsSupport, ApplicationSettingsSupport {

    companion object {
        private fun defaultRecommendCodes(): String {
            return RecommendPresetRegistry.allPresets()
                .filter { it.defaultEnabled }
                .joinToString(",") { it.code }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Settings

        if (feignEnable != other.feignEnable) return false
        if (jaxrsEnable != other.jaxrsEnable) return false
        if (actuatorEnable != other.actuatorEnable) return false
        if (postmanBuildExample != other.postmanBuildExample) return false
        if (wrapCollection != other.wrapCollection) return false
        if (autoMergeScript != other.autoMergeScript) return false
        if (queryExpanded != other.queryExpanded) return false
        if (formExpanded != other.formExpanded) return false
        if (pathMulti != other.pathMulti) return false
        if (inferReturnMain != other.inferReturnMain) return false
        if (enableUrlTemplating != other.enableUrlTemplating) return false
        if (switchNotice != other.switchNotice) return false
        if (httpTimeOut != other.httpTimeOut) return false
        if (unsafeSsl != other.unsafeSsl) return false
        if (logLevel != other.logLevel) return false
        if (outputDemo != other.outputDemo) return false
        if (postmanToken != other.postmanToken) return false
        if (postmanWorkspace != other.postmanWorkspace) return false
        if (postmanExportMode != other.postmanExportMode) return false
        if (postmanCollections != other.postmanCollections) return false
        if (postmanJson5FormatType != other.postmanJson5FormatType) return false
        if (httpClient != other.httpClient) return false
        if (recommendConfigs != other.recommendConfigs) return false
        if (outputCharset != other.outputCharset) return false
        if (markdownFormatType != other.markdownFormatType) return false
        if (builtInConfig != other.builtInConfig) return false
        if (!remoteConfig.contentEquals(other.remoteConfig)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = feignEnable.hashCode()
        result = 31 * result + jaxrsEnable.hashCode()
        result = 31 * result + actuatorEnable.hashCode()
        result = 31 * result + postmanBuildExample.hashCode()
        result = 31 * result + wrapCollection.hashCode()
        result = 31 * result + autoMergeScript.hashCode()
        result = 31 * result + queryExpanded.hashCode()
        result = 31 * result + formExpanded.hashCode()
        result = 31 * result + pathMulti.hashCode()
        result = 31 * result + inferReturnMain.hashCode()
        result = 31 * result + enableUrlTemplating.hashCode()
        result = 31 * result + switchNotice.hashCode()
        result = 31 * result + httpTimeOut
        result = 31 * result + unsafeSsl.hashCode()
        result = 31 * result + logLevel
        result = 31 * result + outputDemo.hashCode()
        result = 31 * result + (postmanToken?.hashCode() ?: 0)
        result = 31 * result + (postmanWorkspace?.hashCode() ?: 0)
        result = 31 * result + (postmanExportMode?.hashCode() ?: 0)
        result = 31 * result + (postmanCollections?.hashCode() ?: 0)
        result = 31 * result + postmanJson5FormatType.hashCode()
        result = 31 * result + httpClient.hashCode()
        result = 31 * result + recommendConfigs.hashCode()
        result = 31 * result + outputCharset.hashCode()
        result = 31 * result + markdownFormatType.hashCode()
        result = 31 * result + (builtInConfig?.hashCode() ?: 0)
        result = 31 * result + remoteConfig.contentHashCode()
        return result
    }
}
