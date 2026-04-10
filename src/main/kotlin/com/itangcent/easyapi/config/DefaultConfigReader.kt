package com.itangcent.easyapi.config

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.config.resource.CachedResourceResolver
import com.itangcent.easyapi.config.source.*
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.DefaultContextSwitchListener
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.util.storage.LocalStorage

/**
 * Default implementation of [ConfigReader] that reads configuration from multiple sources
 * in a layered manner, with each source having a specific priority.
 *
 * Configuration sources are queried in order (highest priority first):
 * 1. **RuntimeConfigSource** - Dynamic runtime configuration, typically module-specific
 * 2. **BuiltInConfigSource** - Built-in configuration defined in plugin settings
 * 3. **SwaggerOnDemandConfigSource** - Swagger 2.x annotations parsed on demand
 * 4. **Swagger3OnDemandConfigSource** - OpenAPI 3.x annotations parsed on demand
 * 5. **RecommendConfigSource** - Recommended configuration presets
 * 6. **RemoteConfigSource** - Configuration fetched from remote URLs
 * 7. **LocalFileConfigSource** - Configuration from local `.easy-api.config` files
 *
 * The reader automatically reloads configuration when:
 * - The service is first initialized (async background reload)
 * - The active module changes in the IDE (via [DefaultContextSwitchListener])
 *
 * This is a project-level service scoped to a specific IntelliJ project.
 *
 * @see ConfigReader
 * @see LayeredConfigReader
 */
@Service(Service.Level.PROJECT)
class DefaultConfigReader(
    private val project: Project
) : ConfigReader {

    private val settingBinder = SettingBinder.getInstance(project)
    private val settings by lazy { settingBinder.read() }
    private val localStorage = LocalStorage.getInstance(project)
    private val contextSwitchListener = DefaultContextSwitchListener.getInstance(project)

    companion object : IdeaLog {
        fun getInstance(project: Project): DefaultConfigReader =
            project.getService(DefaultConfigReader::class.java)
    }

    private val configTextParser by lazy { ConfigTextParser(settings) }
    private val console by lazy { IdeaConsoleProvider.getInstance(project).getConsole() }
    private val cachedResourceResolver by lazy { CachedResourceResolver(localStorage, console) }

    private val runtimeConfigSource by lazy { RuntimeConfigSource(project.basePath ?: "") }
    private val localFileConfigSource by lazy {
        LocalFileConfigSource(project.basePath ?: "", configTextParser)
    }

    private val delegate by lazy {
        LayeredConfigReader(
            sources = listOf(
                runtimeConfigSource,
                BuiltInConfigSource(
                    settings.builtInConfig?.isNotBlank() == true,
                    configTextParser,
                    settings.builtInConfig
                ),
                SwaggerOnDemandConfigSource(project),
                Swagger3OnDemandConfigSource(project),
                RecommendConfigSource(settings.recommendConfigs, configTextParser),
                RemoteConfigSource(
                    parseUrls(settings.remoteConfig.joinToString("\n")),
                    configTextParser,
                    cachedResourceResolver
                ),
                localFileConfigSource
            )
        )
    }

    init {
        contextSwitchListener.addModuleChangeListener { _, newModule ->
            onModuleChanged(newModule)
        }
        backgroundAsync { delegate.reload() }
    }

    override fun getFirst(key: String): String? = delegate.getFirst(key)

    override fun getAll(key: String): List<String> = delegate.getAll(key)

    override suspend fun reload() = delegate.reload()

    override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
        delegate.foreach(keyFilter, action)
    }

    private fun onModuleChanged(newModule: String) {
        runtimeConfigSource.setModulePath(newModule)
        localFileConfigSource.setProjectBasePath(newModule)
        backgroundAsync { delegate.reload() }
    }

    private fun parseUrls(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("!") }
    }
}
