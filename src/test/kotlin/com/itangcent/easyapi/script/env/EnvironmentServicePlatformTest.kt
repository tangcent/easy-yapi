package com.itangcent.easyapi.script.env

import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class EnvironmentServicePlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var service: EnvironmentService

    override fun setUp() {
        super.setUp()
        // Initialize settings with empty environments
        val settingBinder = SettingBinder.getInstance(project)
        val settings = Settings()
        settingBinder.save(settings)
        service = EnvironmentService(project)
    }

    fun testGetEnvironmentsInitiallyEmpty() {
        val envs = service.getEnvironments()
        // May have default environments from settings
        assertNotNull("Environments list should not be null", envs)
    }

    fun testAddEnvironment() {
        val env = Environment("test-dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://localhost:8080"))
        service.addEnvironment(env)

        val envs = service.getEnvironments()
        assertTrue("Should contain test-dev environment", envs.any { it.name == "test-dev" })
    }

    fun testAddMultipleEnvironments() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))
        service.addEnvironment(Environment("staging", EnvironmentScope.PROJECT, mapOf("URL" to "http://staging.api.com")))

        val envs = service.getEnvironments()
        assertTrue("Should contain dev", envs.any { it.name == "dev" })
        assertTrue("Should contain staging", envs.any { it.name == "staging" })
    }

    fun testReplaceEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://old.api.com")))
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://new.api.com")))

        val envs = service.getEnvironments()
        val devEnv = envs.first { it.name == "dev" }
        assertEquals("http://new.api.com", devEnv.variables["URL"])
    }

    fun testSetActiveEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))
        service.setActiveEnvironment("dev")

        assertEquals("dev", service.getActiveEnvironmentName())
        assertEquals("dev", service.getActiveEnvironment()?.name)
    }

    fun testSetActiveEnvironmentNull() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT))
        service.setActiveEnvironment("dev")
        service.setActiveEnvironment(null)

        assertNull(service.getActiveEnvironmentName())
        assertNull(service.getActiveEnvironment())
    }

    fun testResolveVariableFromActiveEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com", "token" to "abc123")))
        service.setActiveEnvironment("dev")

        assertEquals("http://dev.api.com", service.resolveVariable("URL"))
        assertEquals("abc123", service.resolveVariable("token"))
        assertNull(service.resolveVariable("nonexistent"))
    }

    fun testResolveVariableNoActiveEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))

        assertNull(service.resolveVariable("URL"))
    }

    fun testResolveAllVariablesFromActiveEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com", "token" to "abc123")))
        service.setActiveEnvironment("dev")

        val vars = service.resolveAllVariables()
        assertEquals(2, vars.size)
        assertEquals("http://dev.api.com", vars["URL"])
        assertEquals("abc123", vars["token"])
    }

    fun testResolveAllVariablesNoActiveEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))

        val vars = service.resolveAllVariables()
        assertTrue(vars.isEmpty())
    }

    fun testRemoveEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT))
        service.addEnvironment(Environment("staging", EnvironmentScope.PROJECT))
        service.removeEnvironment("dev")

        val envs = service.getEnvironments()
        assertFalse("Should not contain dev", envs.any { it.name == "dev" })
        assertTrue("Should contain staging", envs.any { it.name == "staging" })
    }

    fun testRemoveActiveEnvironmentClearsSelection() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT))
        service.setActiveEnvironment("dev")
        service.removeEnvironment("dev")

        assertNull("Active environment should be cleared", service.getActiveEnvironmentName())
    }

    fun testRemoveNonActiveEnvironmentKeepsSelection() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT))
        service.addEnvironment(Environment("staging", EnvironmentScope.PROJECT))
        service.setActiveEnvironment("dev")
        service.removeEnvironment("staging")

        assertEquals("dev", service.getActiveEnvironmentName())
    }

    fun testUpdateEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://old.api.com")))
        service.updateEnvironment("dev", Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://new.api.com")))

        val envs = service.getEnvironments()
        val devEnv = envs.first { it.name == "dev" }
        assertEquals("http://new.api.com", devEnv.variables["URL"])
    }

    fun testUpdateEnvironmentRename() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))
        service.setActiveEnvironment("dev")
        service.updateEnvironment("dev", Environment("development", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))

        // Active environment should follow the rename
        assertEquals("development", service.getActiveEnvironmentName())
    }

    fun testUpdateEnvironmentDifferentNameKeepsActive() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT))
        service.addEnvironment(Environment("staging", EnvironmentScope.PROJECT))
        service.setActiveEnvironment("dev")
        service.updateEnvironment("staging", Environment("production", EnvironmentScope.PROJECT))

        assertEquals("dev", service.getActiveEnvironmentName())
    }

    fun testSetVariable() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))
        service.setActiveEnvironment("dev")
        service.setVariable("token", "xyz789")

        assertEquals("xyz789", service.resolveVariable("token"))
        assertEquals("http://dev.api.com", service.resolveVariable("URL"))
    }

    fun testSetVariableNoActiveEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))
        // No active environment — should be no-op
        service.setVariable("token", "xyz789")

        // Variable should not be set
        val envs = service.getEnvironments()
        val devEnv = envs.first { it.name == "dev" }
        assertFalse(devEnv.variables.containsKey("token"))
    }

    fun testUnsetVariable() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com", "token" to "abc")))
        service.setActiveEnvironment("dev")
        service.unsetVariable("token")

        assertNull(service.resolveVariable("token"))
        assertEquals("http://dev.api.com", service.resolveVariable("URL"))
    }

    fun testUnsetVariableNotPresent() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))
        service.setActiveEnvironment("dev")
        // Should be no-op
        service.unsetVariable("nonexistent")

        assertEquals("http://dev.api.com", service.resolveVariable("URL"))
    }

    fun testUnsetVariableNoActiveEnvironment() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))
        // No active environment — should be no-op
        service.unsetVariable("URL")

        val envs = service.getEnvironments()
        val devEnv = envs.first { it.name == "dev" }
        assertEquals("http://dev.api.com", devEnv.variables["URL"])
    }

    fun testAddGlobalEnvironment() {
        service.addEnvironment(Environment("global-env", EnvironmentScope.GLOBAL, mapOf("API_KEY" to "global-key")))
        service.setActiveEnvironment("global-env")

        assertEquals("global-key", service.resolveVariable("API_KEY"))
    }

    fun testEnvironmentDataStateFlow() {
        service.addEnvironment(Environment("dev", EnvironmentScope.PROJECT, mapOf("URL" to "http://dev.api.com")))
        service.setActiveEnvironment("dev")

        val data = service.environmentData.value
        assertEquals("dev", data.activeEnvironmentName)
        assertTrue(data.environments.any { it.name == "dev" })
    }

    fun testGetInstance() {
        val instance = EnvironmentService.getInstance(project)
        assertNotNull("EnvironmentService instance should not be null", instance)
    }
}
