package com.itangcent.easyapi.core.settings.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.settings.HttpClientType
import com.itangcent.easyapi.core.settings.PostmanJson5FormatType

/**
 * Application-level settings state for EasyAPI plugin.
 *
 * **Deprecated.** Kept only as a readable fallback for the one-time settings
 * migration ([com.itangcent.easyapi.core.settings.migration.SettingsMigrationActivity])
 * that reads legacy `easyapi_app.xml` and ports it into the unified map-backed
 * state ([UnifiedAppSettingsState]).
 *
 * New code must NOT read or write this state — use
 * [com.itangcent.easyapi.core.settings.SettingBinder] with the appropriate
 * [com.itangcent.easyapi.core.settings.Settings] subtype instead. This class will
 * be removed once the migration window closes.
 *
 * Persisted in `easyapi_app.xml`.
 *
 * Settings include:
 * - Framework support toggles (Feign, JAX-RS, Actuator)
 * - HTTP client configuration
 * - Logging and output preferences
 */
@Deprecated(
    "Legacy state kept only for one-time settings migration; use SettingBinder with a Settings subtype instead",
    level = DeprecationLevel.WARNING
)
@State(name = "EasyApiApplicationSettings", storages = [Storage("easyapi_app.xml")])
class ApplicationSettingsState : PersistentStateComponent<ApplicationSettingsState.State> {
    /**
     * Data class holding all application-level settings.
     */
    data class State(
        @Deprecated(
            "Use GeneralSettings.enabledFrameworks/disabledFrameworks via FrameworkRegistry. " +
                "Retained read-only for the one-time v4 settings migration.",
            level = DeprecationLevel.WARNING
        )
        var feignEnable: Boolean = false,
        @Deprecated(
            "Use GeneralSettings.enabledFrameworks/disabledFrameworks via FrameworkRegistry. " +
                "Retained read-only for the one-time v4 settings migration.",
            level = DeprecationLevel.WARNING
        )
        var jaxrsEnable: Boolean = true,
        @Deprecated(
            "Use GeneralSettings.enabledFrameworks/disabledFrameworks via FrameworkRegistry. " +
                "Retained read-only for the one-time v4 settings migration.",
            level = DeprecationLevel.WARNING
        )
        var actuatorEnable: Boolean = false,
        @Deprecated(
            "Use GeneralSettings.enabledFrameworks/disabledFrameworks via FrameworkRegistry. " +
                "Retained read-only for the one-time v4 settings migration.",
            level = DeprecationLevel.WARNING
        )
        var grpcEnable: Boolean = true,
        var postmanToken: String? = null,
        var wrapCollection: Boolean = false,
        var autoMergeScript: Boolean = false,
        var postmanJson5FormatType: String = PostmanJson5FormatType.EXAMPLE_ONLY.name,
        var queryExpanded: Boolean = true,
        var formExpanded: Boolean = true,
        var pathMulti: String = "ALL",
        var inferReturnMain: Boolean = true,
        var enableUrlTemplating: Boolean = true,
        var switchNotice: Boolean = true,
        var httpTimeOut: Int = 30,
        var unsafeSsl: Boolean = false,
        var httpClient: String = HttpClientType.APACHE.value,
        var extensionConfigs: String = ExtensionConfigRegistry.codesToString(ExtensionConfigRegistry.defaultCodes()),
        var logLevel: Int = 100, // SILENT — console off by default
        var outputCharset: String = "UTF-8",
        var builtInConfig: String? = null,
        var remoteConfig: Array<String> = emptyArray(),
        var autoScanEnabled: Boolean = true,
        var grpcArtifactConfigs: Array<String> = emptyArray(),
        var grpcAdditionalJars: Array<String> = emptyArray(),
        var grpcCallEnabled: Boolean = false,
        var grpcRepositories: Array<String> = emptyArray(),
        var concurrentScanEnabled: Boolean = false,
        var gutterIconEnabled: Boolean = true,
        var globalEnvironments: String = "",
        var enumFieldAutoInferEnabled: Boolean = false,
        var disabledGlobalRuleFiles: Array<String> = emptyArray(),
        var aiProvider: String = "OPENAI",
        var aiBaseUrl: String = "",
        var aiModel: String = "",
        var aiRequestTimeoutSec: Int = 60,
        var aiMaxRequests: Int = 100,
        var aiContextWindow: Int = AiProvider.DEFAULT_CONTEXT_WINDOW
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as State

            if (feignEnable != other.feignEnable) return false
            if (jaxrsEnable != other.jaxrsEnable) return false
            if (actuatorEnable != other.actuatorEnable) return false
            if (grpcEnable != other.grpcEnable) return false
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
            if (postmanToken != other.postmanToken) return false
            if (postmanJson5FormatType != other.postmanJson5FormatType) return false
            if (httpClient != other.httpClient) return false
            if (extensionConfigs != other.extensionConfigs) return false
            if (outputCharset != other.outputCharset) return false
            if (builtInConfig != other.builtInConfig) return false
            if (!remoteConfig.contentEquals(other.remoteConfig)) return false
            if (autoScanEnabled != other.autoScanEnabled) return false
            if (!grpcArtifactConfigs.contentEquals(other.grpcArtifactConfigs)) return false
            if (!grpcAdditionalJars.contentEquals(other.grpcAdditionalJars)) return false
            if (grpcCallEnabled != other.grpcCallEnabled) return false
            if (!grpcRepositories.contentEquals(other.grpcRepositories)) return false
            if (concurrentScanEnabled != other.concurrentScanEnabled) return false
            if (gutterIconEnabled != other.gutterIconEnabled) return false
            if (globalEnvironments != other.globalEnvironments) return false
            if (enumFieldAutoInferEnabled != other.enumFieldAutoInferEnabled) return false
            if (!disabledGlobalRuleFiles.contentEquals(other.disabledGlobalRuleFiles)) return false
            if (aiProvider != other.aiProvider) return false
            if (aiBaseUrl != other.aiBaseUrl) return false
            if (aiModel != other.aiModel) return false
            if (aiRequestTimeoutSec != other.aiRequestTimeoutSec) return false
            if (aiMaxRequests != other.aiMaxRequests) return false
            if (aiContextWindow != other.aiContextWindow) return false

            return true
        }

