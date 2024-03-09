package com.itangcent.idea.plugin.api.export.postman

import com.itangcent.intellij.config.ConfigProvider
import com.itangcent.intellij.config.ConfigProviderTest
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test case of [PostmanConfigProvider]
 */
internal class PostmanConfigProviderTest : ConfigProviderTest() {

    override val configProviderClass: KClass<out ConfigProvider>
        get() = PostmanConfigProvider::class

    override fun loadConfigs(): Array<String> {
        return arrayOf(
            "config/.postman.config",
            "config/.postman.yml",
            "config/.postman.yaml",
            "config/a/.postman.config",
            "config/a/.postman.yml",
            "config/a/.postman.yaml",

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
            resourceId("config/a/.postman.config") + "\n" +
                    resourceId("config/a/.postman.yml") + "\n" +
                    resourceId("config/a/.postman.yaml") + "\n" +
                    resourceId("config/a/.easy.api.config") + "\n" +
                    resourceId("config/a/.easy.api.yml") + "\n" +
                    resourceId("config/a/.easy.api.yaml") + "\n" +
                    resourceId("config/.postman.config") + "\n" +
                    resourceId("config/.postman.yml") + "\n" +
                    resourceId("config/.postman.yaml") + "\n" +
                    resourceId("config/.easy.api.config") + "\n" +
                    resourceId("config/.easy.api.yml") + "\n" +
                    resourceId("config/.easy.api.yaml"),
            configProvider.loadConfig().joinToString("\n") { it.id })
    }
}