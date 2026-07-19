package com.itangcent.easyapi.core.settings

import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.module.HttpSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

/**
 * Tests for [DefaultSettingBinder] covering read/save round-trips, cache
 * invalidation, and error paths for unconstructable settings modules.
 *
 * Extends [EasyApiLightCodeInsightFixtureTestCase] because [DefaultSettingBinder]
 * requires a real [com.intellij.openapi.project.Project] with registered
 * unified state components and a message bus.
 */
class DefaultSettingBinderTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testReadReturnsDefaultsForKnownModule() {
        // Reset to defaults first so the test is independent of persisted
        // state left by other test classes that save GeneralSettings.
        settingBinder.save(GeneralSettings())
        val general = settingBinder.read(GeneralSettings::class)
        assertNotNull(general)
        // GeneralSettings defaults — framework enablement arrays are empty
        // (Feign/SpringActuator default-off, JAX-RS/gRPC default-on).
        assertFalse(general.enabledFrameworks.contains("Feign"))
        assertFalse(general.disabledFrameworks.contains("JAX-RS"))
    }

    fun testTryReadReturnsNullForUnconstructableModule() {
        val result = settingBinder.tryRead(RequiredArgSettings::class)
        assertNull("tryRead should return null when the module cannot be constructed", result)
    }

    fun testReadThrowsForUnconstructableModule() {
        try {
            settingBinder.read(RequiredArgSettings::class)
            fail("Expected IllegalStateException when reading an unconstructable settings module")
        } catch (e: IllegalStateException) {
            assertTrue(
                "Error message should mention the module name",
                e.message!!.contains(RequiredArgSettings::class.qualifiedName!!)
            )
        }
    }

    fun testSaveAndReadRoundTrip() {
        val custom = GeneralSettings(
            enabledFrameworks = arrayOf("Feign"),
            disabledFrameworks = arrayOf("JAX-RS"),
            logLevel = 50
        )
        settingBinder.save(custom)

        val readBack = settingBinder.read(GeneralSettings::class)
        assertTrue("enabledFrameworks should round-trip", readBack.enabledFrameworks.contains("Feign"))
        assertTrue("disabledFrameworks should round-trip", readBack.disabledFrameworks.contains("JAX-RS"))
        assertEquals(50, readBack.logLevel)
    }

    fun testSaveInvalidatesCache() {
        // Save a known state first (other tests may have modified it)
        settingBinder.save(GeneralSettings())
        val original = settingBinder.read(GeneralSettings::class)
        assertFalse(original.enabledFrameworks.contains("Feign"))

        // Save new values — should invalidate the cache
        settingBinder.save(GeneralSettings(enabledFrameworks = arrayOf("Feign")))

        // Second read should reflect the saved values, not the cached defaults
        val readBack = settingBinder.read(GeneralSettings::class)
        assertTrue("Cache should be invalidated after save", readBack.enabledFrameworks.contains("Feign"))
    }

    fun testTryReadReturnsCachedValueBeforeTtl() {
        // First read populates the cache
        val first = settingBinder.tryRead(GeneralSettings::class)
        assertNotNull(first)

        // Second tryRead should return the cached instance
        val second = settingBinder.tryRead(GeneralSettings::class)
        assertNotNull(second)
        // Both should have the same values (defaults)
        assertEquals(first!!.enabledFrameworks.toList(), second!!.enabledFrameworks.toList())
    }

    fun testSaveAndReadRoundTripForHttpSettings() {
        val custom = HttpSettings(
            httpTimeOut = 60,
            unsafeSsl = true,
            httpClient = "OKHTTP"
        )
        settingBinder.save(custom)

        val readBack = settingBinder.read(HttpSettings::class)
        assertEquals(60, readBack.httpTimeOut)
        assertTrue(readBack.unsafeSsl)
        assertEquals("OKHTTP", readBack.httpClient)
    }

    fun testReadReturnsDefaultsForUnrecognizedState() {
        // Reading a module that has never been saved should return defaults
        val http = settingBinder.read(HttpSettings::class)
        assertEquals(30, http.httpTimeOut)
        assertFalse(http.unsafeSsl)
    }
}

/**
 * A [Settings] implementation with a required constructor parameter (no default).
 * Used to test [DefaultSettingBinder]'s error paths: `createDefaultInstance()`
 * returns null, `tryRead` returns null, and `read` throws IllegalStateException.
 */
data class RequiredArgSettings(val required: String) : Settings
