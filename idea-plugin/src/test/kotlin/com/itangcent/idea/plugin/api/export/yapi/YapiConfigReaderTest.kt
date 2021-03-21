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
                "config${s}.yapi.config",
                "config${s}.yapi.yml",
                "config${s}.yapi.yaml",
                "config${s}a${s}.yapi.config",
                "config${s}a${s}.yapi.yml",
                "config${s}a${s}.yapi.yaml",

                "config${s}.easy.api.config",
                "config${s}.easy.api.yml",
                "config${s}.easy.api.yaml",
                "config${s}a${s}.easy.api.config",
                "config${s}a${s}.easy.api.yml",
                "config${s}a${s}.easy.api.yaml",
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