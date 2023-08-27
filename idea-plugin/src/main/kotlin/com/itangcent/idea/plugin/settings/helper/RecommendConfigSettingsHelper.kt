package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.utils.appendln
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.update
import com.itangcent.common.utils.ResourceUtils
import java.util.*

@Singleton
class RecommendConfigSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    fun useRecommendConfig(): Boolean {
        return settingBinder.read().useRecommendConfig
    }

    fun loadRecommendConfig(): String {
        return RecommendConfigLoader.buildRecommendConfig(settingBinder.read().recommendConfigs)
    }

    fun addConfig(code: String) {
        settingBinder.update {
            recommendConfigs = RecommendConfigLoader.addSelectedConfig(recommendConfigs, code)
        }
    }

    fun removeConfig(vararg code: String) {
        settingBinder.update {
            recommendConfigs = RecommendConfigLoader.removeSelectedConfig(recommendConfigs, *code)
        }
    }
}

@Singleton
object RecommendConfigLoader {

    fun plaint(): String {
        return RECOMMEND_CONFIG_PLAINT
    }

    fun buildRecommendConfig(codes: String, separator: CharSequence = "\n"): String {
        val set = codes.split(",").toSet()
        return RECOMMEND_CONFIGS
            .filter { set.contains(it.code) || (it.default && !set.contains("-${it.code}")) }
            .joinToString(separator) { it.content }
    }

    fun addSelectedConfig(codes: String, vararg code: String): String {
        val set = LinkedHashSet(codes.split(","))
        set.addAll(code)
        code.map { "-$it" }.forEach { set.remove(it) }
        return set.joinToString(",")
    }

    fun removeSelectedConfig(codes: String, vararg code: String): String {
        val set = LinkedHashSet(codes.split(","))
        set.removeAll(code)
        code.map { "-$it" }.forEach { set.add(it) }
        return set.joinToString(",")
    }

    fun codes(): Array<String> {
        return RECOMMEND_CONFIGS.mapToTypedArray { it.code }
    }

    fun selectedCodes(codes: String): Array<String> {
        val set = codes.split(",").toSet()
        return RECOMMEND_CONFIGS
            .filter { set.contains(it.code) || (it.default && !set.contains("-${it.code}")) }
            .map { it.code }
            .toTypedArray()
    }

    fun defaultCodes(): String {
        return RECOMMEND_CONFIGS
            .filter { it.default }
            .joinToString(",") { it.code }
    }

    init {
        loadRecommendConfig()
        resolveRecommendConfig(RECOMMEND_CONFIG_PLAINT)
    }

    private const val config_name = ".recommend.easy.api.config"

    private fun loadRecommendConfig(): String {
        val config = ResourceUtils.readResource(config_name)
        RECOMMEND_CONFIG_PLAINT = config
        return config
    }

    private fun resolveRecommendConfig(config: String) {
        val recommendConfigCodes: MutableList<RecommendConfig> = LinkedList()
        var code: String? = null
        var content: String? = ""
        var default: Boolean = false
        for (line in config.lines()) {
            if (line.startsWith("#[")) {
                if (code != null) {
                    recommendConfigCodes.add(RecommendConfig(code, default, content!!))
                    content = ""
                    default = false
                }
                if (line.endsWith("*")) {
                    default = true
                    code = line.removeSurrounding("#[", "]*")
                } else {
                    code = line.removeSurrounding("#[", "]")
                }
            } else {
                content = content.appendln(line)
            }
        }

        if (code != null) {
            recommendConfigCodes.add(RecommendConfig(code, default, content!!))
        }

        RECOMMEND_CONFIGS = recommendConfigCodes.toTypedArray()
    }

    /**
     * Get the content of the specified code
     *
     * @param key the specified code
     * @return the content of the specified code
     */
    operator fun get(key: String?): String? {
        return RECOMMEND_CONFIGS.firstOrNull { it.code == key }?.content
    }

    /**
     * Returns the code at the specified index
     *
     * @return the code at the specified index
     */
    operator fun get(index: Int): String {
        return RECOMMEND_CONFIGS[index].code
    }

    private lateinit var RECOMMEND_CONFIG_PLAINT: String
    private lateinit var RECOMMEND_CONFIGS: Array<RecommendConfig>

    data class RecommendConfig(
        val code: String,
        val default: Boolean,
        val content: String,
    )
}