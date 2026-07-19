package com.itangcent.easyapi.core.rule.context

import org.junit.Assert.*
import org.junit.Test

class ScriptFieldPathContextTest {

    @Test
    fun testPathReturnsFieldPath() {
        val context = ScriptFieldPathContext("user.name")
        assertEquals("user.name", context.path())
    }

    @Test
    fun testPathTopLevel() {
        val context = ScriptFieldPathContext("name")
        assertEquals("name", context.path())
    }

    @Test
    fun testPropertyWithParent() {
        val context = ScriptFieldPathContext("user.name")
        assertEquals("user.age", context.property("age"))
    }

    @Test
    fun testPropertyTopLevel() {
        val context = ScriptFieldPathContext("name")
        assertEquals("age", context.property("age"))
    }

    @Test
    fun testPropertyNestedPath() {
        val context = ScriptFieldPathContext("user.address.city")
        assertEquals("user.address.zip", context.property("zip"))
    }

    @Test
    fun testToStringReturnsPath() {
        val context = ScriptFieldPathContext("user.name")
        assertEquals("user.name", context.toString())
    }

    @Test
    fun testPropertyWithEmptyPath() {
        val context = ScriptFieldPathContext("")
        assertEquals("field", context.property("field"))
    }
}
