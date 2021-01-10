package com.itangcent.idea.plugin.config

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.utils.appendln
import com.itangcent.common.utils.mapToTypedArray
import com.itangcent.intellij.logger.Logger
import org.apache.commons.io.IOUtils
import java.util.*


@Singleton
object RecommendConfigLoader {

    @Inject
    val logger: Logger? = null

    fun plaint(): String {
        return RECOMMEND_CONFIG_PLAINT
    }

    fun buildRecommendConfig(codes: String, separator: CharSequence = "\n"): String {
        val set = codes.split(",").toSet()
        return RECOMMEND_CONFIGS
                .filter { set.contains(it.code) || (it.default && !set.contains("-${it.code}")) }
                .joinToString(separator) { it.content }
    }

    fun addSelectedConfig(codes: String, code: String): String {
        val set = codes.split(",").toHashSet()
        set.add(code)
        set.remove("-$code")
        return set.joinToString(",")
    }

    fun removeSelectedConfig(codes: String, code: String): String {
        val set = codes.split(",").toHashSet()
        set.remove(code)
        set.add("-$code")
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
        val config = IOUtils.toString(RecommendConfigReader::class.java.classLoader.getResourceAsStream(config_name)
                ?: RecommendConfigReader::class.java.getResourceAsStream(config_name),
                Charsets.UTF_8)
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

    operator fun get(key: String?): String? {
        return RECOMMEND_CONFIGS.first { it.code == key }.content
    }

    operator fun get(index: Int): String? {
        return RECOMMEND_CONFIGS[index].code
    }

    private lateinit var RECOMMEND_CONFIG_PLAINT: String
    private lateinit var RECOMMEND_CONFIGS: Array<RecommendConfig>


    data class RecommendConfig(val code: String,
                               val default: Boolean,
                               val content: String)
}