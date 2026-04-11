package com.itangcent.easyapi.extension

import org.junit.Assert.*
import org.junit.Test

class ExtensionConfigParserTest {

    @Test
    fun testParse_fullConfig() {
        val content = """
            ---
            code: swagger
            description: Swagger 2.x annotation support
            on-class: io.swagger.annotations.Api
            default-enabled: true
            ---
            param.doc=@io.swagger.annotations.ApiParam#value
            param.required=@io.swagger.annotations.ApiParam#required
        """.trimIndent()

        val config = ExtensionConfigParser.parse(content)
        
        assertNotNull(config)
        assertEquals("swagger", config!!.code)
        assertEquals("Swagger 2.x annotation support", config.description)
        assertEquals("io.swagger.annotations.Api", config.onClass)
        assertTrue(config.defaultEnabled)
        assertTrue(config.content.contains("param.doc=@io.swagger.annotations.ApiParam#value"))
        assertTrue(config.content.contains("param.required=@io.swagger.annotations.ApiParam#required"))
    }

    @Test
    fun testParse_minimalConfig() {
        val content = """
            ---
            code: minimal
            description: Minimal extension
            ---
            key=value
        """.trimIndent()

        val config = ExtensionConfigParser.parse(content)
        
        assertNotNull(config)
        assertEquals("minimal", config!!.code)
        assertEquals("Minimal extension", config.description)
        assertNull(config.onClass)
        assertFalse(config.defaultEnabled)
        assertEquals("key=value", config.content)
    }

    @Test
    fun testParse_noYamlFrontMatter() {
        val content = "key=value"
        
        val config = ExtensionConfigParser.parse(content)
        
        assertNull("Should return null when no code is provided", config)
    }

    @Test
    fun testParse_emptyContent() {
        val content = ""
        
        val config = ExtensionConfigParser.parse(content)
        
        assertNull(config)
    }

    @Test
    fun testParse_quotedValues() {
        val content = """
            ---
            code: "my-extension"
            description: "My Extension with spaces"
            on-class: "com.example.MyClass"
            ---
            config=value
        """.trimIndent()

        val config = ExtensionConfigParser.parse(content)
        
        assertNotNull(config)
        assertEquals("my-extension", config!!.code)
        assertEquals("My Extension with spaces", config.description)
        assertEquals("com.example.MyClass", config.onClass)
    }

    @Test
    fun testParse_multilineContent() {
        val content = """
            ---
            code: test
            description: Test extension
            ---
            key1=value1
            key2=value2
            key3=value3
        """.trimIndent()

        val config = ExtensionConfigParser.parse(content)
        
        assertNotNull(config)
        assertTrue(config!!.content.contains("key1=value1"))
        assertTrue(config.content.contains("key2=value2"))
        assertTrue(config.content.contains("key3=value3"))
    }

    @Test
    fun testStripYamlFrontMatter_withYaml() {
        val content = """
            ---
            code: test
            description: Test
            ---
            key=value
            another=setting
        """.trimIndent()

        val stripped = ExtensionConfigParser.stripYamlFrontMatter(content)
        
        assertFalse(stripped.contains("---"))
        assertFalse(stripped.contains("code:"))
        assertFalse(stripped.contains("description:"))
        assertTrue(stripped.contains("key=value"))
        assertTrue(stripped.contains("another=setting"))
    }

    @Test
    fun testStripYamlFrontMatter_withoutYaml() {
        val content = "key=value\nanother=setting"
        
        val stripped = ExtensionConfigParser.stripYamlFrontMatter(content)
        
        assertEquals(content, stripped)
    }

    @Test
    fun testStripYamlFrontMatter_emptyContent() {
        val content = ""
        
        val stripped = ExtensionConfigParser.stripYamlFrontMatter(content)
        
        assertEquals("", stripped)
    }

    @Test
    fun testStripYamlFrontMatter_onlyYaml() {
        val content = """
            ---
            code: test
            description: Test
            ---
        """.trimIndent()

        val stripped = ExtensionConfigParser.stripYamlFrontMatter(content)
        
        assertEquals("", stripped)
    }

    @Test
    fun testParse_defaultEnabledFalse() {
        val content = """
            ---
            code: optional
            description: Optional extension
            default-enabled: false
            ---
            key=value
        """.trimIndent()

        val config = ExtensionConfigParser.parse(content)
        
        assertNotNull(config)
        assertFalse(config!!.defaultEnabled)
    }

    @Test
    fun testParse_noOnClass() {
        val content = """
            ---
            code: simple
            description: Simple extension
            default-enabled: true
            ---
            key=value
        """.trimIndent()

        val config = ExtensionConfigParser.parse(content)
        
        assertNotNull(config)
        assertNull(config!!.onClass)
        assertTrue(config.defaultEnabled)
    }
}
