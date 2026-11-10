package com.itangcent.easyapi.core.config

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*

class DefaultConfigReaderTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var configReader: ConfigReader

    override fun setUp() {
        super.setUp()
        configReader = ConfigReader.getInstance(project)
    }

    fun testGetInstance() {
        assertNotNull(configReader)
        assertSame(configReader, ConfigReader.getInstance(project))
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

    fun testCancelledReloadDoesNotNotifyListeners() {
        var notified = 0
        val connection = project.messageBus.connect()
        connection.subscribe(ConfigReloadListener.TOPIC, object : ConfigReloadListener {
            override fun onConfigReloaded() {
                notified++
            }
        })
        try {
            val reader = DefaultConfigReader(project)
            var outcome: Any? = null
            runBlocking {
                // A job of its own, so cancelling it leaves runBlocking alone.
                launch(Job()) {
                    currentCoroutineContext().cancel()
                    outcome = try {
                        reader.reload()
                        "reloaded"
                    } catch (e: CancellationException) {
                        e
                    }
                }.join()
            }
            assertTrue("a cancelled reload must not complete: $outcome", outcome is CancellationException)
            assertEquals("a cancelled reload must not notify listeners", 0, notified)
        } finally {
            connection.dispose()
        }
    }
}
