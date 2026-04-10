package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.settings.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OnDemandConfigSourceTest {

    private lateinit var parser: ConfigTextParser

    @Before
    fun setUp() {
        parser = ConfigTextParser(Settings())
    }

    private class TestOnDemandConfigSource(
        private val enabled: Boolean,
        private val configText: String?,
        private val p: Int = 5
    ) : OnDemandConfigSource() {
        override val priority: Int = p
        override val sourceId: String = "test"

        override fun isEnabled(): Boolean = enabled

        override suspend fun loadConfig(): Sequence<ConfigEntry> {
            if (configText.isNullOrBlank()) {
                return emptySequence()
            }
            val testParser = ConfigTextParser(Settings())
            return testParser.parse(configText, sourceId, null)
        }
    }

    @Test
    fun testPriority() {
        val source = TestOnDemandConfigSource(true, "key=value")
        assertEquals(5, source.priority)
    }

    @Test
    fun testSourceId() {
        val source = TestOnDemandConfigSource(true, "key=value")
        assertEquals("test", source.sourceId)
    }

    @Test
    fun testDisabledSourceReturnsEmpty() = runBlocking {
        val source = TestOnDemandConfigSource(false, "key=value")
        val entries = source.collect().toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testEnabledSourceLoadsConfig() = runBlocking {
        val source = TestOnDemandConfigSource(true, "key=value")
        val entries = source.collect().toList()
        assertFalse(entries.isEmpty())
        assertEquals("value", entries.first().value)
    }

    @Test
    fun testNullConfigTextReturnsEmpty() = runBlocking {
        val source = TestOnDemandConfigSource(true, null)
        val entries = source.collect().toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testEmptyConfigTextReturnsEmpty() = runBlocking {
        val source = TestOnDemandConfigSource(true, "")
        val entries = source.collect().toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testBlankConfigTextReturnsEmpty() = runBlocking {
        val source = TestOnDemandConfigSource(true, "   \n  \t  ")
        val entries = source.collect().toList()
        assertTrue(entries.isEmpty())
    }
}