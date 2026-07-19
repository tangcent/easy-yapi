package com.itangcent.easyapi.core.settings.migration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.itangcent.easyapi.core.logging.console
import com.itangcent.easyapi.core.settings.SettingsChangeListener
import com.itangcent.easyapi.core.settings.module.*
import com.itangcent.easyapi.core.settings.state.ApplicationSettingsState
import com.itangcent.easyapi.core.settings.state.ProjectSettingsState
import com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.core.settings.state.UnifiedProjectSettingsState
import com.itangcent.easyapi.core.util.json.GsonUtils

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
 * - v3: `enumFieldAutoInferEnabled` moved GeneralSettings -> ParsingOutputSettings;
 *   `globalEnvironments` moved ParsingOutputSettings -> EnvironmentSettings (APP scope).
 * - v4: per-framework enable booleans (`feignEnable`, `jaxrsEnable`, `actuatorEnable`,
 *   `grpcEnable`) translated to the unified `enabledFrameworks`/`disabledFrameworks`
 *   arrays in `GeneralSettings`; the legacy booleans are no longer written to per-module
 *   state (the fields were removed from `GeneralSettings`/`GrpcSettings`). Framework
 *   enablement is now resolved by `FrameworkRegistry` from the new arrays.
 *
 * Special handling:
 * - `postmanToken` duplication: prefer the non-empty value between APP/PROJ copies; write to APP only.
 * - `builtInConfig`/`remoteConfig` APP-vs-PROJ forms are different settings; PROJ forms renamed
 *   (`builtInConfig:Boolean` → `projectBuiltInConfigEnabled`, `remoteConfig:String?` → `projectRemoteConfig`).
 */
class SettingsMigrationActivity : StartupActivity {

    companion object {
        private const val MIGRATION_VERSION = 4
    }

