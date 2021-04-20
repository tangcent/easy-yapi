package com.itangcent.idea.plugin.config

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.common.utils.invokeMethod
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.plugin.settings.helper.BuiltInConfigSettingsHelper
import com.itangcent.idea.plugin.settings.helper.RecommendConfigSettingsHelper
import com.itangcent.intellij.adaptor.ModuleAdaptor.file
import com.itangcent.intellij.adaptor.ModuleAdaptor.filePath
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.MutableConfigReader
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.jvm.dev.DevEnv
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.utils.Initializable
import java.io.File
import java.util.concurrent.TimeUnit


class RecommendConfigReader : ConfigReader, Initializable {

    @Inject
    @Named("delegate_config_reader")
    private val configReader: ConfigReader? = null

    @Inject(optional = true)
    private val builtInConfigSettingsHelper: BuiltInConfigSettingsHelper? = null

    @Inject(optional = true)
    private val recommendConfigSettingsHelper: RecommendConfigSettingsHelper? = null

    @Inject
    private val contextSwitchListener: ContextSwitchListener? = null

    @Inject
    private val logger: Logger? = null

    @Inject
    private val devEnv: DevEnv? = null

    @Volatile
    private var loading: Thread? = null

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
        while (loading != null && loading != Thread.currentThread()) {
            TimeUnit.MILLISECONDS.sleep(100)
        }
    }

    @PostConstruct
    override fun init() {

        if (configReader is MutableConfigReader) {
            contextSwitchListener!!.clear()
            contextSwitchListener.onModuleChange { module ->
                synchronized(this)
                {
                    loading = Thread.currentThread()
                    configReader.reset()
                    val moduleFile = module.file()
                    val modulePath = when {
                        moduleFile == null -> module.filePath()?.substringBeforeLast(File.separator)
                        moduleFile.isDirectory -> moduleFile.path
                        else -> moduleFile.parent.path
                    }
                    modulePath?.let { configReader.put("module_path", it) }
                    initDelegateAndRecommend()
                    loading = null
                }
            }
        } else {
            initDelegateAndRecommend()
        }
    }

    private fun initDelegateAndRecommend() {
        try {
            if (configReader is Initializable) {
                configReader.init()
            } else {
                configReader?.invokeMethod("init")
            }
        } catch (e: Throwable) {
        }
        tryLoadRecommend()
        tryLoadBuiltIn()
    }

    private fun tryLoadRecommend() {
        if (recommendConfigSettingsHelper?.useRecommendConfig() == true) {
            if (configReader is MutableConfigReader) {
                val recommendConfig = recommendConfigSettingsHelper.loadRecommendConfig()

                if (recommendConfig.isEmpty()) {
                    logger!!.info(
                            "Even useRecommendConfig was true, but no recommend config be selected!\n" +
                                    "\n" +
                                    "If you need to enable the built-in recommended configuration." +
                                    "Go to [Preference -> Other Setting -> EasyApi -> Recommend]"
                    )

                    return
                }

                configReader.loadConfigInfoContent(recommendConfig)
                logger!!.info("use recommend config")
                devEnv!!.dev {
                    logger.debug("----------------\n$recommendConfig\n----------------")
                }
            } else {
                logger!!.warn("failed to use recommend config")
            }
        }
    }

    private fun tryLoadBuiltIn() {
        val builtInConfig = builtInConfigSettingsHelper?.builtInConfig()
        if (builtInConfig.notNullOrBlank()) {
            if (configReader is MutableConfigReader) {
                configReader.loadConfigInfoContent(builtInConfig!!)
                logger!!.info("use built-in config")
                devEnv!!.dev {
                    logger.debug("----------------\n$builtInConfig\n----------------")
                }
            } else {
                logger!!.warn("failed to use recommend config")
            }
        }
    }
}