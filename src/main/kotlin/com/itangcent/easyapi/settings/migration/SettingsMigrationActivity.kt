package com.itangcent.easyapi.settings.migration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.itangcent.easyapi.exporter.channel.postman.PostmanSettings
import com.itangcent.easyapi.logging.console
import com.itangcent.easyapi.settings.SettingsChangeListener
import com.itangcent.easyapi.settings.module.*
import com.itangcent.easyapi.settings.state.ApplicationSettingsState
import com.itangcent.easyapi.settings.state.ProjectSettingsState
import com.itangcent.easyapi.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.settings.state.UnifiedProjectSettingsState
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * One-time migration from legacy `ApplicationSettingsState` / `ProjectSettingsState`
 * to the unified [UnifiedAppSettingsState] / [UnifiedProjectSettingsState] (DD-2, R-A-21).
 *
 * Runs on startup, guarded by [MigrationFlag]. Reads legacy state, maps each field
 * to the unified state under the module's qualified-name key, persists, and bumps the
 * version flag so it never runs again.
 *
 * Version history:
 * - v1: migrated legacy state to per-module `*SettingsState` components (now deleted).
 * - v2: re-migrates legacy state into the unified map-backed state components.
 *
 * Special handling:
 * - `postmanToken` duplication: prefer the non-empty value between APP/PROJ copies; write to APP only.
 * - `builtInConfig`/`remoteConfig` APP-vs-PROJ forms are different settings; PROJ forms renamed
 *   (`builtInConfig:Boolean` → `projectBuiltInConfigEnabled`, `remoteConfig:String?` → `projectRemoteConfig`).
 */
class SettingsMigrationActivity : StartupActivity {

    companion object {
        private const val MIGRATION_VERSION = 2
    }

    override fun runActivity(project: Project) {
        val flag = MigrationFlag.getInstance()
        if (flag.state.migrated && flag.state.version >= MIGRATION_VERSION) return

        try {
            project.console.info("Migrating EasyApi settings to unified layout…")

            val appState = UnifiedAppSettingsState.getInstance()
            val projState = UnifiedProjectSettingsState.getInstance(project)

            migrateApplicationSettings(appState)
            migrateProjectSettings(project, projState, appState)

            flag.state.migrated = true
            flag.state.version = MIGRATION_VERSION

            // Invalidate caches by firing settings change
            project.messageBus.syncPublisher(SettingsChangeListener.TOPIC).settingsChanged()

            project.console.info("EasyApi settings migration complete.")
        } catch (t: Throwable) {
            // Never error — migration failures are logged as warnings; next startup retries.
            project.console.warn("EasyApi settings migration failed: ${t.message}")
        }
    }

