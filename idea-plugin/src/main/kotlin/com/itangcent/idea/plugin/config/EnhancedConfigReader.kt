package com.itangcent.idea.plugin.config

import com.google.inject.Inject
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.logger.traceWarn
import com.itangcent.idea.plugin.settings.helper.BuiltInConfigSettingsHelper
import com.itangcent.idea.plugin.settings.helper.RecommendConfigSettingsHelper
import com.itangcent.idea.plugin.settings.helper.RemoteConfigSettingsHelper
import com.itangcent.intellij.adaptor.ModuleAdaptor.filePath
import com.itangcent.intellij.config.BaseConfigReader
import com.itangcent.intellij.config.ConfigContent
import com.itangcent.intellij.config.ConfigProvider
import com.itangcent.intellij.config.dev
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.order.Order
import com.itangcent.order.Ordered
import com.itangcent.spi.SpiCompositeLoader
import java.util.concurrent.TimeUnit


/**
 * An enhanced configuration reader that extends a base configuration reader to include dynamic loading and initialization checks.
 */
class EnhancedConfigReader : BaseConfigReader() {

    private val configProviders: Array<ConfigProvider> by lazy {
        SpiCompositeLoader.load<ConfigProvider>(ActionContext.getContext()!!)
    }

    @Inject
    private lateinit var contextSwitchListener: ContextSwitchListener

    @Volatile
    private var loading: Thread? = null

    private var notInit = true

    override fun first(key: String): String? {
        checkStatus()
        return super.first(key)
    }

    override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
        checkStatus()
        super.foreach(keyFilter, action)
    }

    override fun foreach(action: (String, String) -> Unit) {
        checkStatus()
        super.foreach(action)
    }

    override fun read(key: String): Collection<String>? {
        checkStatus()
        return super.read(key)
    }

    override fun resolveProperty(property: String): String {
        checkStatus()
        return super.resolveProperty(property)
    }

    /**
     * Ensures the configuration is loaded and up to date before any read operation.
     */
    private fun checkStatus() {
        if (notInit) {
            synchronized(this) {
                if (notInit) {
                    try {
                        contextSwitchListener.onModuleChange {
                            loadConfigList()
                        }
                        //fix: https://github.com/tangcent/easy-yapi/issues/1121
                        //if the module is null, the contextSwitchListener will not be triggered
                        if (contextSwitchListener.getModule() == null) {
                            loadConfigList()
                        }
                    } finally {
                        notInit = false
                    }
                }
            }
        }
        while (loading != null && loading != Thread.currentThread()) {
            TimeUnit.MILLISECONDS.sleep(100)
        }
    }

    private var currentConfigContentHash: String? = null

    /**
     * Get the parent path from a ConfigContent.
     * For local files, it will use the file's parent directory.
     * For remote URLs or invalid file paths, it will use the URL's parent path.
     */
    private fun ConfigContent.getContentPath(): String? {
        val url = url ?: return null
        try {
            val path = if (url.startsWith("file:")) {
                url.substringAfter("file:")
            } else {
                url
            }
            val file = java.io.File(path)
            if (file.exists()) {
                // For existing local files, use File.parent
                return file.parentFile.path
            }
        } catch (_: Exception) {
        }
        // For non-existent files or invalid file paths (like URLs), get the parent path from URL
        return url.substringBeforeLast('/')
    }

    /**
     * Collect configuration contents from all providers, handling any errors gracefully.
     */
    private fun loadConfigList() {
        synchronized(this)
        {
            if (loading != null && loading == Thread.currentThread()) {
                return
            }
            try {
                loading = Thread.currentThread()
                val configContentList = collectConfigContentList()
                val configContentHash = configContentList.joinToString { it.id }.let { "${it.length}x${it.hashCode()}" }
                if (configContentHash == this.currentConfigContentHash) {
                    dev {
                        logger.debug("config not change. skip reload.")
                    }
                    return
                }
                this.currentConfigContentHash = configContentHash
                super.reset()
                configContentList.forEach { configContent ->
                    val prePath = configInfo.getFirst("_curr_path")
                    try {
                        configContent.getContentPath()?.let { path ->
                            configInfo.replace("_curr_path", path)
                        }
                        super.loadConfigInfoContent(configContent.content, configContent.type)
                    } finally {
                        if (prePath == null) {
                            configInfo.removeAll("_curr_path")
                        } else {
                            configInfo.replace("_curr_path", prePath)
                        }
                    }
                }
            } finally {
                loading = null
            }
        }
    }

    private fun collectConfigContentList(): List<ConfigContent> {
        val configContents = mutableListOf<ConfigContent>()
        configProviders.forEach { configProvider ->
            try {
                configProvider.loadConfig().forEach { configContent ->
                    configContents.add(configContent)
                }
            } catch (e: Throwable) {
                logger.traceError("failed to load config", e)
            }
        }
        return configContents
    }
}

