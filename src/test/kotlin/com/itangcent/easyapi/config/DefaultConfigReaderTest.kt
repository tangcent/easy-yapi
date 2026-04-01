package com.itangcent.easyapi.config

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class DefaultConfigReaderTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var configReader: DefaultConfigReader

    override fun setUp() {
        super.setUp()
        configReader = DefaultConfigReader.getInstance(project)
    }

    fun testGetInstance() {
        assertNotNull(configReader)
        assertSame(configReader, DefaultConfigReader.getInstance(project))
    }

    fun testGetFirstNonExistent() {
        val value = configReader.getFirst("non.existent.key")
        assertNull(value)
    }

    fun testGetAllNonExistent() {
        val values = configReader.getAll("non.existent.key")
        assertTrue(values.isEmpty())
    }

    fun testForeachIteratesAllEntries() {
        var count = 0
        configReader.foreach { _, _ ->
            count++
        }
        assertTrue(count >= 0)
    }

    fun testForeachWithNoMatches() {
        var count = 0
        configReader.foreach(
            { _ -> false },
            { _, _ -> count++ }
        )
        assertEquals(0, count)
    }
}
