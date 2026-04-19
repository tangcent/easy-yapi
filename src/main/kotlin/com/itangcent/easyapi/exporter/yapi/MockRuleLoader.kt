package com.itangcent.easyapi.exporter.yapi

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.ConfigReloadListener
import com.itangcent.easyapi.logging.IdeaLog

@Service(Service.Level.PROJECT)
class MockRuleLoader(
    private val project: Project
) : ConfigReloadListener, Disposable, IdeaLog {

    @Volatile
    private var cachedRules: Map<String, String>? = null

    private val connection = project.messageBus.connect(this)

    init {
        connection.subscribe(ConfigReloadListener.TOPIC, this)
    }

    fun getMockRules(): Map<String, String> {
        cachedRules?.let { return it }
        val rules = loadMockRules()
        cachedRules = rules
        return rules
    }

    private fun loadMockRules(): Map<String, String> {
        val configReader = ConfigReader.getInstance(project)
        val rules = mutableMapOf<String, String>()
        configReader.foreach(
            { key -> key == CONFIG_KEY || key.startsWith("$CONFIG_KEY[") },
            { _, value ->
                parseMockRule(value)?.let { (pattern, mockValue) ->
                    rules[pattern] = mockValue
                }
            }
        )
        return rules
    }

    override fun onConfigReloaded() {
        LOG.info("MockRuleLoader: clearing cache due to config reload")
        cachedRules = null
    }

    override fun dispose() {
        cachedRules = null
    }

    companion object {
        private const val CONFIG_KEY = "mock.rule"

        fun getInstance(project: Project): MockRuleLoader = project.service()

        internal fun parseMockRule(line: String): Pair<String, String>? {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
            val eqIndex = trimmed.indexOf('=')
            if (eqIndex <= 0 || eqIndex == trimmed.length - 1) return null
            val pattern = trimmed.substring(0, eqIndex).trim()
            val mockValue = trimmed.substring(eqIndex + 1).trim()
            if (pattern.isEmpty() || mockValue.isEmpty()) return null
            return pattern to mockValue
        }
    }
}
