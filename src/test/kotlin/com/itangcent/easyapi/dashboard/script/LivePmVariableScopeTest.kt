package com.itangcent.easyapi.dashboard.script

import org.junit.Assert.*
import org.junit.Test

class LivePmVariableScopeTest {

    @Test
    fun testSetPersistsViaCallback() {
        val persistedVars = mutableMapOf("base_url" to "http://localhost")
        val scope = LivePmVariableScope(
            variables = persistedVars.toMap(),
            onSet = { name, value -> persistedVars[name] = value },
            onUnset = { name -> persistedVars.remove(name) }
        )

        scope.set("token", "abc123")

        assertEquals("abc123", scope.get("token"))
        assertEquals("abc123", persistedVars["token"])
        assertEquals("http://localhost", persistedVars["base_url"])
    }

    @Test
    fun testUnsetRemovesViaCallback() {
        val persistedVars = mutableMapOf("base_url" to "http://localhost", "temp" to "value")
        val scope = LivePmVariableScope(
            variables = persistedVars.toMap(),
            onSet = { name, value -> persistedVars[name] = value },
            onUnset = { name -> persistedVars.remove(name) }
        )

        scope.unset("temp")

        assertNull(scope.get("temp"))
        assertNull(persistedVars["temp"])
        assertEquals("http://localhost", persistedVars["base_url"])
    }

    @Test
    fun testSetWithNullUnsets() {
        val persistedVars = mutableMapOf("key" to "value")
        val scope = LivePmVariableScope(
            variables = persistedVars.toMap(),
            onSet = { name, value -> persistedVars[name] = value },
            onUnset = { name -> persistedVars.remove(name) }
        )

        scope.set("key", null)

        assertNull(scope.get("key"))
        assertNull(persistedVars["key"])
    }

    @Test
    fun testGetReturnsInitialValues() {
        val persistedVars = mutableMapOf("host" to "https://api.example.com", "port" to "8080")
        val scope = LivePmVariableScope(
            variables = persistedVars.toMap(),
            onSet = { name, value -> persistedVars[name] = value },
            onUnset = { name -> persistedVars.remove(name) }
        )

        assertEquals("https://api.example.com", scope.get("host"))
        assertEquals("8080", scope.get("port"))
        assertNull(scope.get("nonexistent"))
    }

    @Test
    fun testHasChecksInitialValues() {
        val persistedVars = mutableMapOf("host" to "https://api.example.com")
        val scope = LivePmVariableScope(
            variables = persistedVars.toMap(),
            onSet = { name, value -> persistedVars[name] = value },
            onUnset = { name -> persistedVars.remove(name) }
        )

        assertTrue(scope.has("host"))
        assertFalse(scope.has("nonexistent"))
    }

    @Test
    fun testOverwriteExistingVariable() {
        val persistedVars = mutableMapOf("token" to "old")
        val scope = LivePmVariableScope(
            variables = persistedVars.toMap(),
            onSet = { name, value -> persistedVars[name] = value },
            onUnset = { name -> persistedVars.remove(name) }
        )

        scope.set("token", "new")

        assertEquals("new", scope.get("token"))
        assertEquals("new", persistedVars["token"])
    }

    @Test
    fun testMultipleSetsAndUnsets() {
        val persistedVars = mutableMapOf<String, String>()
        val scope = LivePmVariableScope(
            variables = persistedVars.toMap(),
            onSet = { name, value -> persistedVars[name] = value },
            onUnset = { name -> persistedVars.remove(name) }
        )

        scope.set("a", "1")
        scope.set("b", "2")
        scope.set("c", "3")

        assertEquals("1", scope.get("a"))
        assertEquals("2", scope.get("b"))
        assertEquals("3", scope.get("c"))
        assertEquals("1", persistedVars["a"])

        scope.unset("b")

        assertNull(scope.get("b"))
        assertNull(persistedVars["b"])
        assertEquals("1", persistedVars["a"])
    }

    @Test
    fun testToMapReturnsCurrentSnapshot() {
        val persistedVars = mutableMapOf("x" to "10")
        val scope = LivePmVariableScope(
            variables = persistedVars.toMap(),
            onSet = { name, value -> persistedVars[name] = value },
            onUnset = { name -> persistedVars.remove(name) }
        )

        scope.set("y", "20")

        val map = scope.toMap()
        assertEquals("10", map["x"])
        assertEquals("20", map["y"])
    }

    @Test
    fun testCallbackNotCalledForReadOperations() {
        var setCallCount = 0
        var unsetCallCount = 0
        val scope = LivePmVariableScope(
            variables = mapOf("key" to "value"),
            onSet = { _, _ -> setCallCount++ },
            onUnset = { _ -> unsetCallCount++ }
        )

        scope.get("key")
        scope.has("key")
        scope.toObject()
        scope.toMap()

        assertEquals(0, setCallCount)
        assertEquals(0, unsetCallCount)
    }
}
