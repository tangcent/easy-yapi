package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.intellij.config.AutoSearchConfigReaderTest
import com.itangcent.intellij.config.ConfigReader
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test case of [YapiConfigReader]
 */
internal class YapiConfigReaderTest : AutoSearchConfigReaderTest() {

    override val configReaderClass: KClass<out ConfigReader>
        get() = YapiConfigReader::class

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
        assertEquals("cc", configReader.first("config.c"))
        assertEquals("ca", configReader.first("config.a"))
        assertEquals("cy", configReader.first("config.y"))
        assertEquals("cac", configReader.first("config.a.c"))
        assertEquals("caa", configReader.first("config.a.a"))
        assertEquals("cay", configReader.first("config.a.y"))

        assertEquals("cc", configReader.first("yapi.config.c"))
        assertEquals("ca", configReader.first("yapi.config.a"))
        assertEquals("cy", configReader.first("yapi.config.y"))
        assertEquals("cac", configReader.first("yapi.config.a.c"))
        assertEquals("caa", configReader.first("yapi.config.a.a"))
        assertEquals("cay", configReader.first("yapi.config.a.y"))
    }
}