package com.itangcent.easyapi.core.config.parser

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.config.resource.ConfigResourceLoader
import com.itangcent.easyapi.core.config.resource.LoadedConfigResource
import com.itangcent.easyapi.core.settings.HttpClientType
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.framework.spi.FrameworkRegistry
import com.itangcent.easyapi.testFramework.ConstantSettingBinder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConfigTextParserTest {

    private fun newParser(
        loader: ConfigResourceLoader? = null
    ): ConfigTextParser {
        val project = mock<Project>()
        val settingBinder = ConstantSettingBinder()
        whenever(project.getService(SettingBinder::class.java)).thenReturn(settingBinder)
        val frameworkRegistry = mock<FrameworkRegistry>()
        whenever(project.getService(FrameworkRegistry::class.java)).thenReturn(frameworkRegistry)
        whenever(frameworkRegistry.isEnabled("Feign")).thenReturn(false)
        whenever(frameworkRegistry.isEnabled("JAX-RS")).thenReturn(true)
        whenever(frameworkRegistry.isEnabled("SpringActuator")).thenReturn(false)
        if (loader != null) {
            whenever(project.getService(ConfigResourceLoader::class.java)).thenReturn(loader)
        }
        return ConfigTextParser(project)
    }

    @Test
    fun testParseSimpleKeyValue() {
        val parser = newParser()
        val entries = runBlocking { parser.parse("api.name=test-api", "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("test-api", entries[0].value)
    }

    @Test
    fun testParseColonSeparator() {
        val parser = newParser()
        val entries = runBlocking { parser.parse("api.name:my-api", "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("my-api", entries[0].value)
    }

    @Test
    fun testParseSkipsComments() {
        val parser = newParser()
        val entries = runBlocking { parser.parse("# this is a comment\napi.name=test", "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
    }

    @Test
    fun testParseSkipsEmptyLines() {
        val parser = newParser()
        val entries = runBlocking { parser.parse("\n\napi.name=test\n\n", "test").toList() }
        assertEquals(1, entries.size)
    }

    @Test
    fun testParseMultipleEntries() {
        val parser = newParser()
        val text = """
            api.name=test-api
            api.version=1.0
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(2, entries.size)
        assertEquals("test-api", entries[0].value)
        assertEquals("1.0", entries[1].value)
    }

    @Test
    fun testParseMultilineValue() {
        val parser = newParser()
        val text = """
            api.description=```
            Line 1
            Line 2
            ```
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertTrue("Should contain Line 1", entries[0].value.contains("Line 1"))
        assertTrue("Should contain Line 2", entries[0].value.contains("Line 2"))
    }

    @Test
    fun testParseConditionalWithTrueCondition() {
        val parser = newParser()
        val text = """
            ###if httpClient==${HttpClientType.APACHE.value}
            api.name=conditional-api
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("conditional-api", entries[0].value)
    }

    @Test
    fun testParseConditionalWithFalseCondition() {
        val parser = newParser()
        val text = """
            ###if httpClient==${HttpClientType.DEFAULT.value}
            api.name=conditional-api
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertTrue("Should have no entries for false condition", entries.isEmpty())
    }

    @Test
    fun testParseDirectiveNotYielded() {
        val parser = newParser()
        val text = """
            ###set resolveProperty=false
            api.name=test
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
    }

    @Test
    fun testParseBracketedKeyWithEquals() {
        val parser = newParser()
        val entries = runBlocking { parser.parse("api.name[true]=test-api", "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name[true]", entries[0].key)
        assertEquals("test-api", entries[0].value)
    }

    @Test
    fun testParseBracketedKeyWithColonSeparator() {
        val parser = newParser()
        val entries = runBlocking { parser.parse("api.name[true]:test-api", "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name[true]", entries[0].key)
        assertEquals("test-api", entries[0].value)
    }

    @Test
    fun testParseColonInsideBracketsNotTreatedAsSeparator() {
        val parser = newParser()
        val entries = runBlocking { parser.parse("json.rule.convert[#regex:some.Type]=replacement", "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("json.rule.convert[#regex:some.Type]", entries[0].key)
        assertEquals("replacement", entries[0].value)
    }

    @Test
    fun testParseRegexFilterWithAngleBracketsAndCaptureGroups() {
        val parser = newParser()
        val text = "json.rule.convert[#regex:reactor.core.publisher.Flux<(.*?)>]=java.util.List<\${1}>"
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("json.rule.convert[#regex:reactor.core.publisher.Flux<(.*?)>]", entries[0].key)
        assertEquals("java.util.List<\${1}>", entries[0].value)
    }

    @Test
    fun testParseMultipleColonsInsideBrackets() {
        val parser = newParser()
        val entries = runBlocking { parser.parse("rule[a:b:c]=value", "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("rule[a:b:c]", entries[0].key)
        assertEquals("value", entries[0].value)
    }

    @Test
    fun testParseColonInValueAfterBracketedKey() {
        val parser = newParser()
        val entries = runBlocking { parser.parse("rule[filter]=host:port", "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("rule[filter]", entries[0].key)
        assertEquals("host:port", entries[0].value)
    }

    // ── properties.additional ──

    @Test
    fun testPropertiesAdditionalLoadsLocalFile() {
        val additionalContent = "api.name=from-additional"
        val loader = mock<ConfigResourceLoader>()
        runBlocking {
            whenever(loader.load("./additional.properties", "/base"))
                .thenReturn(LoadedConfigResource(additionalContent, "/base"))
        }
        val parser = newParser(loader = loader)
        val entries = runBlocking { parser.parse("properties.additional=./additional.properties", "test", "/base").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("from-additional", entries[0].value)
    }

    @Test
    fun testPropertiesAdditionalLoadsRemoteUrl() {
        val additionalContent = "api.name=from-remote"
        val loader = mock<ConfigResourceLoader>()
        runBlocking {
            whenever(loader.load("https://example.com/rules.config", "/base"))
                .thenReturn(LoadedConfigResource(additionalContent, "https://example.com"))
        }
        val parser = newParser(loader = loader)
        val entries = runBlocking { parser.parse("properties.additional=https://example.com/rules.config", "test", "/base").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("from-remote", entries[0].value)
    }

    @Test
    fun testPropertiesAdditionalThrowsWhenLoadFails() {
        val loader = mock<ConfigResourceLoader>()
        // load() returns null by default for unstubbed mocks
        val parser = newParser(loader = loader)
        try {
            runBlocking { parser.parse("properties.additional=./missing.properties", "test", "/base").toList() }
            fail("Expected IllegalStateException when additional resource cannot be resolved")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("./missing.properties"))
        }
    }

    @Test
    fun testPropertiesAdditionalIgnoredWhenLoadFailsWithDirective() {
        val loader = mock<ConfigResourceLoader>()
        val parser = newParser(loader = loader)
        val text = """
            ###set ignoreNotFoundFile=true
            properties.additional=./missing.properties
            api.name=still-here
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("still-here", entries[0].value)
    }

    @Test
    fun testPropertiesAdditionalPropagatesBaseDirFromLoadedResource() {
        // The included file references another relative include; the baseDir
        // for that nested include should come from the loaded resource, not
        // the parent's baseDir.
        val loader = mock<ConfigResourceLoader>()
        runBlocking {
            whenever(loader.load("./first.properties", "/original")).thenReturn(
                LoadedConfigResource(
                    "properties.additional=./second.properties\napi.name=first",
                    "/first-dir"
                )
            )
            whenever(loader.load("./second.properties", "/first-dir")).thenReturn(
                LoadedConfigResource("api.version=2.0", "/first-dir")
            )
        }
        val parser = newParser(loader = loader)
        val entries = runBlocking { parser.parse("properties.additional=./first.properties", "test", "/original").toList() }
        assertEquals(2, entries.size)
        // The nested include is processed first (properties.additional line
        // appears before api.name in first.properties), so api.version comes first.
        assertEquals("api.version", entries[0].key)
        assertEquals("2.0", entries[0].value)
        assertEquals("api.name", entries[1].key)
        assertEquals("first", entries[1].value)
    }

    // ── ###include ──

    @Test
    fun testIncludeDirectiveLoadsLocalFile() {
        val loader = mock<ConfigResourceLoader>()
        runBlocking {
            whenever(loader.load("./additional.properties", "/base"))
                .thenReturn(LoadedConfigResource("api.name=from-include", "/base"))
        }
        val parser = newParser(loader = loader)
        val entries = runBlocking { parser.parse("###include ./additional.properties", "test", "/base").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("from-include", entries[0].value)
    }

    @Test
    fun testIncludeDirectiveLoadsRemoteUrl() {
        val loader = mock<ConfigResourceLoader>()
        runBlocking {
            whenever(loader.load("https://example.com/rules.config", "/base"))
                .thenReturn(LoadedConfigResource("api.name=from-remote", "https://example.com"))
        }
        val parser = newParser(loader = loader)
        val entries = runBlocking { parser.parse("###include https://example.com/rules.config", "test", "/base").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("from-remote", entries[0].value)
    }

    @Test
    fun testIncludeDirectiveThrowsWhenLoadFails() {
        val loader = mock<ConfigResourceLoader>()
        val parser = newParser(loader = loader)
        try {
            runBlocking { parser.parse("###include ./missing.properties", "test", "/base").toList() }
            fail("Expected IllegalStateException when included resource cannot be resolved")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("./missing.properties"))
        }
    }

    @Test
    fun testIncludeDirectiveIgnoredWhenLoadFailsWithDirective() {
        val loader = mock<ConfigResourceLoader>()
        val parser = newParser(loader = loader)
        val text = """
            ###set ignoreNotFoundFile=true
            ###include ./missing.properties
            api.name=still-here
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("still-here", entries[0].value)
    }

    @Test
    fun testIncludeDirectiveSkippedInInactiveConditionalBlock() {
        val loader = mock<ConfigResourceLoader>()
        val parser = newParser(loader = loader)
        val text = """
            ###if builtInConfig==false
            ###include ./should-not-load.properties
            ###endif
            api.name=outside
        """.trimIndent()
        // builtInConfig defaults to null, so the condition is inactive and the
        // include must not be attempted (loader returns null -> would throw).
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("outside", entries[0].value)
    }

    @Test
    fun testIncludeDirectiveInheritsDirectiveState() {
        // ignoreUnresolved set before the include should carry into the
        // included file's entries (outer state is reused, not reset).
        val loader = mock<ConfigResourceLoader>()
        runBlocking {
            whenever(loader.load("./additional.properties", "/base"))
                .thenReturn(LoadedConfigResource("api.name=included", "/base"))
        }
        val parser = newParser(loader = loader)
        val text = """
            ###set ignoreUnresolved=true
            ###include ./additional.properties
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test", "/base").toList() }
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertTrue(entries[0].directives.ignoreUnresolved)
    }

    // ── Multi-line value with prefix (value ends with ``` but isn't just ```) ──

    @Test
    fun testParseMultilineValueWithPrefix() {
        val parser = newParser()
        val text = """
            api.description=prefix```
            Line 1
            Line 2
            ```
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertTrue("Should contain prefix", entries[0].value.contains("prefix"))
        assertTrue("Should contain Line 1", entries[0].value.contains("Line 1"))
        assertTrue("Should contain Line 2", entries[0].value.contains("Line 2"))
        assertFalse("Should not contain raw backticks", entries[0].value.contains("```"))
    }

    @Test
    fun testParseMultilineValueWithPrefixNoClosingBackticks() {
        // Multi-line value that reaches EOF without a closing ``` — the
        // while loop should exhaust lines gracefully.
        val parser = newParser()
        val text = "api.description=prefix```\nLine 1\nLine 2"
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertTrue("Should contain prefix", entries[0].value.contains("prefix"))
        assertTrue("Should contain Line 1", entries[0].value.contains("Line 1"))
        assertTrue("Should contain Line 2", entries[0].value.contains("Line 2"))
    }

    @Test
    fun testParseMultilineValueNoClosingBackticks() {
        // Multi-line value with just ``` (no prefix) that reaches EOF
        // without a closing ```.
        val parser = newParser()
        val text = "api.description=```\nLine 1\nLine 2"
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertTrue("Should contain Line 1", entries[0].value.contains("Line 1"))
        assertTrue("Should contain Line 2", entries[0].value.contains("Line 2"))
    }

    // ── Setting resolver keys (###if with various settings) ──

    @Test
    fun testParseConditionalLogLevel() {
        val parser = newParser()
        val text = """
            ###if logLevel==100
            api.name=log-level-match
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("log-level-match", entries[0].value)
    }

    @Test
    fun testParseConditionalLogLevelFalse() {
        val parser = newParser()
        val text = """
            ###if logLevel==50
            api.name=log-level-match
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testParseConditionalHttpTimeOut() {
        val parser = newParser()
        val text = """
            ###if httpTimeOut==30
            api.name=timeout-match
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("timeout-match", entries[0].value)
    }

    @Test
    fun testParseConditionalUnsafeSsl() {
        val parser = newParser()
        val text = """
            ###if unsafeSsl==false
            api.name=ssl-match
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("ssl-match", entries[0].value)
    }

    @Test
    fun testParseConditionalFeignEnable() {
        val parser = newParser()
        val text = """
            ###if feignEnable==false
            api.name=feign-match
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("feign-match", entries[0].value)
    }

    @Test
    fun testParseConditionalJaxrsEnable() {
        val parser = newParser()
        val text = """
            ###if jaxrsEnable==true
            api.name=jaxrs-match
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("jaxrs-match", entries[0].value)
    }

    @Test
    fun testParseConditionalActuatorEnable() {
        val parser = newParser()
        val text = """
            ###if actuatorEnable==false
            api.name=actuator-match
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertEquals(1, entries.size)
        assertEquals("actuator-match", entries[0].value)
    }

    @Test
    fun testParseConditionalUnknownKeyEvaluatesFalse() {
        val parser = newParser()
        val text = """
            ###if unknownKey==value
            api.name=should-not-appear
            ###endif
        """.trimIndent()
        val entries = runBlocking { parser.parse(text, "test").toList() }
        assertTrue(entries.isEmpty())
    }
}
