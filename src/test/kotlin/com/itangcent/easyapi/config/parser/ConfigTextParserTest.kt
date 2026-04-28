package com.itangcent.easyapi.config.parser

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

class ConfigTextParserTest {

    @Test
    fun testParseSimpleKeyValue() {
        val parser = ConfigTextParser(null)
        val entries = parser.parse("api.name=test-api", "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("test-api", entries[0].value)
    }

    @Test
    fun testParseColonSeparator() {
        val parser = ConfigTextParser(null)
        val entries = parser.parse("api.name:my-api", "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("my-api", entries[0].value)
    }

    @Test
    fun testParseSkipsComments() {
        val parser = ConfigTextParser(null)
        val entries = parser.parse("# this is a comment\napi.name=test", "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
    }

    @Test
    fun testParseSkipsEmptyLines() {
        val parser = ConfigTextParser(null)
        val entries = parser.parse("\n\napi.name=test\n\n", "test").toList()
        assertEquals(1, entries.size)
    }

    @Test
    fun testParseMultipleEntries() {
        val parser = ConfigTextParser(null)
        val text = """
            api.name=test-api
            api.version=1.0
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(2, entries.size)
        assertEquals("test-api", entries[0].value)
        assertEquals("1.0", entries[1].value)
    }

    @Test
    fun testParseMultilineValue() {
        val parser = ConfigTextParser(null)
        val text = """
            api.description=```
            Line 1
            Line 2
            ```
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertTrue("Should contain Line 1", entries[0].value.contains("Line 1"))
        assertTrue("Should contain Line 2", entries[0].value.contains("Line 2"))
    }

    @Test
    fun testParseConditionalWithTrueCondition() {
        val settings = Settings(httpClient = "APACHE")
        val parser = ConfigTextParser(settings)
        val text = """
            ###if httpClient==APACHE
            api.name=conditional-api
            ###endif
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("conditional-api", entries[0].value)
    }

    @Test
    fun testParseConditionalWithFalseCondition() {
        val settings = Settings(httpClient = "APACHE")
        val parser = ConfigTextParser(settings)
        val text = """
            ###if httpClient==URL_CONNECTION
            api.name=conditional-api
            ###endif
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertTrue("Should have no entries for false condition", entries.isEmpty())
    }

    @Test
    fun testParseDirectiveNotYielded() {
        val parser = ConfigTextParser(null)
        val text = """
            ###set resolveProperty=false
            api.name=test
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
    }

    @Test
    fun testParseBracketedKeyWithEquals() {
        val parser = ConfigTextParser(null)
        val entries = parser.parse("api.name[true]=test-api", "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.name[true]", entries[0].key)
        assertEquals("test-api", entries[0].value)
    }

    @Test
    fun testParseBracketedKeyWithColonSeparator() {
        val parser = ConfigTextParser(null)
        val entries = parser.parse("api.name[true]:test-api", "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.name[true]", entries[0].key)
        assertEquals("test-api", entries[0].value)
    }

    @Test
    fun testParseColonInsideBracketsNotTreatedAsSeparator() {
        val parser = ConfigTextParser(null)
        val entries = parser.parse("json.rule.convert[#regex:some.Type]=replacement", "test").toList()
        assertEquals(1, entries.size)
        assertEquals("json.rule.convert[#regex:some.Type]", entries[0].key)
        assertEquals("replacement", entries[0].value)
    }

    @Test
    fun testParseRegexFilterWithAngleBracketsAndCaptureGroups() {
        val parser = ConfigTextParser(null)
        val text = "json.rule.convert[#regex:reactor.core.publisher.Flux<(.*?)>]=java.util.List<\${1}>"
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("json.rule.convert[#regex:reactor.core.publisher.Flux<(.*?)>]", entries[0].key)
        assertEquals("java.util.List<\${1}>", entries[0].value)
    }

    @Test
    fun testParseMultipleColonsInsideBrackets() {
        val parser = ConfigTextParser(null)
        val entries = parser.parse("rule[a:b:c]=value", "test").toList()
        assertEquals(1, entries.size)
        assertEquals("rule[a:b:c]", entries[0].key)
        assertEquals("value", entries[0].value)
    }

    @Test
    fun testParseColonInValueAfterBracketedKey() {
        val parser = ConfigTextParser(null)
        val entries = parser.parse("rule[filter]=host:port", "test").toList()
        assertEquals(1, entries.size)
        assertEquals("rule[filter]", entries[0].key)
        assertEquals("host:port", entries[0].value)
    }
}
