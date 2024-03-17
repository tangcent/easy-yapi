package com.itangcent.idea.plugin.config

import com.google.inject.Inject
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.settings.helper.BuiltInConfigSettingsHelper
import com.itangcent.idea.plugin.settings.helper.RecommendConfigSettingsHelper
import com.itangcent.idea.plugin.settings.helper.RemoteConfigSettingsHelper
import com.itangcent.intellij.config.BaseConfigReader
import com.itangcent.intellij.config.ConfigContent
import com.itangcent.intellij.config.ConfigProvider
import com.itangcent.intellij.config.dev
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.dev.DevEnv
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.order.Order
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
                    super.loadConfigInfoContent(configContent.content, configContent.type)
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

    @Inject(optional = true)
    private val builtInConfigSettingsHelper: BuiltInConfigSettingsHelper? = null

    @Inject
    private lateinit var logger: Logger

    @Inject
    private val devEnv: DevEnv? = null

    override fun loadConfig(): Sequence<ConfigContent> {
        try {
            val builtInConfig = builtInConfigSettingsHelper?.builtInConfig() ?: return emptySequence()
            logger.debug("use built-in config")
            devEnv!!.dev {
                logger.debug("----------------\n$builtInConfig\n----------------")
            }
            return sequenceOf(ConfigContent(builtInConfig, "properties"))
        } catch (e: Exception) {
            logger.warn("failed to use built-in config")
            return emptySequence()
        }
    }
}

@Order(1)
class RecommendConfigProvider : ConfigProvider {

    @Inject(optional = true)
    private val recommendConfigSettingsHelper: RecommendConfigSettingsHelper? = null

    @Inject
    private lateinit var logger: Logger

    @Inject
    private val devEnv: DevEnv? = null

    override fun loadConfig(): Sequence<ConfigContent> {
        try {
            if (recommendConfigSettingsHelper?.useRecommendConfig() == true) {
                val recommendConfig = recommendConfigSettingsHelper.loadRecommendConfig()
                logger.debug("use recommend config")
                devEnv!!.dev {
                    logger.debug("----------------\n$recommendConfig\n----------------")
                }
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
            logger.warn("failed to use recommend config")
        }
        return emptySequence()
    }
}

@Order(2)
class RemoteConfigProvider : ConfigProvider {

    @Inject(optional = true)
    private val remoteConfigSettingsHelper: RemoteConfigSettingsHelper? = null

    @Inject
    private lateinit var logger: Logger

    @Inject
    private val devEnv: DevEnv? = null

    override fun loadConfig(): Sequence<ConfigContent> {
        try {
            val remoteConfig = remoteConfigSettingsHelper?.remoteConfigContent() ?: return emptySequence()
            logger.debug("load remote config")
            devEnv!!.dev {
                logger.debug("----------------\n$remoteConfig\n----------------")
            }
            return remoteConfig.asSequence()
        } catch (e: Exception) {
            logger.warn("failed to load remote config")
            return emptySequence()
        }
    }
}