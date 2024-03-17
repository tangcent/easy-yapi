package com.itangcent.idea.plugin.api.export.core

import com.itangcent.intellij.config.ConfigProvider
import com.itangcent.intellij.config.ConfigProviderTest
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test case of [EasyApiConfigProvider]
 */
internal class EasyApiConfigProviderTest : ConfigProviderTest() {
    override val configProviderClass: KClass<out ConfigProvider>
        get() = EasyApiConfigProvider::class

    @Test
    fun testConfig() {
        assertEquals(
            resourceId("config/a/.easy.api.config") + "\n" +
                    resourceId("config/a/.easy.api.yml") + "\n" +
                    resourceId("config/a/.easy.api.yaml") + "\n" +
                    resourceId("config/.easy.api.config") + "\n" +
                    resourceId("config/.easy.api.yml") + "\n" +
                    resourceId("config/.easy.api.yaml"),
            configProvider.loadConfig().joinToString("\n") { it.id })
    }
}