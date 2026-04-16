package com.itangcent.easyapi.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.config.resource.CachedResourceResolver
import com.itangcent.easyapi.config.source.*
import com.itangcent.easyapi.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.util.storage.LocalStorage

/**
 * Default implementation of [ConfigReader] that reads configuration from multiple sources
 * in a layered manner, with each source having a specific priority.
 *
 * Configuration sources are queried in order (highest priority first):
 * 1. **RuntimeConfigSource** - Dynamic runtime configuration, typically module-specific
 * 2. **BuiltInConfigSource** - Built-in configuration defined in plugin settings
 * 3. **ExtensionConfigSource** - Extension configurations (Swagger, Jackson, etc.)
 * 4. **RemoteConfigSource** - Configuration fetched from remote URLs
 * 5. **LocalFileConfigSource** - Configuration from local `.easy-api.config` files
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

    private val settingBinder = SettingBinder.getInstance(project)
    private val localStorage = LocalStorage.getInstance(project)

    private val console by lazy { IdeaConsoleProvider.getInstance(project).getConsole() }
    private val cachedResourceResolver by lazy { CachedResourceResolver(localStorage, console) }

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
        val settings = settingBinder.read()
        val configTextParser = ConfigTextParser(settings)
        return LayeredConfigReader(
            sources = listOf(
                BuiltInConfigSource(
                    settings.builtInConfig?.isNotBlank() == true,
                    configTextParser,
                    settings.builtInConfig
                ),
                ExtensionConfigSource(
                    ExtensionConfigRegistry.stringToCodes(settings.extensionConfigs),
                    configTextParser
                ),
                RemoteConfigSource(
                    parseUrls(settings.remoteConfig.joinToString("\n")),
                    configTextParser,
                    cachedResourceResolver
                ),
                LocalFileConfigSource(project.basePath ?: "", configTextParser)
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
