package com.itangcent.easyapi.ide.script

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ScriptSupport implementations and ScriptContext data classes.
 */
class ScriptSupportTest {

    // ==================== GeneralScriptSupport tests ====================

    @Test
    fun `GeneralScriptSupport buildScript passes through`() {
        assertEquals("my-script", GeneralScriptSupport.buildScript("my-script"))
    }

    @Test
    fun `GeneralScriptSupport buildProperty passes through`() {
        assertEquals("my-property", GeneralScriptSupport.buildProperty("my-property"))
    }

    @Test
    fun `GeneralScriptSupport checkSupport always returns true`() {
        assertTrue(GeneralScriptSupport.checkSupport())
    }

    @Test
    fun `GeneralScriptSupport suffix is txt`() {
        assertEquals("txt", GeneralScriptSupport.suffix())
    }

    @Test
    fun `GeneralScriptSupport demoCode returns annotation`() {
        assertEquals("@org.springframework.web.bind.annotation.RequestMapping", GeneralScriptSupport.demoCode())
    }

    @Test
    fun `GeneralScriptSupport toString returns General`() {
        assertEquals("General", GeneralScriptSupport.toString())
    }

    @Test
    fun `GeneralScriptSupport equals is identity`() {
        assertEquals(GeneralScriptSupport, GeneralScriptSupport)
        assertFalse(GeneralScriptSupport.equals("General"))
    }

    // ==================== GroovyScriptSupport tests ====================

    @Test
    fun `GroovyScriptSupport suffix is groovy`() {
        assertEquals("groovy", GroovyScriptSupport.suffix())
    }

    @Test
    fun `GroovyScriptSupport scriptType is groovy`() {
        assertEquals("groovy", GroovyScriptSupport.scriptType())
    }

    @Test
    fun `GroovyScriptSupport toString returns Groovy`() {
        assertEquals("Groovy", GroovyScriptSupport.toString())
    }

    @Test
    fun `GroovyScriptSupport equals is identity`() {
        assertEquals(GroovyScriptSupport, GroovyScriptSupport)
        assertFalse(GroovyScriptSupport.equals("Groovy"))
    }

    @Test
    fun `GroovyScriptSupport demoCode contains tool`() {
        assertTrue(GroovyScriptSupport.demoCode().contains("tool"))
    }

    @Test
    fun `GroovyScriptSupport buildScript adds prefix`() {
        val result = GroovyScriptSupport.buildScript("println 'hello'")
        assertEquals("groovy:println 'hello'", result)
    }

    @Test
    fun `GroovyScriptSupport buildProperty wraps in code block`() {
        val result = GroovyScriptSupport.buildProperty("some.property")
        assertEquals("groovy:```\nsome.property\n```", result)
    }

    // ==================== AbstractScriptSupport via GroovyScriptSupport ====================

    @Test
    fun `AbstractScriptSupport prefix defaults to scriptType`() {
        assertEquals("groovy", GroovyScriptSupport.prefix())
    }

    // ==================== scriptSupports list tests ====================

    @Test
    fun `scriptSupports contains General and Groovy`() {
        assertEquals(2, scriptSupports.size)
        assertTrue(scriptSupports.contains(GeneralScriptSupport))
        assertTrue(scriptSupports.contains(GroovyScriptSupport))
    }

    // ==================== SimpleScriptContext tests ====================

    @Test
    fun `SimpleScriptContext element returns target`() {
        val target = "my-target"
        val context = SimpleScriptContext(target)
        assertEquals(target, context.element())
    }

    @Test
    fun `SimpleScriptContext name uses displayName when provided`() {
        val context = SimpleScriptContext("target", "My Display Name")
        assertEquals("My Display Name", context.name())
    }

    @Test
    fun `SimpleScriptContext name uses toString when no displayName`() {
        val context = SimpleScriptContext(42)
        assertEquals("42", context.name())
    }

    @Test
    fun `SimpleScriptContext toString returns name`() {
        val context = SimpleScriptContext("target", "Display")
        assertEquals("Display", context.toString())
    }

    @Test
    fun `SimpleScriptContext equals based on target`() {
        val c1 = SimpleScriptContext("same", "Name1")
        val c2 = SimpleScriptContext("same", "Name2")
        assertEquals(c1, c2)
    }

    @Test
    fun `SimpleScriptContext not equals different target`() {
        val c1 = SimpleScriptContext("a")
        val c2 = SimpleScriptContext("b")
        assertNotEquals(c1, c2)
    }

    @Test
    fun `SimpleScriptContext hashCode based on target`() {
        val c1 = SimpleScriptContext("same", "Name1")
        val c2 = SimpleScriptContext("same", "Name2")
        assertEquals(c1.hashCode(), c2.hashCode())
    }

    // ==================== EMPTY_SCRIPT_CONTEXT tests ====================

    @Test
    fun `EMPTY_SCRIPT_CONTEXT has select class name`() {
        assertEquals("<select class>", EMPTY_SCRIPT_CONTEXT.name())
    }

    // ==================== ScriptInfo tests ====================

    @Test
    fun `ScriptInfo default values`() {
        val info = ScriptInfo(script = "test", scriptType = null, context = null)
        assertEquals("test", info.script)
        assertNull(info.scriptType)
        assertNull(info.context)
        assertTrue(info.scriptUpdateTime > 0)
    }

    @Test
    fun `ScriptInfo with all fields`() {
        val info = ScriptInfo(
            script = "println 'hello'",
            scriptType = GroovyScriptSupport,
            context = "MyClass",
            scriptUpdateTime = 123456789L
        )
        assertEquals("println 'hello'", info.script)
        assertEquals(GroovyScriptSupport, info.scriptType)
        assertEquals("MyClass", info.context)
        assertEquals(123456789L, info.scriptUpdateTime)
    }

    @Test
    fun `ScriptInfo copy`() {
        val info = ScriptInfo(script = "test", scriptType = null, context = null)
        val copy = info.copy(script = "updated")
        assertEquals("updated", copy.script)
        assertEquals(info.scriptType, copy.scriptType)
    }

    @Test
    fun `ScriptInfo var scriptUpdateTime can be modified`() {
        val info = ScriptInfo(script = "test", scriptType = null, context = null, scriptUpdateTime = 100L)
        info.scriptUpdateTime = 200L
        assertEquals(200L, info.scriptUpdateTime)
    }
}
