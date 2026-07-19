package com.itangcent.easyapi.core.ide.script

import org.junit.Assert.*
import org.junit.Test

class ScriptInfoTest {

    @Test
    fun testConstruction() {
        val context = SimpleScriptContext("test")
        val scriptInfo = ScriptInfo(
            script = "println('hello')",
            scriptType = null,
            context = context
        )
        
        assertEquals("println('hello')", scriptInfo.script)
        assertNull(scriptInfo.scriptType)
        assertEquals(context, scriptInfo.context)
    }

    @Test
    fun testScriptUpdateTime() {
        val before = System.currentTimeMillis()
        val scriptInfo = ScriptInfo(
            script = "test",
            scriptType = null,
            context = null
        )
        val after = System.currentTimeMillis()
        
        assertTrue(scriptInfo.scriptUpdateTime >= before)
        assertTrue(scriptInfo.scriptUpdateTime <= after)
    }

    @Test
    fun testCopy() {
        val scriptInfo = ScriptInfo(
            script = "test",
            scriptType = null,
            context = null
        )
        
        val copy = scriptInfo.copy(script = "updated")
        assertEquals("updated", copy.script)
    }

    @Test
    fun testEquality() {
        val scriptInfo1 = ScriptInfo(
            script = "test",
            scriptType = null,
            context = "context"
        )
        val scriptInfo2 = ScriptInfo(
            script = "test",
            scriptType = null,
            context = "context"
        )
        
        assertEquals(scriptInfo1, scriptInfo2)
    }
}

class SimpleScriptContextTest {

    @Test
    fun testElement() {
        val target = "test element"
        val context = SimpleScriptContext(target)
        
        assertEquals(target, context.element())
    }

    @Test
    fun testNameWithDisplayName() {
        val context = SimpleScriptContext("test", "My Context")
        assertEquals("My Context", context.name())
    }

    @Test
    fun testNameWithoutDisplayName() {
        val context = SimpleScriptContext("test element")
        assertEquals("test element", context.name())
    }

    @Test
    fun testToString() {
        val context = SimpleScriptContext("test", "My Context")
        assertEquals("My Context", context.toString())
    }

    @Test
    fun testEquality() {
        val target = "test"
        val context1 = SimpleScriptContext(target)
        val context2 = SimpleScriptContext(target)
        
        assertEquals(context1, context2)
    }

    @Test
    fun testInequality() {
        val context1 = SimpleScriptContext("test1")
        val context2 = SimpleScriptContext("test2")
        
        assertNotEquals(context1, context2)
    }

    @Test
    fun testHashCode() {
        val target = "test"
        val context1 = SimpleScriptContext(target)
        val context2 = SimpleScriptContext(target)
        
        assertEquals(context1.hashCode(), context2.hashCode())
    }
}

class EmptyScriptContextTest {

    @Test
    fun testEmptyScriptContext() {
        assertEquals("<select class>", EMPTY_SCRIPT_CONTEXT.name())
        assertTrue(EMPTY_SCRIPT_CONTEXT.element() is Any)
    }
}

class ScriptInfoExtendedTest {

    @Test
    fun testScriptInfoWithScriptType() {
        val scriptInfo = ScriptInfo(
            script = "println 'hello'",
            scriptType = GroovyScriptSupport,
            context = "test"
        )
        assertEquals("println 'hello'", scriptInfo.script)
        assertEquals(GroovyScriptSupport, scriptInfo.scriptType)
        assertEquals("test", scriptInfo.context)
    }

    @Test
    fun testScriptInfoCopyWithScriptType() {
        val scriptInfo = ScriptInfo(
            script = "test",
            scriptType = GeneralScriptSupport,
            context = null
        )
        val copy = scriptInfo.copy(scriptType = GroovyScriptSupport)
        assertEquals(GroovyScriptSupport, copy.scriptType)
        assertEquals(GeneralScriptSupport, scriptInfo.scriptType) // original unchanged
    }

    @Test
    fun testScriptInfoUpdateTimeChangesOnCopy() {
        val original = ScriptInfo(script = "test", scriptType = null, context = null)
        Thread.sleep(2) // ensure time difference
        val copy = original.copy(scriptUpdateTime = System.currentTimeMillis())
        assertTrue(copy.scriptUpdateTime >= original.scriptUpdateTime)
    }

    @Test
    fun testScriptInfoEqualityWithDifferentScriptType() {
        val info1 = ScriptInfo(script = "test", scriptType = GeneralScriptSupport, context = null)
        val info2 = ScriptInfo(script = "test", scriptType = GroovyScriptSupport, context = null)
        assertNotEquals(info1, info2)
    }

    @Test
    fun testScriptInfoEqualityWithSameScriptType() {
        val info1 = ScriptInfo(script = "test", scriptType = GeneralScriptSupport, context = "ctx")
        val info2 = ScriptInfo(script = "test", scriptType = GeneralScriptSupport, context = "ctx")
        assertEquals(info1, info2)
    }
}

class SimpleScriptContextExtendedTest {

    @Test
    fun testNameWithIntegerTarget() {
        val context = SimpleScriptContext(42)
        assertEquals("42", context.name())
    }

    @Test
    fun testNameWithNullDisplayName() {
        val context = SimpleScriptContext("target", null)
        assertEquals("target", context.name())
    }

    @Test
    fun testEqualsWithNull() {
        val context = SimpleScriptContext("test")
        assertNotEquals(context, null)
    }

    @Test
    fun testEqualsWithDifferentType() {
        val context = SimpleScriptContext("test")
        assertNotEquals(context, "test")
    }

    @Test
    fun testEqualsWithSameTarget() {
        val target = "shared"
        val context1 = SimpleScriptContext(target)
        val context2 = SimpleScriptContext(target)
        assertEquals(context1, context2)
    }
}
