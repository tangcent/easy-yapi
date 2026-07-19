package com.itangcent.easyapi.core.script

import org.junit.Assert.*
import org.junit.Test

class ScriptScopeTest {

    @Test
    fun testModuleKey() {
        val scope = ScriptScope.Module("user-service")
        assertEquals("module:user-service", scope.key)
    }

    @Test
    fun testModuleDisplayLabel() {
        val scope = ScriptScope.Module("user-service")
        assertEquals("Module:user-service", scope.displayLabel())
    }

    @Test
    fun testClassKey() {
        val scope = ScriptScope.Class("com.itangcent.api.UserCtrl")
        assertEquals("class:com.itangcent.api.UserCtrl", scope.key)
    }

    @Test
    fun testClassDisplayLabel() {
        val scope = ScriptScope.Class("com.itangcent.api.UserCtrl")
        assertEquals("Class:UserCtrl", scope.displayLabel())
    }

    @Test
    fun testEndpointKey() {
        val scope = ScriptScope.Endpoint("com.itangcent.api.UserCtrl#getUser")
        assertEquals("endpoint:com.itangcent.api.UserCtrl#getUser", scope.key)
    }

    @Test
    fun testEndpointDisplayLabel() {
        val scope = ScriptScope.Endpoint("com.itangcent.api.UserCtrl#getUser")
        assertEquals("Endpoint:getUser", scope.displayLabel())
    }

    @Test
    fun testModuleEquality() {
        val s1 = ScriptScope.Module("svc")
        val s2 = ScriptScope.Module("svc")
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testModuleInequality() {
        val s1 = ScriptScope.Module("svc1")
        val s2 = ScriptScope.Module("svc2")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testClassEquality() {
        val s1 = ScriptScope.Class("com.example.Foo")
        val s2 = ScriptScope.Class("com.example.Foo")
        assertEquals(s1, s2)
    }

    @Test
    fun testEndpointEquality() {
        val s1 = ScriptScope.Endpoint("com.example.Foo#bar")
        val s2 = ScriptScope.Endpoint("com.example.Foo#bar")
        assertEquals(s1, s2)
    }

    @Test
    fun testDifferentScopeTypesNotEqual() {
        val module = ScriptScope.Module("key")
        val cls = ScriptScope.Class("key")
        val endpoint = ScriptScope.Endpoint("key")
        assertNotEquals(module, cls)
        assertNotEquals(module, endpoint)
        assertNotEquals(cls, endpoint)
    }

    @Test
    fun testModuleCopy() {
        val s1 = ScriptScope.Module("old")
        val s2 = s1.copy(name = "new")
        assertEquals("module:new", s2.key)
        assertEquals("Module:new", s2.displayLabel())
    }

    @Test
    fun testClassCopy() {
        val s1 = ScriptScope.Class("com.example.Old")
        val s2 = s1.copy(qualifiedName = "com.example.New")
        assertEquals("Class:New", s2.displayLabel())
    }
}

class ScriptCacheTest {

    @Test
    fun testDefaultConstruction() {
        val cache = ScriptCache()
        assertNull(cache.preRequestScript)
        assertNull(cache.postResponseScript)
    }

    @Test
    fun testConstructionWithScripts() {
        val cache = ScriptCache(
            preRequestScript = "pm.environment.set('token', 'abc')",
            postResponseScript = "pm.test('ok') { pm.expect(200).to.equal(200) }"
        )
        assertEquals("pm.environment.set('token', 'abc')", cache.preRequestScript)
        assertEquals("pm.test('ok') { pm.expect(200).to.equal(200) }", cache.postResponseScript)
    }

    @Test
    fun testCopy() {
        val cache = ScriptCache(preRequestScript = "old")
        val copy = cache.copy(preRequestScript = "new")
        assertEquals("new", copy.preRequestScript)
        assertEquals("old", cache.preRequestScript)
    }

    @Test
    fun testEquality() {
        val c1 = ScriptCache("pre", "post")
        val c2 = ScriptCache("pre", "post")
        assertEquals(c1, c2)
    }

    @Test
    fun testInequality() {
        val c1 = ScriptCache("pre1", "post")
        val c2 = ScriptCache("pre2", "post")
        assertNotEquals(c1, c2)
    }
}

class ResolvedScriptsTest {

    @Test
    fun testDefaultConstruction() {
        val scripts = ResolvedScripts()
        assertNull(scripts.preRequestScript)
        assertNull(scripts.postResponseScript)
    }

    @Test
    fun testConstructionWithScripts() {
        val scripts = ResolvedScripts(
            preRequestScript = "pm.request.headers.add('Auth', 'token')",
            postResponseScript = "pm.test('status') { pm.expect(200).to.equal(200) }"
        )
        assertEquals("pm.request.headers.add('Auth', 'token')", scripts.preRequestScript)
        assertEquals("pm.test('status') { pm.expect(200).to.equal(200) }", scripts.postResponseScript)
    }

    @Test
    fun testEquality() {
        val s1 = ResolvedScripts("pre", "post")
        val s2 = ResolvedScripts("pre", "post")
        assertEquals(s1, s2)
    }
}