        override fun hashCode(): Int {
            var result = feignEnable.hashCode()
            result = 31 * result + jaxrsEnable.hashCode()
            result = 31 * result + actuatorEnable.hashCode()
            result = 31 * result + grpcEnable.hashCode()
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
            result = 31 * result + (postmanToken?.hashCode() ?: 0)
            result = 31 * result + postmanJson5FormatType.hashCode()
            result = 31 * result + httpClient.hashCode()
            result = 31 * result + extensionConfigs.hashCode()
            result = 31 * result + outputCharset.hashCode()
            result = 31 * result + (builtInConfig?.hashCode() ?: 0)
            result = 31 * result + remoteConfig.contentHashCode()
            result = 31 * result + autoScanEnabled.hashCode()
            result = 31 * result + grpcArtifactConfigs.contentHashCode()
            result = 31 * result + grpcAdditionalJars.contentHashCode()
            result = 31 * result + grpcCallEnabled.hashCode()
            result = 31 * result + grpcRepositories.contentHashCode()
            result = 31 * result + concurrentScanEnabled.hashCode()
            result = 31 * result + gutterIconEnabled.hashCode()
            result = 31 * result + globalEnvironments.hashCode()
            result = 31 * result + enumFieldAutoInferEnabled.hashCode()
            result = 31 * result + disabledGlobalRuleFiles.contentHashCode()
            result = 31 * result + aiProvider.hashCode()
            result = 31 * result + aiBaseUrl.hashCode()
            result = 31 * result + aiModel.hashCode()
            result = 31 * result + aiRequestTimeoutSec
            result = 31 * result + aiMaxRequests
            result = 31 * result + aiContextWindow
            return result
        }

        fun copyTo(target: State) {
            target.feignEnable = feignEnable
            target.jaxrsEnable = jaxrsEnable
            target.actuatorEnable = actuatorEnable
            target.grpcEnable = grpcEnable
            target.postmanToken = postmanToken
            target.wrapCollection = wrapCollection
            target.autoMergeScript = autoMergeScript
            target.postmanJson5FormatType = postmanJson5FormatType
            target.queryExpanded = queryExpanded
            target.formExpanded = formExpanded
            target.pathMulti = pathMulti
            target.inferReturnMain = inferReturnMain
            target.enableUrlTemplating = enableUrlTemplating
            target.switchNotice = switchNotice
            target.httpTimeOut = httpTimeOut
            target.unsafeSsl = unsafeSsl
            target.httpClient = httpClient
            target.extensionConfigs = extensionConfigs
            target.logLevel = logLevel
            target.outputCharset = outputCharset
            target.builtInConfig = builtInConfig
            target.remoteConfig = remoteConfig
            target.autoScanEnabled = autoScanEnabled
            target.grpcArtifactConfigs = grpcArtifactConfigs
            target.grpcAdditionalJars = grpcAdditionalJars
            target.grpcCallEnabled = grpcCallEnabled
            target.grpcRepositories = grpcRepositories
            target.concurrentScanEnabled = concurrentScanEnabled
            target.gutterIconEnabled = gutterIconEnabled
            target.globalEnvironments = globalEnvironments
            target.enumFieldAutoInferEnabled = enumFieldAutoInferEnabled
            target.disabledGlobalRuleFiles = disabledGlobalRuleFiles
            target.aiProvider = aiProvider
            target.aiBaseUrl = aiBaseUrl
            target.aiModel = aiModel
            target.aiRequestTimeoutSec = aiRequestTimeoutSec
            target.aiMaxRequests = aiMaxRequests
            target.aiContextWindow = aiContextWindow
        }
    }

    private var state: State = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
