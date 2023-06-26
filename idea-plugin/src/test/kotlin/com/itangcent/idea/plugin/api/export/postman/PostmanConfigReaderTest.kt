package com.itangcent.idea.plugin.api.export.postman

import com.itangcent.intellij.config.AutoSearchConfigReaderTest
import com.itangcent.intellij.config.ConfigReader
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test case of [PostmanConfigReader]
 */
internal class PostmanConfigReaderTest : AutoSearchConfigReaderTest() {

    override val configReaderClass: KClass<out ConfigReader>
        get() = PostmanConfigReader::class

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
        assertEquals("cc", configReader.first("config.c"))
        assertEquals("ca", configReader.first("config.a"))
        assertEquals("cy", configReader.first("config.y"))
        assertEquals("cac", configReader.first("config.a.c"))
        assertEquals("caa", configReader.first("config.a.a"))
        assertEquals("cay", configReader.first("config.a.y"))

        assertEquals("cc", configReader.first("postman.config.c"))
        assertEquals("ca", configReader.first("postman.config.a"))
        assertEquals("cy", configReader.first("postman.config.y"))
        assertEquals("cac", configReader.first("postman.config.a.c"))
        assertEquals("caa", configReader.first("postman.config.a.a"))
        assertEquals("cay", configReader.first("postman.config.a.y"))
    }
}