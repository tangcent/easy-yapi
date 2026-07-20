package com.itangcent.easyapi.core.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.config.source.*
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.settings.module.EnvironmentSettings
import com.itangcent.easyapi.core.settings.module.RuleFileSettings
import com.itangcent.easyapi.core.settings.settings

/**
 * Default implementation of [ConfigReader] that reads configuration from multiple sources
 * in a layered manner, with each source having a specific priority.
 *
 * Configuration sources are queried in order (highest priority first):
 * 1. **ProjectFileConfigSource** (priority 4) - Configuration from local `.easy.api.config` files (auto-detected + custom project files)
 * 2. **ExtensionConfigSource** (priority 3) - Extension configurations (Swagger, Jackson, etc.)
 * 3. **UrlConfigSource** (priority 3) - Configuration fetched from remote URLs
 * 4. **GlobalFileConfigSource** (priority 2) - User-managed global rule files
 *
 * (`RuntimeConfigSource` exists at priority 0 but is not registered here.)
 *
 * ## Automatic Reloading
 *
 * The reader automatically reloads configuration when:
 * - The service is first initialized (async background reload)
 * - The active module changes in the IDE (via [DefaultContextSwitchListener])
 * - Configuration files or settings change (via [ConfigSyncService])
 *
 * [ConfigSyncService] monitors file changes and settings updates, triggering
 * reloads with a debounce mechanism to prevent excessive reload operations.
 *
 * This is a project-level service scoped to a specific IntelliJ project.
 *
 * @see ConfigReader
 * @see LayeredConfigReader
 * @see ConfigSyncService
 */
@Service(Service.Level.PROJECT)
class DefaultConfigReader(
    private val project: Project
) : ConfigReader {

    @Volatile
    private var delegate: LayeredConfigReader = buildDelegate()

    override fun getFirst(key: String): String? = delegate.getFirst(key)

    override fun getAll(key: String): List<String> = delegate.getAll(key)

    override suspend fun reload() {
        delegate = buildDelegate().also { it.reload() }
        project.messageBus.syncPublisher(ConfigReloadListener.TOPIC).onConfigReloaded()
    }

    override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
        delegate.foreach(keyFilter, action)
    }

    private fun buildDelegate(): LayeredConfigReader {
        val ruleFileSettings = project.settings<RuleFileSettings>()
        val envSettings = project.settings<EnvironmentSettings>()
        val configTextParser = ConfigTextParser.getInstance(project)
        val disabledAuto = envSettings.disabledAutoRuleFiles.toSet()
        val globalDir = java.nio.file.Path.of(System.getProperty("user.home"), ".easyapi")
        return LayeredConfigReader(
            sources = listOf(
                GlobalFileConfigSource(
                    project,
                    globalDir,
                    ruleFileSettings.disabledGlobalRuleFiles.toSet()
                ),
                ExtensionConfigSource(
                    project,
                    ExtensionConfigRegistry.stringToCodes(ruleFileSettings.extensionConfigs),
                    configTextParser
                ),
                UrlConfigSource(
                    parseUrls(ruleFileSettings.remoteConfig.joinToString("\n")),
                    configTextParser,
                    project
                ),
                ProjectFileConfigSource(
                    project,
                    project.basePath ?: "",
                    disabledFiles = disabledAuto
                )
            )
        )
    }

    private fun parseUrls(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("!") }
    }
}