    /**
     * Reads legacy [ApplicationSettingsState] and writes to [UnifiedAppSettingsState]
     * under each module's qualified-name key.
     */
    private fun migrateApplicationSettings(appState: UnifiedAppSettingsState) {
        val app = ApplicationManager.getApplication()
        val legacy = app.getService(ApplicationSettingsState::class.java)?.state ?: return

        // GeneralSettings
        val generalKey = GeneralSettings::class.qualifiedName!!
        appState.setValue(generalKey, "feignEnable", legacy.feignEnable.toString())
        appState.setValue(generalKey, "jaxrsEnable", legacy.jaxrsEnable.toString())
        appState.setValue(generalKey, "actuatorEnable", legacy.actuatorEnable.toString())
        appState.setValue(generalKey, "autoScanEnabled", legacy.autoScanEnabled.toString())
        appState.setValue(generalKey, "concurrentScanEnabled", legacy.concurrentScanEnabled.toString())
        appState.setValue(generalKey, "gutterIconEnabled", legacy.gutterIconEnabled.toString())
        appState.setValue(generalKey, "switchNotice", legacy.switchNotice.toString())
        appState.setValue(generalKey, "enumFieldAutoInferEnabled", legacy.enumFieldAutoInferEnabled.toString())
        appState.setValue(generalKey, "logLevel", legacy.logLevel.toString())
        appState.setValue(generalKey, "outputDemo", legacy.outputDemo.toString())
        appState.setValue(generalKey, "outputCharset", legacy.outputCharset)

        // HttpSettings
        val httpKey = HttpSettings::class.qualifiedName!!
        appState.setValue(httpKey, "httpTimeOut", legacy.httpTimeOut.toString())
        appState.setValue(httpKey, "unsafeSsl", legacy.unsafeSsl.toString())
        appState.setValue(httpKey, "httpClient", legacy.httpClient)

        // IntelligentSettings
        val intelligentKey = IntelligentSettings::class.qualifiedName!!
        appState.setValue(intelligentKey, "inferReturnMain", legacy.inferReturnMain.toString())
        appState.setValue(intelligentKey, "enableUrlTemplating", legacy.enableUrlTemplating.toString())
        appState.setValue(intelligentKey, "queryExpanded", legacy.queryExpanded.toString())
        appState.setValue(intelligentKey, "formExpanded", legacy.formExpanded.toString())
        appState.setValue(intelligentKey, "pathMulti", legacy.pathMulti)
        appState.setValue(intelligentKey, "globalEnvironments", legacy.globalEnvironments)

        // GrpcSettings
        val grpcKey = GrpcSettings::class.qualifiedName!!
        appState.setValue(grpcKey, "grpcEnable", legacy.grpcEnable.toString())
        appState.setValue(grpcKey, "grpcArtifactConfigs", GsonUtils.toJson(legacy.grpcArtifactConfigs))
        appState.setValue(grpcKey, "grpcAdditionalJars", GsonUtils.toJson(legacy.grpcAdditionalJars))
        appState.setValue(grpcKey, "grpcCallEnabled", legacy.grpcCallEnabled.toString())
        appState.setValue(grpcKey, "grpcRepositories", GsonUtils.toJson(legacy.grpcRepositories))

        // AiSettings
        val aiKey = AiSettings::class.qualifiedName!!
        appState.setValue(aiKey, "aiProvider", legacy.aiProvider)
        appState.setValue(aiKey, "aiBaseUrl", legacy.aiBaseUrl)
        appState.setValue(aiKey, "aiModel", legacy.aiModel)
        appState.setValue(aiKey, "aiRequestTimeoutSec", legacy.aiRequestTimeoutSec.toString())
        appState.setValue(aiKey, "aiMaxRequests", legacy.aiMaxRequests.toString())
        appState.setValue(aiKey, "aiContextWindow", legacy.aiContextWindow.toString())

        // RuleFileSettings (APP fields)
        val ruleFileKey = RuleFileSettings::class.qualifiedName!!
        appState.setValue(ruleFileKey, "extensionConfigs", legacy.extensionConfigs)
        appState.setValue(ruleFileKey, "builtInConfig", legacy.builtInConfig)
        appState.setValue(ruleFileKey, "remoteConfig", GsonUtils.toJson(legacy.remoteConfig))
        appState.setValue(ruleFileKey, "disabledGlobalRuleFiles", GsonUtils.toJson(legacy.disabledGlobalRuleFiles))

        // PostmanSettings (APP) — postmanToken standardized to APP only
        val postmanKey = PostmanSettings::class.qualifiedName!!
        appState.setValue(postmanKey, "postmanToken", legacy.postmanToken)
        appState.setValue(postmanKey, "wrapCollection", legacy.wrapCollection.toString())
        appState.setValue(postmanKey, "autoMergeScript", legacy.autoMergeScript.toString())
        appState.setValue(postmanKey, "postmanJson5FormatType", legacy.postmanJson5FormatType)
    }

    /**
     * Reads legacy [ProjectSettingsState] and writes to [UnifiedProjectSettingsState]
     * under each module's qualified-name key.
     */
    private fun migrateProjectSettings(
        project: Project,
        projState: UnifiedProjectSettingsState,
        appState: UnifiedAppSettingsState
    ) {
        val legacy = project.getService(ProjectSettingsState::class.java)?.state ?: return

        // RuleFileSettings (PROJ) — renamed fields
        val ruleFileKey = RuleFileSettings::class.qualifiedName!!
        projState.setValue(ruleFileKey, "projectBuiltInConfigEnabled", legacy.builtInConfig.toString())
        projState.setValue(ruleFileKey, "projectRemoteConfig", legacy.remoteConfig)
        projState.setValue(ruleFileKey, "recommendConfig", legacy.recommendConfig)

        // EnvironmentSettings
        val envKey = EnvironmentSettings::class.qualifiedName!!
        projState.setValue(envKey, "projectEnvironments", legacy.projectEnvironments)
        projState.setValue(envKey, "disabledAutoRuleFiles", GsonUtils.toJson(legacy.disabledAutoRuleFiles))

        // PostmanSettings (PROJ)
        val postmanKey = PostmanSettings::class.qualifiedName!!
        projState.setValue(postmanKey, "postmanWorkspace", legacy.postmanWorkspace)
        projState.setValue(postmanKey, "postmanExportMode", legacy.postmanExportMode)
        projState.setValue(postmanKey, "postmanCollections", legacy.postmanCollections)
        projState.setValue(postmanKey, "postmanBuildExample", legacy.postmanBuildExample.toString())

        // postmanToken duplication resolution: if APP copy is empty but PROJ copy is non-empty, use PROJ
        val projToken = legacy.postmanToken
        if (!projToken.isNullOrEmpty()) {
            val postmanAppKey = PostmanSettings::class.qualifiedName!!
            val existing = appState.getValue(postmanAppKey, "postmanToken")
            if (existing.isNullOrEmpty()) {
                appState.setValue(postmanAppKey, "postmanToken", projToken)
            }
        }
    }
}