@Order(0)
class BuiltinConfigProvider : ConfigProvider {

    companion object : Log()

    @Inject(optional = true)
    private val builtInConfigSettingsHelper: BuiltInConfigSettingsHelper? = null

    @Inject
    private lateinit var logger: Logger

    override fun loadConfig(): Sequence<ConfigContent> {
        try {
            val builtInConfig = builtInConfigSettingsHelper?.builtInConfig() ?: return emptySequence()
            LOG.debug("use built-in config")
            LOG.debug("----------------\n$builtInConfig\n----------------")
            return sequenceOf(ConfigContent(builtInConfig, "properties"))
        } catch (e: Exception) {
            logger.warn("failed to use built-in config")
            return emptySequence()
        }
    }
}

@Order(1)
class RecommendConfigProvider : ConfigProvider {

    companion object : Log()

    @Inject(optional = true)
    private val recommendConfigSettingsHelper: RecommendConfigSettingsHelper? = null

    @Inject
    private lateinit var logger: Logger

    override fun loadConfig(): Sequence<ConfigContent> {
        try {
            if (recommendConfigSettingsHelper?.useRecommendConfig() == true) {
                val recommendConfig = recommendConfigSettingsHelper.loadRecommendConfig()
                LOG.debug("use recommend config")
                LOG.debug("----------------\n$recommendConfig\n----------------")
                if (recommendConfig.isEmpty()) {
                    logger.debug(
                        "Even useRecommendConfig was true, but no recommend config be selected!\n" +
                                "\n" +
                                "If you need to enable the built-in recommended configuration." +
                                "Go to [Preference -> Other Setting -> EasyApi -> Recommend]"
                    )
                }
                return sequenceOf(ConfigContent(recommendConfig, "properties"))
            }
        } catch (e: Exception) {
            logger.traceWarn("failed to use recommend config", e)
        }
        return emptySequence()
    }
}

@Order(2)
class RemoteConfigProvider : ConfigProvider {

    companion object : Log()

    @Inject(optional = true)
    private val remoteConfigSettingsHelper: RemoteConfigSettingsHelper? = null

    @Inject
    private lateinit var logger: Logger

    override fun loadConfig(): Sequence<ConfigContent> {
        try {
            val remoteConfig = remoteConfigSettingsHelper?.remoteConfigContent() ?: return emptySequence()
            LOG.debug("load remote config")
            LOG.debug("----------------\n$remoteConfig\n----------------")
            return remoteConfig.asSequence()
        } catch (e: Exception) {
            logger.traceError("failed to load remote config", e)
            return emptySequence()
        }
    }
}

@Order(Ordered.HIGHEST_PRECEDENCE)
class RuntimeConfigProvider : ConfigProvider {

    companion object : Log()

    @Inject
    private lateinit var contextSwitchListener: ContextSwitchListener

    override fun loadConfig(): Sequence<ConfigContent> {
        try {
            val runtimeConfig = runtimeConfig()
            LOG.debug("use runtime config")
            LOG.debug("----------------\n$runtimeConfig\n----------------")
            return sequenceOf(ConfigContent(runtimeConfig, "properties"))
        } catch (e: Exception) {
            LOG.warn("failed to use runtime config")
        }
        return emptySequence()
    }

    private fun runtimeConfig(): String {
        val res = StringBuilder()
        contextSwitchListener.getModule()?.let { module ->
            res.append("module_path=${module.filePath()}")
            res.appendLine()
        }
        return res.toString()
    }
}