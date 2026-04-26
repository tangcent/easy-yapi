package com.itangcent.easyapi.dashboard.script

import org.junit.Assert.*
import org.junit.Test

class PmVariableScopeTest {

    @Test
    fun testDefaultConstruction() {
        val scope = PmVariableScope()
        assertFalse(scope.has("key"))
        assertNull(scope.get("key"))
        assertTrue(scope.toObject().isEmpty())
    }

    @Test
    fun testConstructionWithInitialVariables() {
        val scope = PmVariableScope(mutableMapOf("base_url" to "http://api.com", "token" to "abc"))
        assertTrue(scope.has("base_url"))
        assertEquals("http://api.com", scope.get("base_url"))
        assertEquals("abc", scope.get("token"))
    }

    @Test
    fun testSetAndGet() {
        val scope = PmVariableScope()
        scope.set("key", "value")
        assertTrue(scope.has("key"))
        assertEquals("value", scope.get("key"))
    }

    @Test
    fun testSetWithNullRemovesVariable() {
        val scope = PmVariableScope()
        scope.set("key", "value")
        scope.set("key", null)
        assertFalse(scope.has("key"))
        assertNull(scope.get("key"))
    }

    @Test
    fun testSetConvertsToString() {
        val scope = PmVariableScope()
        scope.set("count", 42)
        assertEquals("42", scope.get("count"))
        scope.set("flag", true)
        assertEquals("true", scope.get("flag"))
    }

    @Test
    fun testUnset() {
        val scope = PmVariableScope()
        scope.set("key", "value")
        scope.unset("key")
        assertFalse(scope.has("key"))
        assertNull(scope.get("key"))
    }

    @Test
    fun testUnsetNonexistentKey() {
        val scope = PmVariableScope()
        scope.unset("nonexistent")
        assertFalse(scope.has("nonexistent"))
    }

    @Test
    fun testClear() {
        val scope = PmVariableScope(mutableMapOf("a" to "1", "b" to "2"))
        scope.clear()
        assertFalse(scope.has("a"))
        assertFalse(scope.has("b"))
        assertTrue(scope.toObject().isEmpty())
    }

    @Test
    fun testToObject() {
        val scope = PmVariableScope()
        scope.set("a", "1")
        scope.set("b", "2")
        val obj = scope.toObject()
        assertEquals(2, obj.size)
        assertEquals("1", obj["a"])
        assertEquals("2", obj["b"])
    }

    @Test
    fun testToObjectIsImmutableSnapshot() {
        val scope = PmVariableScope()
        scope.set("key", "original")
        val snapshot = scope.toObject()
        scope.set("key", "modified")
        assertEquals("original", snapshot["key"])
        assertEquals("modified", scope.get("key"))
    }

    @Test
    fun testReplaceInWithTimestamp() {
        val scope = PmVariableScope()
        val input = "time={{${'$'}timestamp}}"
        val result = scope.replaceIn(input)
        assertFalse(result.contains("{{${'$'}timestamp}}"))
        assertTrue(result.startsWith("time="))
        assertTrue(result.length > "time=".length)
    }

    @Test
    fun testReplaceInWithGuid() {
        val scope = PmVariableScope()
        val input = "id={{${'$'}guid}}"
        val result = scope.replaceIn(input)
        assertFalse(result.contains("{{${'$'}guid}}"))
        assertTrue(result.matches(Regex("id=[0-9a-f-]{36}")))
    }

    @Test
    fun testReplaceInWithUnknownVariable() {
        val scope = PmVariableScope()
        val input = "value={{${'$'}unknownVar}}"
        val result = scope.replaceIn(input)
        assertEquals(input, result)
    }

    @Test
    fun testReplaceInWithNoPlaceholders() {
        val scope = PmVariableScope()
        val result = scope.replaceIn("plain text")
        assertEquals("plain text", result)
    }

    @Test
    fun testReplaceInWithMultiplePlaceholders() {
        val scope = PmVariableScope()
        val result = scope.replaceIn("id={{${'$'}guid}}&ts={{${'$'}timestamp}}")
        assertFalse(result.contains("{{${'$'}"))
        assertTrue(result.contains("id="))
        assertTrue(result.contains("&ts="))
    }

    @Test
    fun testPutAll() {
        val scope = PmVariableScope()
        scope.putAll(mapOf("a" to "1", "b" to "2"))
        assertEquals("1", scope.get("a"))
        assertEquals("2", scope.get("b"))
    }

    @Test
    fun testToMap() {
        val scope = PmVariableScope()
        scope.set("key", "value")
        val map = scope.toMap()
        assertEquals("value", map["key"])
    }
}

class CompositeVariableScopeTest {

