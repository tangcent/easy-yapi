package com.itangcent.idea.plugin.api.export

import com.itangcent.intellij.config.AutoSearchConfigReaderTest
import com.itangcent.intellij.config.ConfigReader
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals

/**
 * Test case of [EasyApiConfigReader]
 */
internal class EasyApiConfigReaderTest : AutoSearchConfigReaderTest() {
    override val configReaderClass: KClass<out ConfigReader>
        get() = EasyApiConfigReader::class

    @Test
    fun testConfig() {
        assertEquals("cc", configReader.first("config.c"))
        assertEquals("ca", configReader.first("config.a"))
        assertEquals("cy", configReader.first("config.y"))
        assertEquals("cac", configReader.first("config.a.c"))
        assertEquals("caa", configReader.first("config.a.a"))
        assertEquals("cay", configReader.first("config.a.y"))
    }

}