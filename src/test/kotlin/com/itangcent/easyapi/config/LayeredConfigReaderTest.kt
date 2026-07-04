package com.itangcent.easyapi.config

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.config.parser.DirectiveSnapshot
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.testFramework.ConstantSettingBinder
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LayeredConfigReaderTest {

    private val parser: ConfigTextParser = run {
        val project = mock<Project>()
        val settingBinder = ConstantSettingBinder()
        whenever(project.getService(SettingBinder::class.java)).thenReturn(settingBinder)
        ConfigTextParser(project)
    }

    @Test
    fun testConfigTextParserMultilineBlock() {
        val text = """
            field.ignore=groovy:```
                return session.get("json-ignore", fieldContext.path())
            ```
            api.name=test
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(2, entries.size)
        assertEquals("field.ignore", entries[0].key)
        assertTrue(entries[0].value.startsWith("groovy:"))
        assertTrue(entries[0].value.contains("session.get"))
        assertEquals("api.name", entries[1].key)
        assertEquals("test", entries[1].value)
    }

    @Test
    fun testConfigTextParserMultilineBlockWithoutPrefix() {
        val text = """
            ignored.classes=```
                java.lang.Class,
                java.lang.ClassLoader
            ```
            api.name=test
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(2, entries.size)
        assertEquals("ignored.classes", entries[0].key)
        assertTrue(entries[0].value.contains("java.lang.Class"))
        assertEquals("api.name", entries[1].key)
        assertEquals("test", entries[1].value)
    }

    @Test
    fun testConfigTextParserInlineValue() {
        val text = """
            field.ignore=groovy:!it.containingClass().name().startsWith("java.lang")
            api.name=test
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(2, entries.size)
        assertEquals("field.ignore", entries[0].key)
        assertTrue(entries[0].value.startsWith("groovy:"))
        assertEquals("api.name", entries[1].key)
        assertEquals("test", entries[1].value)
    }

    @Test
    fun testGetFirstWithSingleSource() = runBlocking {
        val source = TestConfigSource(
            listOf(
                ConfigEntry("api.name", "Test API", "test"),
                ConfigEntry("api.version", "1.0.0", "test")
            ),
            priority = 0
        )
        val reader = LayeredConfigReader(listOf(source))
        reader.reload()

        assertEquals("Test API", reader.getFirst("api.name"))
        assertEquals("1.0.0", reader.getFirst("api.version"))
        assertNull(reader.getFirst("nonexistent"))
    }

    @Test
    fun testGetAllWithSingleSource() = runBlocking {
        val source = TestConfigSource(
            listOf(
                ConfigEntry("method.doc", "tag1", "test"),
                ConfigEntry("method.doc", "tag2", "test"),
                ConfigEntry("method.doc", "tag3", "test")
            ),
            priority = 0
        )
        val reader = LayeredConfigReader(listOf(source))
        reader.reload()

        val tags = reader.getAll("method.doc")
        assertEquals(3, tags.size)
        assertTrue(tags.contains("tag1"))
        assertTrue(tags.contains("tag2"))
        assertTrue(tags.contains("tag3"))
    }

    @Test
    fun testMultipleSourcesPriority() = runBlocking {
        val source1 = TestConfigSource(
            listOf(
                ConfigEntry("api.name", "Source1", "test1"),
                ConfigEntry("api.priority", "low", "test1")
            ),
            priority = 0
        )
        val source2 = TestConfigSource(
            listOf(
                ConfigEntry("api.name", "Source2", "test2"),
                ConfigEntry("api.priority", "high", "test2")
            ),
            priority = 10
        )

        val reader = LayeredConfigReader(listOf(source1, source2))
        reader.reload()

        assertEquals("Source2", reader.getFirst("api.name"))
    }

    @Test
    fun testPropertyResolution() = runBlocking {
        val source = TestConfigSource(
            listOf(
                ConfigEntry("api.base", "/api/v1", "test"),
                ConfigEntry("api.endpoint", "\${api.base}/users", "test")
            ),
            priority = 0
        )
        val reader = LayeredConfigReader(listOf(source))
        reader.reload()

        assertEquals("/api/v1/users", reader.getFirst("api.endpoint"))
    }

    @Test
    fun testEmptySource() = runBlocking {
        val reader = LayeredConfigReader(emptyList())
        reader.reload()

        assertNull(reader.getFirst("any.key"))
        assertTrue(reader.getAll("any.key").isEmpty())
    }

    @Test
    fun testGetRules() = runBlocking {
        val source = TestConfigSource(
            listOf(
                ConfigEntry("rule.1", "first", "test"),
                ConfigEntry("rule.2", "second", "test")
            ),
            priority = 0
        )
        val reader = LayeredConfigReader(listOf(source))
        reader.reload()

        val rules = reader.getAll("rule.1")
        assertEquals(1, rules.size)
        assertEquals("first", rules[0])
    }

    @Test
    fun testIgnoreUnresolved() = runBlocking {
        val source = TestConfigSource(
            listOf(
                ConfigEntry(
                    "api.value", "\${unresolved.property}", "test",
                    DirectiveSnapshot(ignoreUnresolved = true)
                )
            ),
            priority = 0
        )
        val reader = LayeredConfigReader(listOf(source))
        reader.reload()

        val value = reader.getFirst("api.value")
        assertNotNull(value)
    }

    @Test
    fun testReloadDetectsChanges() = runBlocking {
        var entries = listOf(
            ConfigEntry("api.name", "Original", "test")
        )
        val source = object : ConfigSource {
            override val priority: Int = 0
            override val sourceId: String = "test"
            override suspend fun collect(): Sequence<ConfigEntry> = entries.asSequence()
        }

        val reader = LayeredConfigReader(listOf(source))
        reader.reload()

        assertEquals("Original", reader.getFirst("api.name"))

        entries = listOf(ConfigEntry("api.name", "Updated", "test"))
        reader.reload()

        assertEquals("Updated", reader.getFirst("api.name"))
    }

    @Test
    fun testSourcesForKeyReturnsValuesOrderedByPriorityDesc() = runBlocking {
        val lowSource = TestConfigSource(
            listOf(ConfigEntry("api.name", "LowValue", "low-src")),
            priority = 1,
            sourceId = "low-src"
        )
        val highSource = TestConfigSource(
            listOf(ConfigEntry("api.name", "HighValue", "high-src")),
            priority = 10,
            sourceId = "high-src"
        )

        val reader = LayeredConfigReader(listOf(lowSource, highSource))
        reader.reload()

        val sourceValues = reader.sourcesForKey("api.name")
        assertEquals(2, sourceValues.size)
        // Highest priority first
        assertEquals("high-src", sourceValues[0].sourceId)
        assertEquals(10, sourceValues[0].priority)
        assertEquals("HighValue", sourceValues[0].value)
        assertEquals("low-src", sourceValues[1].sourceId)
        assertEquals(1, sourceValues[1].priority)
        assertEquals("LowValue", sourceValues[1].value)
    }

    @Test
    fun testSourcesForKeyEmptyForMissingKey() = runBlocking {
        val source = TestConfigSource(
            listOf(ConfigEntry("api.name", "Present", "test")),
            priority = 0
        )
        val reader = LayeredConfigReader(listOf(source))
        reader.reload()

        assertTrue(reader.sourcesForKey("missing.key").isEmpty())
    }

    @Test
    fun testSourcesForKeyConsistentWithGetAll() = runBlocking {
        val source1 = TestConfigSource(
            listOf(ConfigEntry("method.doc", "a", "s1")),
            priority = 5,
            sourceId = "s1"
        )
        val source2 = TestConfigSource(
            listOf(ConfigEntry("method.doc", "b", "s2")),
            priority = 2,
            sourceId = "s2"
        )
        val reader = LayeredConfigReader(listOf(source1, source2))
        reader.reload()

        val all = reader.getAll("method.doc")
        val sourceValues = reader.sourcesForKey("method.doc")
        assertEquals(all, sourceValues.map { it.value })
    }

    @Test
    fun testDefaultSourcesForKeyReturnsEmpty() = runBlocking {
        // A ConfigReader that doesn't override sourcesForKey should return empty.
        val reader = object : ConfigReader {
            override fun getFirst(key: String): String? = null
            override fun getAll(key: String): List<String> = emptyList()
            override suspend fun reload() {}
            override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {}
        }
        assertTrue(reader.sourcesForKey("any").isEmpty())
    }

    private class TestConfigSource(
        private val testEntries: List<ConfigEntry>,
        override val priority: Int,
        override val sourceId: String = "test"
    ) : ConfigSource {
        override suspend fun collect(): Sequence<ConfigEntry> = testEntries.asSequence()
    }
}
