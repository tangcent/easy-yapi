package com.itangcent.easyapi.config.parser

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

class ConfigTextParserTest {

    private fun createParser(settings: Settings? = null): ConfigTextParser {
        return ConfigTextParser(settings)
    }

    @Test
    fun testParseSimpleKeyValue() {
        val parser = createParser()
        val text = """
            api.name=Test API
            api.version=1.0.0
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(2, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("Test API", entries[0].value)
        assertEquals("api.version", entries[1].key)
        assertEquals("1.0.0", entries[1].value)
    }

    @Test
    fun testParseWithColonSeparator() {
        val parser = createParser()
        val text = """
            field.name:@com.fasterxml.jackson.annotation.JsonProperty#value
            field.ignore:!it.hasModifier("transient")
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(2, entries.size)
        assertEquals("field.name", entries[0].key)
        assertEquals("@com.fasterxml.jackson.annotation.JsonProperty#value", entries[0].value)
        assertEquals("field.ignore", entries[1].key)
        assertEquals("!it.hasModifier(\"transient\")", entries[1].value)
    }

    @Test
    fun testParseWithMixedSeparators() {
        val parser = createParser()
        val text = """
            api.name=Test API
            field.name:@JsonProperty#value
            api.tag=groovy:it.name()
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(3, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("Test API", entries[0].value)
        assertEquals("field.name", entries[1].key)
        assertEquals("@JsonProperty#value", entries[1].value)
        assertEquals("api.tag", entries[2].key)
        assertEquals("groovy:it.name()", entries[2].value)
    }

    @Test
    fun testParseWithComments() {
        val parser = createParser()
        val text = """
            # This is a comment
            api.name=Test API
            # Another comment
            api.version=1.0.0
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(2, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("api.version", entries[1].key)
    }

    @Test
    fun testParseWithEmptyLines() {
        val parser = createParser()
        val text = """

            api.name=Test API

            api.version=1.0.0

        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(2, entries.size)
    }

    @Test
    fun testParseMultilineBlockWithPrefix() {
        val parser = createParser()
        val text = """
            field.ignore=groovy:```
                return session.get("json-ignore", fieldContext.path())
            ```
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("field.ignore", entries[0].key)
        assertTrue(entries[0].value.startsWith("groovy:"))
        assertTrue(entries[0].value.contains("session.get"))
        assertTrue(entries[0].value.contains("json-ignore"))
    }

    @Test
    fun testParseMultilineBlockWithoutPrefix() {
        val parser = createParser()
        val text = """
            ignored.classes=```
                java.lang.Class,
                java.lang.ClassLoader,
                java.lang.Module
            ```
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("ignored.classes", entries[0].key)
        assertTrue(entries[0].value.contains("java.lang.Class"))
        assertTrue(entries[0].value.contains("java.lang.ClassLoader"))
        assertFalse(entries[0].value.startsWith("groovy:"))
    }

    @Test
    fun testParseMultilineBlockPreservesIndentation() {
        val parser = createParser()
        val text = """
            field.advanced=@javax.validation.constraints.Size=groovy:```
                def element = (it.jsonType().name() == "java.lang.String")?"Length":"Items"
                def ann = it.annMap("javax.validation.constraints.Size")
                return [minElement: ann["min"], maxElement: ann["max"]]
            ```
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertTrue(entries[0].value.contains("def element"))
        assertTrue(entries[0].value.contains("def ann"))
        assertTrue(entries[0].value.contains("return [minElement"))
    }

    @Test
    fun testParseInlineGroovyScript() {
        val parser = createParser()
        val text = """
            field.ignore=groovy:!it.containingClass().name().startsWith("java.lang")
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("field.ignore", entries[0].key)
        assertEquals("groovy:!it.containingClass().name().startsWith(\"java.lang\")", entries[0].value)
    }

    @Test
    fun testParseMultipleEntriesWithSameKey() {
        val parser = createParser()
        val text = """
            api.tag=tag1
            api.tag=tag2
            api.tag=tag3
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(3, entries.size)
        entries.forEach { assertEquals("api.tag", it.key) }
        assertEquals(listOf("tag1", "tag2", "tag3"), entries.map { it.value })
    }

    @Test
    fun testParseWithDirectiveSetResolveProperty() {
        val parser = createParser()
        val text = """
            api.name=Test API
            ###set resolveProperty = false
            api.path=${'$'}{api.base}/users
            ###set resolveProperty = true
            api.version=1.0.0
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(3, entries.size)
    }

    @Test
    fun testParseWithDirectiveIfCondition() {
        val parser = createParser(Settings().apply { builtInConfig = "dev" })
        val text = """
            api.name=Test API
            ###if builtInConfig == "dev"
            api.debug=true
            ###endif
            api.version=1.0.0
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(3, entries.size)
        assertTrue(entries.any { it.key == "api.debug" && it.value == "true" })
    }

    @Test
    fun testParseWithDirectiveIfConditionFalse() {
        val parser = createParser(Settings().apply { builtInConfig = "prod" })
        val text = """
            api.name=Test API
            ###if builtInConfig == "dev"
            api.debug=true
            ###endif
            api.version=1.0.0
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        val keys = entries.map { it.key }
        assertTrue("Expected api.name and api.version, got: $keys", 
            keys.contains("api.name") && keys.contains("api.version"))
        assertFalse("api.debug should not be present when builtInConfig != dev", 
            entries.any { it.key == "api.debug" })
    }

    @Test
    fun testParseWithDirectiveIfNotEquals() {
        val parser = createParser(Settings().apply { builtInConfig = "prod" })
        val text = """
            ###if builtInConfig != "dev"
            api.production=true
            ###endif
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.production", entries[0].key)
        assertEquals("true", entries[0].value)
    }

    @Test
    fun testParseWithNestedConditions() {
        val parser = createParser(Settings().apply { 
            builtInConfig = "dev"
            remoteConfig = arrayOf("debug")
        })
        val text = """
            ###if builtInConfig == "dev"
            api.dev=true
            ###if remoteConfig == "debug"
            api.verbose=true
            ###endif
            ###endif
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(2, entries.size)
    }

    @Test
    fun testParseWithDirectiveIgnoreNotFoundFile() {
        val parser = createParser()
        val text = """
            ###set ignoreNotFoundFile = true
            properties.additional=nonexistent.properties
            api.name=Test API
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
    }

    @Test
    fun testParseWithDirectiveSetIgnoreNotFoundFile() {
        val state = DirectiveState()
        val parser = DirectiveParser(state, null)
        
        parser.handle("###set ignoreNotFoundFile = true")
        assertTrue("ignoreNotFoundFile should be true", state.ignoreNotFoundFile)
        
        parser.handle("###set ignoreNotFoundFile = false")
        assertFalse("ignoreNotFoundFile should be false", state.ignoreNotFoundFile)
    }

    @Test
    fun testParseWithDirectiveIgnoreUnresolved() {
        val parser = createParser()
        val text = """
            ###set ignoreUnresolved = true
            api.path=${'$'}{undefined.property}
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
    }

    @Test
    fun testParseComplexConfig() {
        val parser = createParser()
        val text = """
            # API Configuration
            api.name=My API
            api.version=1.0.0

            # Field rules
            field.name=@com.fasterxml.jackson.annotation.JsonProperty#value
            field.ignore=@com.fasterxml.jackson.annotation.JsonIgnore#value

            # Multi-line groovy script
            field.mock=@javax.validation.constraints.Size=groovy:```
                def ann = it.annMap("javax.validation.constraints.Size")
                if(ann.containsKey("min") && ann.containsKey("max")){
                    return "@string(" + ann["min"] + "," + ann["max"] + ")"
                }
                return null
            ```
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(5, entries.size)
    }

    @Test
    fun testParseValueWithEquals() {
        val parser = createParser()
        val text = """
            api.equation=a=b+c
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.equation", entries[0].key)
        assertEquals("a=b+c", entries[0].value)
    }

    @Test
    fun testParseValueWithColon() {
        val parser = createParser()
        val text = """
            api.url=https://example.com
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.url", entries[0].key)
        assertEquals("https://example.com", entries[0].value)
    }

    @Test
    fun testParseEmptyText() {
        val parser = createParser()
        val entries = parser.parse("", "test").toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testParseOnlyComments() {
        val parser = createParser()
        val text = """
            # Comment 1
            # Comment 2
            # Comment 3
        """.trimIndent()
        val entries = parser.parse(text, "test").toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testSourceIdPreserved() {
        val parser = createParser()
        val text = "api.name=Test"
        val entries = parser.parse(text, "my-source").toList()
        assertEquals(1, entries.size)
        assertEquals("my-source", entries[0].sourceId)
    }
}

class DirectiveParserTest {

    private fun createParser(state: DirectiveState, settings: Settings? = null): DirectiveParser {
        return DirectiveParser(state, settings)
    }

    @Test
    fun testHandleNonDirectiveLine() {
        val state = DirectiveState()
        val parser = createParser(state)
        assertFalse(parser.handle("api.name=Test"))
        assertFalse(parser.handle("# comment"))
        assertFalse(parser.handle(""))
    }

    @Test
    fun testSetResolveProperty() {
        val state = DirectiveState()
        val parser = createParser(state)
        assertTrue(state.resolveProperty)
        parser.handle("###set resolveProperty = false")
        assertFalse(state.resolveProperty)
        parser.handle("###set resolveProperty = true")
        assertTrue(state.resolveProperty)
    }

    @Test
    fun testSetResolvePropertyCaseInsensitive() {
        val state = DirectiveState()
        val parser = createParser(state)
        parser.handle("###set resolveProperty = FALSE")
        assertFalse(state.resolveProperty)
        parser.handle("###set resolveProperty = TRUE")
        assertTrue(state.resolveProperty)
    }

    @Test
    fun testSetIgnoreNotFoundFile() {
        val state = DirectiveState()
        val parser = createParser(state)
        assertFalse(state.ignoreNotFoundFile)
        parser.handle("###set ignoreNotFoundFile = true")
        assertTrue(state.ignoreNotFoundFile)
    }

    @Test
    fun testSetIgnoreUnresolved() {
        val state = DirectiveState()
        val parser = createParser(state)
        assertFalse(state.ignoreUnresolved)
        parser.handle("###set ignoreUnresolved = true")
        assertTrue(state.ignoreUnresolved)
    }

    @Test
    fun testSetResolveMulti() {
        val state = DirectiveState()
        val parser = createParser(state)
        assertEquals(ResolveMultiMode.FIRST, state.resolveMulti)
        parser.handle("###set resolveMulti = error")
        assertEquals(ResolveMultiMode.ERROR, state.resolveMulti)
    }

    @Test
    fun testIfConditionEquals() {
        val state = DirectiveState()
        val parser = createParser(state, Settings().apply { builtInConfig = "dev" })
        assertTrue(state.isActive())
        parser.handle("###if builtInConfig == \"dev\"")
        assertTrue(state.isActive())
        parser.handle("###endif")
        assertTrue(state.isActive())
    }

    @Test
    fun testIfConditionNotEquals() {
        val state = DirectiveState()
        val parser = createParser(state, Settings().apply { builtInConfig = "prod" })
        assertTrue(state.isActive())
        parser.handle("###if builtInConfig != \"dev\"")
        assertTrue(state.isActive())
        parser.handle("###if builtInConfig == \"dev\"")
        assertFalse(state.isActive())
    }

    @Test
    fun testIfConditionWithSingleEquals() {
        val state = DirectiveState()
        val parser = createParser(state, Settings().apply { builtInConfig = "test" })
        parser.handle("###if builtInConfig = \"test\"")
        assertTrue(state.isActive())
    }

    @Test
    fun testIfConditionFalse() {
        val state = DirectiveState()
        val parser = createParser(state, Settings().apply { builtInConfig = "prod" })
        parser.handle("###if builtInConfig == \"dev\"")
        assertFalse(state.isActive())
    }

    @Test
    fun testNestedConditions() {
        val state = DirectiveState()
        val parser = createParser(state, Settings().apply { 
            builtInConfig = "1"
            remoteConfig = arrayOf("2")
        })
        
        assertTrue(state.isActive())
        
        parser.handle("###if builtInConfig == \"1\"")
        assertTrue(state.isActive())
        
        parser.handle("###if remoteConfig == \"2\"")
        assertTrue(state.isActive())
        
        parser.handle("###endif")
        assertTrue(state.isActive())
        
        parser.handle("###endif")
        assertTrue(state.isActive())
    }

    @Test
    fun testNestedConditionsWithFalseInner() {
        val state = DirectiveState()
        val parser = createParser(state, Settings().apply { 
            builtInConfig = "1"
            remoteConfig = arrayOf("3")
        })
        
        parser.handle("###if builtInConfig == \"1\"")
        assertTrue(state.isActive())
        
        parser.handle("###if remoteConfig == \"2\"")
        assertFalse(state.isActive())
        
        parser.handle("###endif")
        assertTrue(state.isActive())
        
        parser.handle("###endif")
        assertTrue(state.isActive())
    }

    @Test
    fun testConditionWithMissingSetting() {
        val state = DirectiveState()
        val parser = createParser(state, null)
        
        parser.handle("###if undefined == \"value\"")
        assertFalse(state.isActive())
        
        parser.handle("###endif")
        assertTrue(state.isActive())
    }

    @Test
    fun testConditionWithQuotes() {
        val state = DirectiveState()
        val parser = createParser(state, Settings().apply { builtInConfig = "MyAPI" })
        
        parser.handle("###if builtInConfig == 'MyAPI'")
        assertTrue(state.isActive())
        
        parser.handle("###endif")
        
        parser.handle("###if builtInConfig == \"MyAPI\"")
        assertTrue(state.isActive())
    }

    @Test
    fun testHandleReturnsTrueForDirectives() {
        val state = DirectiveState()
        val parser = createParser(state, Settings().apply { builtInConfig = "dev" })
        
        assertTrue(parser.handle("###set resolveProperty = false"))
        assertTrue(parser.handle("###if builtInConfig == \"dev\""))
        assertTrue(parser.handle("###endif"))
    }
}

class DirectiveStateTest {

    @Test
    fun testInitialState() {
        val state = DirectiveState()
        assertTrue(state.resolveProperty)
        assertEquals(ResolveMultiMode.FIRST, state.resolveMulti)
        assertFalse(state.ignoreNotFoundFile)
        assertFalse(state.ignoreUnresolved)
        assertTrue(state.isActive())
    }

    @Test
    fun testPushPopCondition() {
        val state = DirectiveState()
        
        assertTrue(state.isActive())
        
        state.pushCondition(true)
        assertTrue(state.isActive())
        
        state.pushCondition(false)
        assertFalse(state.isActive())
        
        state.popCondition()
        assertTrue(state.isActive())
        
        state.popCondition()
        assertTrue(state.isActive())
    }

    @Test
    fun testMultipleTrueConditions() {
        val state = DirectiveState()
        
        state.pushCondition(true)
        state.pushCondition(true)
        state.pushCondition(true)
        assertTrue(state.isActive())
        
        state.popCondition()
        state.popCondition()
        state.popCondition()
        assertTrue(state.isActive())
    }

    @Test
    fun testMixedConditions() {
        val state = DirectiveState()
        
        state.pushCondition(true)
        state.pushCondition(true)
        state.pushCondition(false)
        assertFalse(state.isActive())
        
        state.popCondition()
        assertTrue(state.isActive())
    }

    @Test
    fun testPopEmptyStack() {
        val state = DirectiveState()
        state.popCondition()
        assertTrue(state.isActive())
    }
}
