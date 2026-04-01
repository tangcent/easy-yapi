package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.config.parser.DirectiveSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RecommendConfigSourceTest {

    private lateinit var configTextParser: ConfigTextParser

    @Before
    fun setUp() {
        configTextParser = mock()
    }

    @Test
    fun testPriority() {
        val source = RecommendConfigSource(null, configTextParser)
        assertEquals(2, source.priority)
    }

    @Test
    fun testSourceId() {
        val source = RecommendConfigSource(null, configTextParser)
        assertEquals("recommend", source.sourceId)
    }

    @Test
    fun testCollectWithNullSelected() {
        val parsedEntries = sequenceOf(
            ConfigEntry("rule", "value", "recommend", DirectiveSnapshot())
        )
        
        whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)
        
        val source = RecommendConfigSource(null, configTextParser)
        
        runBlocking {
            val entries = source.collect().toList()
            assertTrue(entries.isNotEmpty())
        }
    }

    @Test
    fun testCollectWithEmptySelected() {
        val parsedEntries = sequenceOf(
            ConfigEntry("rule", "value", "recommend", DirectiveSnapshot())
        )
        
        whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)
        
        val source = RecommendConfigSource("", configTextParser)
        
        runBlocking {
            val entries = source.collect().toList()
            assertTrue(entries.isNotEmpty())
        }
    }

    @Test
    fun testCollectWithBlankSelected() {
        val parsedEntries = sequenceOf(
            ConfigEntry("rule", "value", "recommend", DirectiveSnapshot())
        )
        
        whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)
        
        val source = RecommendConfigSource("   ", configTextParser)
        
        runBlocking {
            val entries = source.collect().toList()
            assertTrue(entries.isNotEmpty())
        }
    }

    @Test
    fun testCollectWithSelectedPresets() {
        val parsedEntries = sequenceOf(
            ConfigEntry("spring.rule", "value", "recommend", DirectiveSnapshot())
        )
        
        whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)
        
        val source = RecommendConfigSource("spring", configTextParser)
        
        runBlocking {
            val entries = source.collect().toList()
            assertTrue(entries.isNotEmpty())
        }
    }

    @Test
    fun testCollectWithCustomRegistry() {
        val customRegistry = mapOf("custom" to "custom.rule=value")
        val parsedEntries = sequenceOf(
            ConfigEntry("custom.rule", "value", "recommend", DirectiveSnapshot())
        )
        
        whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)
        
        val source = RecommendConfigSource("custom", configTextParser, customRegistry)
        
        runBlocking {
            val entries = source.collect().toList()
            assertTrue(entries.isNotEmpty())
        }
    }
}
