package com.itangcent.easyapi.rule.context

import org.junit.Assert.*
import org.junit.Test

class ScriptFieldContextTest {

    @Test
    fun testPathReturnsFieldPath() {
        val context = ScriptFieldContext("user.name")
        assertEquals("user.name", context.path())
    }

    @Test
    fun testPathTopLevel() {
        val context = ScriptFieldContext("name")
        assertEquals("name", context.path())
    }

    @Test
    fun testPropertyWithParent() {
        val context = ScriptFieldContext("user.name")
        assertEquals("user.age", context.property("age"))
    }

    @Test
    fun testPropertyTopLevel() {
        val context = ScriptFieldContext("name")
        assertEquals("age", context.property("age"))
    }

    @Test
    fun testPropertyNestedPath() {
        val context = ScriptFieldContext("user.address.city")
        assertEquals("user.address.zip", context.property("zip"))
    }

    @Test
    fun testToStringReturnsPath() {
        val context = ScriptFieldContext("user.name")
        assertEquals("user.name", context.toString())
    }

    @Test
    fun testPropertyWithEmptyPath() {
        val context = ScriptFieldContext("")
        assertEquals("field", context.property("field"))
    }
}
