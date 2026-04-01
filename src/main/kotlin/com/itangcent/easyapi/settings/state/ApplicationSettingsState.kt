package com.itangcent.easyapi.settings.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.itangcent.easyapi.settings.HttpClientType
import com.itangcent.easyapi.settings.MarkdownFormatType
import com.itangcent.easyapi.settings.PostmanJson5FormatType
import com.itangcent.easyapi.settings.Settings

/**
 * Application-level settings state for EasyAPI plugin.
 * 
 * Stores global settings that apply across all projects,
 * persisted in `easyapi_app.xml`.
 * 
 * Settings include:
 * - Framework support toggles (Feign, JAX-RS, Actuator)
 * - HTTP client configuration
 * - Logging and output preferences
 */
@State(name = "EasyApiApplicationSettings", storages = [Storage("easyapi_app.xml")])
class ApplicationSettingsState : PersistentStateComponent<ApplicationSettingsState.State> {
    /**
     * Data class holding all application-level settings.
     * Implements ApplicationSettingsSupport for consistent access.
     */
    data class State(
        override var feignEnable: Boolean = false,
        override var jaxrsEnable: Boolean = true,
        override var actuatorEnable: Boolean = false,
        override var postmanToken: String? = null,
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
        override var recommendConfigs: String = Settings().recommendConfigs,
        override var logLevel: Int = 50,
        override var outputDemo: Boolean = true,
        override var outputCharset: String = "UTF-8",
        override var markdownFormatType: String = MarkdownFormatType.SIMPLE.name,
        override var builtInConfig: String? = null,
        override var remoteConfig: Array<String> = emptyArray()
    ) : ApplicationSettingsSupport {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as State

            if (feignEnable != other.feignEnable) return false
            if (jaxrsEnable != other.jaxrsEnable) return false
            if (actuatorEnable != other.actuatorEnable) return false
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

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
