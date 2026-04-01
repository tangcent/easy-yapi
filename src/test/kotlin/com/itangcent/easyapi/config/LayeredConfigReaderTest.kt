package com.itangcent.easyapi.config

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.config.parser.DirectiveSnapshot
import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class LayeredConfigReaderTest {

    private val parser = ConfigTextParser(null)

    @Test
    fun testConfigTextParserMultilineBlock() {
        val text = """
field.ignore=groovy:```
    return session.get("json-ignore", fieldContext.path())
```
api.name=test
""".trimIndent()
        val entries = parser.parse(text, "test").toList()
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
        val entries = parser.parse(text, "test").toList()
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
        val entries = parser.parse(text, "test").toList()
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
                ConfigEntry("api.tag", "tag1", "test"),
                ConfigEntry("api.tag", "tag2", "test"),
                ConfigEntry("api.tag", "tag3", "test")
            ),
            priority = 0
        )
        val reader = LayeredConfigReader(listOf(source))
        reader.reload()

        val tags = reader.getAll("api.tag")
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
                ConfigEntry("api.value", "\${unresolved.property}", "test",
                    DirectiveSnapshot(ignoreUnresolved = true))
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

    private class TestConfigSource(
        private val testEntries: List<ConfigEntry>,
        override val priority: Int,
        override val sourceId: String = "test"
    ) : ConfigSource {
        override suspend fun collect(): Sequence<ConfigEntry> = testEntries.asSequence()
    }
}
