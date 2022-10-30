package com.itangcent.idea.plugin.config

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.invokeMethod
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.settings.helper.BuiltInConfigSettingsHelper
import com.itangcent.idea.plugin.settings.helper.RecommendConfigSettingsHelper
import com.itangcent.idea.plugin.settings.helper.RemoteConfigSettingsHelper
import com.itangcent.intellij.adaptor.ModuleAdaptor.filePath
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.MutableConfigReader
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.jvm.dev.DevEnv
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.utils.Initializable
import java.util.concurrent.TimeUnit


class RecommendConfigReader : ConfigReader, Initializable {

    @Inject
    @Named("delegate_config_reader")
    private val configReader: ConfigReader? = null

    @Inject(optional = true)
    private val builtInConfigSettingsHelper: BuiltInConfigSettingsHelper? = null


    @Inject(optional = true)
    private val remoteConfigSettingsHelper: RemoteConfigSettingsHelper? = null

    @Inject(optional = true)
    private val recommendConfigSettingsHelper: RecommendConfigSettingsHelper? = null

    @Inject
    private lateinit var contextSwitchListener: ContextSwitchListener

    @Inject
    private lateinit var logger: Logger

    @Inject
    private val devEnv: DevEnv? = null

    @Volatile
    private var loading: Thread? = null

    private var notInit = true

    override fun first(key: String): String? {
        checkStatus()
        return configReader!!.first(key)
    }

    override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
        checkStatus()
        configReader!!.foreach(keyFilter, action)
    }

    override fun foreach(action: (String, String) -> Unit) {
        checkStatus()
        configReader!!.foreach(action)
    }

    override fun read(key: String): Collection<String>? {
        checkStatus()
        return configReader!!.read(key)
    }

    override fun resolveProperty(property: String): String {
        checkStatus()
        return configReader!!.resolveProperty(property)
    }

    private fun checkStatus() {
        if (notInit) {
            initDelegateAndRecommend()
        }
        while (loading != null && loading != Thread.currentThread()) {
            TimeUnit.MILLISECONDS.sleep(100)
        }
    }

    @PostConstruct
    override fun init() {

        if (configReader is MutableConfigReader) {
            contextSwitchListener.onModuleChange { module ->
                synchronized(this)
                {
                    try {
                        configReader.reset()
                        module.filePath()?.let { configReader.put("module_path", it) }
                        initDelegateAndRecommend()
                    } finally {
                        loading = null
                    }
                }
            }
        } else {
            initDelegateAndRecommend()
        }
    }

    private fun initDelegateAndRecommend() {
        synchronized(this)
        {
            try {
                if (loading != null && loading == Thread.currentThread()) {
                    return
                }
                loading = Thread.currentThread()
                try {
                    if (configReader is Initializable) {
                        configReader.init()
                    } else {
                        configReader?.invokeMethod("init")
                    }
                } catch (e: Throwable) {
                    logger.traceError("failed init config", e)
                }
                try {
                    tryLoadRecommend()
                } catch (e: Throwable) {
                    logger.traceError("failed load recommend config", e)
                }
                try {
                    tryLoadBuiltIn()
                } catch (e: Throwable) {
                    logger.traceError("failed load built-in config", e)
                }
                try {
                    tryLoadRemote()
                } catch (e: Throwable) {
                    logger.traceError("failed load remote config", e)
                }
            } finally {
                loading = null
                notInit = false
            }
        }
    }

    private fun tryLoadRecommend() {
        if (recommendConfigSettingsHelper?.useRecommendConfig() == true) {
            if (configReader is MutableConfigReader) {
                val recommendConfig = recommendConfigSettingsHelper.loadRecommendConfig()

                if (recommendConfig.isEmpty()) {
                    logger.info(
                        "Even useRecommendConfig was true, but no recommend config be selected!\n" +
                                "\n" +
                                "If you need to enable the built-in recommended configuration." +
                                "Go to [Preference -> Other Setting -> EasyApi -> Recommend]"
                    )

                    return
                }

                configReader.loadConfigInfoContent(recommendConfig)
                logger.debug("use recommend config")
                devEnv!!.dev {
                    logger.debug("----------------\n$recommendConfig\n----------------")
                }
            } else {
                logger.warn("failed to use recommend config")
            }
        }
    }

    private fun tryLoadBuiltIn() {
        val builtInConfig = builtInConfigSettingsHelper?.builtInConfig()
        if (builtInConfig.notNullOrBlank()) {
            if (configReader is MutableConfigReader) {
                configReader.loadConfigInfoContent(builtInConfig!!)
                logger.debug("use built-in config")
                devEnv!!.dev {
                    logger.debug("----------------\n$builtInConfig\n----------------")
                }
            } else {
                logger.warn("failed to use built-in config")
            }
        }
    }

    private fun tryLoadRemote() {
        val remoteConfig = remoteConfigSettingsHelper?.remoteConfigContent()
        if (remoteConfig.notNullOrBlank()) {
            if (configReader is MutableConfigReader) {
                configReader.loadConfigInfoContent(remoteConfig!!)
                logger.debug("load remote config")
                devEnv!!.dev {
                    logger.debug("----------------\n$remoteConfig\n----------------")
                }
            } else {
                logger.warn("failed to load remote config")
            }
        }
    }
}