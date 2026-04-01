package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.settings.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BuiltInConfigSourceTest {

    private lateinit var parser: ConfigTextParser

    @Before
    fun setUp() {
        parser = ConfigTextParser(Settings())
    }

    @Test
    fun testPriority() {
        val source = BuiltInConfigSource(true, parser)
        assertEquals(1, source.priority)
    }

    @Test
    fun testSourceId() {
        val source = BuiltInConfigSource(true, parser)
        assertEquals("builtin", source.sourceId)
    }

    @Test
    fun testDisabledSource() = runBlocking {
        val source = BuiltInConfigSource(false, parser)
        val entries = source.collect().toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testCustomText() = runBlocking {
        val customConfig = """
            api.name=Test API
            api.version=1.0.0
        """.trimIndent()
        val source = BuiltInConfigSource(true, parser, customConfig)
        val entries = source.collect().toList()
        assertTrue(entries.isNotEmpty())
    }

    @Test
    fun testEmptyCustomText() = runBlocking {
        val source = BuiltInConfigSource(true, parser, "")
        val entries = source.collect().toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testBlankCustomText() = runBlocking {
        val source = BuiltInConfigSource(true, parser, "   ")
        val entries = source.collect().toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testCustomTextWithMultiline() = runBlocking {
        val customConfig = """
            field.ignore=groovy:```
                return session.get("json-ignore", fieldContext.path())
            ```
            api.name=test
        """.trimIndent()
        val source = BuiltInConfigSource(true, parser, customConfig)
        val entries = source.collect().toList()
        assertTrue(entries.isNotEmpty())
    }
}
