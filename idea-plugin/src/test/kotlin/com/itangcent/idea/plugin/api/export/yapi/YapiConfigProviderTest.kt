package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.intellij.config.ConfigProvider
import com.itangcent.intellij.config.ConfigProviderTest
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test case of [YapiConfigProvider]
 */
internal class YapiConfigProviderTest : ConfigProviderTest() {

    override val configProviderClass: KClass<out ConfigProvider>
        get() = YapiConfigProvider::class

    override fun loadConfigs(): Array<String> {
        return arrayOf(
            "config/.yapi.config",
            "config/.yapi.yml",
            "config/.yapi.yaml",
            "config/a/.yapi.config",
            "config/a/.yapi.yml",
            "config/a/.yapi.yaml",

            "config/.easy.api.config",
            "config/.easy.api.yml",
            "config/.easy.api.yaml",
            "config/a/.easy.api.config",
            "config/a/.easy.api.yml",
            "config/a/.easy.api.yaml",
        )
    }

    @Test
    fun testConfig() {
        assertEquals(
            resourceId("config/a/.yapi.config") + "\n" +
                    resourceId("config/a/.yapi.yml") + "\n" +
                    resourceId("config/a/.yapi.yaml") + "\n" +
                    resourceId("config/a/.easy.api.config") + "\n" +
                    resourceId("config/a/.easy.api.yml") + "\n" +
                    resourceId("config/a/.easy.api.yaml") + "\n" +
                    resourceId("config/.yapi.config") + "\n" +
                    resourceId("config/.yapi.yml") + "\n" +
                    resourceId("config/.yapi.yaml") + "\n" +
                    resourceId("config/.easy.api.config") + "\n" +
                    resourceId("config/.easy.api.yml") + "\n" +
                    resourceId("config/.easy.api.yaml"),
            configProvider.loadConfig().joinToString("\n") { it.id })
    }
}