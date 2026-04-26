package com.itangcent.easyapi.dashboard.env

import org.junit.Assert.*
import org.junit.Test

class EnvironmentTest {

    @Test
    fun testConstructionWithDefaults() {
        val env = Environment(name = "dev")
        assertEquals("dev", env.name)
        assertEquals(EnvironmentScope.PROJECT, env.scope)
        assertTrue(env.variables.isEmpty())
    }

    @Test
    fun testConstructionWithAllFields() {
        val env = Environment(
            name = "staging",
            scope = EnvironmentScope.GLOBAL,
            variables = mapOf("base_url" to "https://staging.api.com", "token" to "abc123")
        )
        assertEquals("staging", env.name)
        assertEquals(EnvironmentScope.GLOBAL, env.scope)
        assertEquals(2, env.variables.size)
        assertEquals("https://staging.api.com", env.variables["base_url"])
        assertEquals("abc123", env.variables["token"])
    }

    @Test
    fun testCopy() {
        val env = Environment(name = "dev", variables = mapOf("key" to "value"))
        val copy = env.copy(name = "prod")
        assertEquals("prod", copy.name)
        assertEquals("value", copy.variables["key"])
        assertEquals("dev", env.name)
    }

    @Test
    fun testEquality() {
        val env1 = Environment("test", EnvironmentScope.PROJECT, mapOf("a" to "1"))
        val env2 = Environment("test", EnvironmentScope.PROJECT, mapOf("a" to "1"))
        assertEquals(env1, env2)
    }

    @Test
    fun testInequalityDifferentScope() {
        val env1 = Environment("test", EnvironmentScope.PROJECT)
        val env2 = Environment("test", EnvironmentScope.GLOBAL)
        assertNotEquals(env1, env2)
    }
}

class EnvironmentScopeTest {

    @Test
    fun testLabel() {
        assertEquals("Global", EnvironmentScope.GLOBAL.label())
        assertEquals("Project", EnvironmentScope.PROJECT.label())
    }

    @Test
    fun testEnumValues() {
        val values = EnvironmentScope.values()
        assertEquals(2, values.size)
        assertTrue(values.contains(EnvironmentScope.GLOBAL))
        assertTrue(values.contains(EnvironmentScope.PROJECT))
    }
}

class EnvironmentDataTest {

    @Test
    fun testDefaultConstruction() {
        val data = EnvironmentData()
        assertTrue(data.environments.isEmpty())
        assertNull(data.activeEnvironmentName)
        assertNull(data.activeEnvironment())
        assertNull(data.resolveVariable("any"))
        assertTrue(data.resolveAllVariables().isEmpty())
    }

    @Test
    fun testActiveEnvironment() {
        val dev = Environment("dev", variables = mapOf("base_url" to "http://dev.api.com"))
        val prod = Environment("prod", variables = mapOf("base_url" to "http://prod.api.com"))
        val data = EnvironmentData(
            environments = listOf(dev, prod),
            activeEnvironmentName = "dev"
        )
        assertEquals(dev, data.activeEnvironment())
    }

    @Test
    fun testActiveEnvironmentNotFound() {
        val data = EnvironmentData(
            environments = listOf(Environment("dev")),
            activeEnvironmentName = "staging"
        )
        assertNull(data.activeEnvironment())
    }

    @Test
    fun testActiveEnvironmentNull() {
        val data = EnvironmentData(
            environments = listOf(Environment("dev")),
            activeEnvironmentName = null
        )
        assertNull(data.activeEnvironment())
    }

    @Test
    fun testResolveVariable() {
        val data = EnvironmentData(
            environments = listOf(Environment("dev", variables = mapOf("base_url" to "http://dev.api.com"))),
            activeEnvironmentName = "dev"
        )
        assertEquals("http://dev.api.com", data.resolveVariable("base_url"))
        assertNull(data.resolveVariable("nonexistent"))
    }

    @Test
    fun testResolveVariableNoActiveEnvironment() {
        val data = EnvironmentData(
            environments = listOf(Environment("dev", variables = mapOf("key" to "value"))),
            activeEnvironmentName = null
        )
        assertNull(data.resolveVariable("key"))
    }

    @Test
    fun testResolveAllVariables() {
        val data = EnvironmentData(
            environments = listOf(Environment("dev", variables = mapOf("a" to "1", "b" to "2"))),
            activeEnvironmentName = "dev"
        )
        val vars = data.resolveAllVariables()
        assertEquals(2, vars.size)
        assertEquals("1", vars["a"])
        assertEquals("2", vars["b"])
    }

    @Test
    fun testResolveAllVariablesNoActiveEnvironment() {
        val data = EnvironmentData(
            environments = listOf(Environment("dev")),
            activeEnvironmentName = null
        )
        assertTrue(data.resolveAllVariables().isEmpty())
    }
}
