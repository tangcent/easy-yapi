package com.itangcent.idea.plugin.config

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.common.utils.invokeMethod
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.adaptor.ModuleAdaptor.file
import com.itangcent.intellij.adaptor.ModuleAdaptor.filePath
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.MutableConfigReader
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.utils.Initializable
import org.apache.commons.io.IOUtils
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit


class RecommendConfigReader : ConfigReader, Initializable {

    @Inject
    @Named("delegate_config_reader")
    val configReader: ConfigReader? = null

    @Inject(optional = true)
    val settingBinder: SettingBinder? = null

    @Inject
    val contextSwitchListener: ContextSwitchListener? = null

    @Inject
    val logger: Logger? = null

    @Volatile
    private var loading: Boolean = false

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
        while (loading) {
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
                    loading = true
                    configReader.reset()
                    val moduleFile = module.file()
                    val modulePath = when {
                        moduleFile == null -> module.filePath()?.substringBeforeLast(File.separator)
                        moduleFile.isDirectory -> moduleFile.path
                        else -> moduleFile.parent.path
                    }
                    modulePath?.let { configReader.put("module_path", it) }
                    initDelegateAndRecommend()
                    loading = false
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
        if (settingBinder?.read()?.useRecommendConfig == true) {
            if (settingBinder.read().recommendConfigs.isEmpty()) {
                logger!!.info(
                        "Even useRecommendConfig was true, but no recommend config be selected!\n" +
                                "\n" +
                                "If you need to enable the built-in recommended configuration." +
                                "Go to [Preference -> Other Setting -> EasyApi -> Recommend]"
                )
                return
            }

            if (configReader is MutableConfigReader) {
                val recommendConfig = buildRecommendConfig(settingBinder.read().recommendConfigs.split(","))

                if (recommendConfig.isEmpty()) {
                    logger!!.warn("No recommend config be selected!")
                    return
                }

                configReader.loadConfigInfoContent(recommendConfig)
                logger!!.info("use recommend config")
            } else {
                logger!!.warn("failed to use recommend config")
            }
        }
    }

    companion object {

        init {
            loadRecommendConfig()
            resolveRecommendConfig(RECOMMEND_CONFIG_PLAINT)
        }

        private const val config_name = ".recommend.easy.api.config"

        private fun loadRecommendConfig(): String {
            val config = IOUtils.toString(RecommendConfigReader::class.java.classLoader.getResourceAsStream(config_name)
                    ?: RecommendConfigReader::class.java.getResourceAsStream(config_name),
                    Charsets.UTF_8)
            RECOMMEND_CONFIG_PLAINT = config
            return config
        }

        private fun resolveRecommendConfig(config: String) {
            val recommendConfig: MutableMap<String, String> = LinkedHashMap()
            val recommendConfigCodes: MutableList<String> = LinkedList()
            var code: String? = null
            var content: String? = ""
            for (line in config.lines()) {
                if (line.startsWith("#[")) {
                    if (code != null) {
                        recommendConfigCodes.add(code)
                        recommendConfig[code] = content ?: ""
                        content = ""
                    }
                    code = line.removeSurrounding("#[", "]")
                } else {
                    if (content.isNullOrBlank()) {
                        content = line
                    } else {
                        content += "\n"
                        content += line
                    }
                }
            }

            if (code != null) {
                recommendConfigCodes.add(code)
                recommendConfig[code] = content ?: ""
            }

            RECOMMEND_CONFIG_CODES = recommendConfigCodes.toTypedArray()
            RECOMMEND_CONFIG_MAP = recommendConfig
        }

        fun buildRecommendConfig(codes: List<String>): String {
            return RECOMMEND_CONFIG_CODES
                    .filter { codes.contains(it) }
                    .map { RECOMMEND_CONFIG_MAP[it] }
                    .joinToString("\n")

        }

        lateinit var RECOMMEND_CONFIG_PLAINT: String
        lateinit var RECOMMEND_CONFIG_CODES: Array<String>
        lateinit var RECOMMEND_CONFIG_MAP: Map<String, String>
    }
}