    private fun createScopes(): Tuple4<PmVariableScope, PmVariableScope, PmVariableScope, PmVariableScope> {
        val local = PmVariableScope()
        val environment = PmVariableScope()
        val collection = PmVariableScope()
        val globals = PmVariableScope()
        return Tuple4(local, environment, collection, globals)
    }

    @Test
    fun testGetResolvesFromLocalFirst() {
        val (local, env, coll, global) = createScopes()
        local.set("key", "local-val")
        env.set("key", "env-val")
        coll.set("key", "coll-val")
        global.set("key", "global-val")

        val composite = CompositeVariableScope(local, env, coll, global)
        assertEquals("local-val", composite.get("key"))
    }

    @Test
    fun testGetResolvesFromEnvironmentWhenNotInLocal() {
        val (local, env, coll, global) = createScopes()
        env.set("key", "env-val")
        coll.set("key", "coll-val")
        global.set("key", "global-val")

        val composite = CompositeVariableScope(local, env, coll, global)
        assertEquals("env-val", composite.get("key"))
    }

    @Test
    fun testGetResolvesFromCollectionWhenNotInLocalOrEnv() {
        val (local, env, coll, global) = createScopes()
        coll.set("key", "coll-val")
        global.set("key", "global-val")

        val composite = CompositeVariableScope(local, env, coll, global)
        assertEquals("coll-val", composite.get("key"))
    }

    @Test
    fun testGetResolvesFromGlobalsWhenNotInOtherScopes() {
        val (local, env, coll, global) = createScopes()
        global.set("key", "global-val")

        val composite = CompositeVariableScope(local, env, coll, global)
        assertEquals("global-val", composite.get("key"))
    }

    @Test
    fun testGetReturnsNullWhenNotInAnyScope() {
        val (local, env, coll, global) = createScopes()
        val composite = CompositeVariableScope(local, env, coll, global)
        assertNull(composite.get("nonexistent"))
    }

    @Test
    fun testHasChecksAllScopes() {
        val (local, env, coll, global) = createScopes()
        val composite = CompositeVariableScope(local, env, coll, global)

        assertFalse(composite.has("key"))

        global.set("key", "val")
        assertTrue(composite.has("key"))
    }

    @Test
    fun testSetWritesToLocalScope() {
        val (local, env, coll, global) = createScopes()
        val composite = CompositeVariableScope(local, env, coll, global)

        composite.set("key", "composite-set")
        assertEquals("composite-set", local.get("key"))
        assertNull(env.get("key"))
        assertNull(coll.get("key"))
        assertNull(global.get("key"))
    }

    @Test
    fun testUnsetRemovesFromLocalScopeOnly() {
        val (local, env, coll, global) = createScopes()
        local.set("key", "local-val")
        env.set("key", "env-val")

        val composite = CompositeVariableScope(local, env, coll, global)
        composite.unset("key")

        assertNull(local.get("key"))
        assertEquals("env-val", env.get("key"))
        assertEquals("env-val", composite.get("key"))
    }

    @Test
    fun testToObjectMergesAllScopesWithCorrectPrecedence() {
        val (local, env, coll, global) = createScopes()
        global.set("a", "global-a")
        global.set("b", "global-b")
        coll.set("b", "coll-b")
        coll.set("c", "coll-c")
        env.set("c", "env-c")
        local.set("d", "local-d")

        val composite = CompositeVariableScope(local, env, coll, global)
        val obj = composite.toObject()

        assertEquals("global-a", obj["a"])
        assertEquals("coll-b", obj["b"])
        assertEquals("env-c", obj["c"])
        assertEquals("local-d", obj["d"])
    }

    @Test
    fun testSetWithNullRemovesFromLocalScope() {
        val (local, env, coll, global) = createScopes()
        local.set("key", "local-val")
        env.set("key", "env-val")

        val composite = CompositeVariableScope(local, env, coll, global)
        assertEquals("local-val", composite.get("key"))

        composite.set("key", null)
        assertNull(local.get("key"))
        assertEquals("env-val", composite.get("key"))
    }

    @Test
    fun testDifferentKeysResolvedFromDifferentScopes() {
        val (local, env, coll, global) = createScopes()
        global.set("host", "global-host")
        env.set("token", "env-token")
        local.set("temp", "local-temp")

        val composite = CompositeVariableScope(local, env, coll, global)
        assertEquals("global-host", composite.get("host"))
        assertEquals("env-token", composite.get("token"))
        assertEquals("local-temp", composite.get("temp"))
    }
}

private data class Tuple4<A, B, C, D>(
    val first: A, val second: B, val third: C, val fourth: D)

private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component1() = first
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component2() = second
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component3() = third
private operator fun <A, B, C, D> Tuple4<A, B, C, D>.component4() = fourth