    override fun runActivity(project: Project) {
        val flag = MigrationFlag.getInstance()
        if (flag.state.migrated && flag.state.version >= MIGRATION_VERSION) return

        try {
            project.console.info("Migrating EasyApi settings to unified layout…")

            val appState = UnifiedAppSettingsState.getInstance()
            val projState = UnifiedProjectSettingsState.getInstance(project)

            migrateApplicationSettings(project, appState)
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
     *
     * v4 branch (PR7) translates the legacy per-framework booleans
     * (`feignEnable`, `jaxrsEnable`, `actuatorEnable`, `grpcEnable`) to the unified
     * `enabledFrameworks`/`disabledFrameworks` arrays in `GeneralSettings`. The
     * translation is idempotent (uses [MutableSet] semantics) and only adds an
     * entry when the legacy value diverges from its pre-patch default — preserving
     * user intent while letting `enabledByDefault` resolve the rest.
     */
    @Suppress("DEPRECATION")  // reads @Deprecated legacy fields for one-time v4 migration
    private fun migrateApplicationSettings(project: Project, appState: UnifiedAppSettingsState) {
        val app = ApplicationManager.getApplication()
        val legacy = app.getService(ApplicationSettingsState::class.java)?.state
        if (legacy == null) {
            project.console.warn(
                "Legacy ApplicationSettingsState unavailable; framework enablement " +
                    "migration skipped, falling back to defaults"
            )
            return
        }

        // GeneralSettings
        val generalKey = GeneralSettings::class.qualifiedName!!
        // v4: feignEnable/jaxrsEnable/actuatorEnable are no longer fields on GeneralSettings;
        // they're translated to enabledFrameworks/disabledFrameworks arrays in the v4 branch
        // below. The legacy booleans are read from `legacy` (ApplicationSettingsState.State)
        // which retains them as @Deprecated read-only fields for one-time migration.
        appState.setValue(generalKey, "autoScanEnabled", legacy.autoScanEnabled.toString())
        appState.setValue(generalKey, "concurrentScanEnabled", legacy.concurrentScanEnabled.toString())
        appState.setValue(generalKey, "gutterIconEnabled", legacy.gutterIconEnabled.toString())
        appState.setValue(generalKey, "switchNotice", legacy.switchNotice.toString())
        appState.setValue(generalKey, "logLevel", legacy.logLevel.toString())
        appState.setValue(generalKey, "outputCharset", legacy.outputCharset)

        // HttpSettings
        val httpKey = HttpSettings::class.qualifiedName!!
        appState.setValue(httpKey, "httpTimeOut", legacy.httpTimeOut.toString())
        appState.setValue(httpKey, "unsafeSsl", legacy.unsafeSsl.toString())
        appState.setValue(httpKey, "httpClient", legacy.httpClient)

        // ParsingOutputSettings (includes enumFieldAutoInferEnabled moved from GeneralSettings in v3)
        val parsingOutputKey = ParsingOutputSettings::class.qualifiedName!!
        appState.setValue(parsingOutputKey, "inferReturnMain", legacy.inferReturnMain.toString())
        appState.setValue(parsingOutputKey, "enableUrlTemplating", legacy.enableUrlTemplating.toString())
        appState.setValue(parsingOutputKey, "queryExpanded", legacy.queryExpanded.toString())
        appState.setValue(parsingOutputKey, "formExpanded", legacy.formExpanded.toString())
        appState.setValue(parsingOutputKey, "pathMulti", legacy.pathMulti)
        appState.setValue(parsingOutputKey, "enumFieldAutoInferEnabled", legacy.enumFieldAutoInferEnabled.toString())

        // EnvironmentSettings (APP field — globalEnvironments moved here from ParsingOutputSettings in v3)
        val envAppKey = EnvironmentSettings::class.qualifiedName!!
        appState.setValue(envAppKey, "globalEnvironments", legacy.globalEnvironments)

        // GrpcSettings
        val grpcKey = GrpcSettings::class.qualifiedName!!
        // v4: grpcEnable is no longer a field on GrpcSettings; it's translated to the
        // disabledFrameworks array in the v4 branch below (legacy false → disabled).
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
        // FQN used as string literal to avoid concrete-impl import from core.* (Decision CO3)
        val postmanKey = "com.itangcent.easyapi.channel.postman.PostmanSettings"
        appState.setValue(postmanKey, "postmanToken", legacy.postmanToken)
        appState.setValue(postmanKey, "wrapCollection", legacy.wrapCollection.toString())
        appState.setValue(postmanKey, "autoMergeScript", legacy.autoMergeScript.toString())
        appState.setValue(postmanKey, "postmanJson5FormatType", legacy.postmanJson5FormatType)

        // v4 (PR7): translate legacy per-framework booleans to the unified
        // enabledFrameworks/disabledFrameworks arrays in GeneralSettings.
        // Idempotent — initialized from existing arrays so re-running migration
        // does not duplicate entries. Only non-default legacy values produce an
        // entry; default-matching values let `enabledByDefault` resolve correctly.
        val enabledFrameworks: MutableSet<String> = runCatching {
            appState.getValue(generalKey, "enabledFrameworks")
                ?.let { GsonUtils.fromJson<Array<String>>(it).toMutableSet() }
                ?: mutableSetOf()
        }.getOrDefault(mutableSetOf())
        val disabledFrameworks: MutableSet<String> = runCatching {
            appState.getValue(generalKey, "disabledFrameworks")
                ?.let { GsonUtils.fromJson<Array<String>>(it).toMutableSet() }
                ?: mutableSetOf()
        }.getOrDefault(mutableSetOf())

        if (legacy.feignEnable) enabledFrameworks += "Feign"
        if (!legacy.jaxrsEnable) disabledFrameworks += "JAX-RS"
        if (legacy.actuatorEnable) enabledFrameworks += "SpringActuator"
        if (!legacy.grpcEnable) disabledFrameworks += "gRPC"

        appState.setValue(generalKey, "enabledFrameworks", GsonUtils.toJson(enabledFrameworks.toTypedArray()))
        appState.setValue(generalKey, "disabledFrameworks", GsonUtils.toJson(disabledFrameworks.toTypedArray()))
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

        // EnvironmentSettings (PROJ fields — same module key as APP, scope-routed by the binder)
        val envKey = EnvironmentSettings::class.qualifiedName!!
        projState.setValue(envKey, "projectEnvironments", legacy.projectEnvironments)
        projState.setValue(envKey, "disabledAutoRuleFiles", GsonUtils.toJson(legacy.disabledAutoRuleFiles))

        // PostmanSettings (PROJ) — FQN as string literal (Decision CO3)
        val postmanKey = "com.itangcent.easyapi.channel.postman.PostmanSettings"
        projState.setValue(postmanKey, "postmanWorkspace", legacy.postmanWorkspace)
        projState.setValue(postmanKey, "postmanExportMode", legacy.postmanExportMode)
        projState.setValue(postmanKey, "postmanCollections", legacy.postmanCollections)
        projState.setValue(postmanKey, "postmanBuildExample", legacy.postmanBuildExample.toString())

        // postmanToken duplication resolution: if APP copy is empty but PROJ copy is non-empty, use PROJ
        val projToken = legacy.postmanToken
        if (!projToken.isNullOrEmpty()) {
            val postmanAppKey = "com.itangcent.easyapi.channel.postman.PostmanSettings"
            val existing = appState.getValue(postmanAppKey, "postmanToken")
            if (existing.isNullOrEmpty()) {
                appState.setValue(postmanAppKey, "postmanToken", projToken)
            }
        }
    }
}
