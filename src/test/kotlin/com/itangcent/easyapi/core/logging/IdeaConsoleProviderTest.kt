package com.itangcent.easyapi.core.logging

import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class IdeaConsoleProviderTest : EasyApiLightCodeInsightFixtureTestCase() {

    private val testSettingBinder by lazy { SettingBinder.getInstance(project) }

    /**
     * Restores `logLevel` after each test that mutates it so tests stay isolated.
     */
    private fun withLogLevel(level: Int, block: () -> Unit) {
        val original = testSettingBinder.read(GeneralSettings::class).logLevel
        try {
            val settings = testSettingBinder.read(GeneralSettings::class)
            settings.logLevel = level
            testSettingBinder.save(settings)
            block()
        } finally {
            val settings = testSettingBinder.read(GeneralSettings::class)
            settings.logLevel = original
            testSettingBinder.save(settings)
        }
    }

    fun testGetConsoleReturnsNonNull() {
        val provider = IdeaConsoleProvider.getInstance(project)
        val console = provider.getConsole()
        assertNotNull("getConsole should return non-null", console)
    }

    fun testGetConsoleReturnsIdeaConsole() {
        val provider = IdeaConsoleProvider.getInstance(project)
        val console = provider.getConsole()
        assertTrue("Console should implement IdeaConsole", console is IdeaConsole)
    }

    fun testGetInstanceReturnsSameProvider() {
        val provider1 = IdeaConsoleProvider.getInstance(project)
        val provider2 = IdeaConsoleProvider.getInstance(project)
        assertSame("getInstance should return same provider", provider1, provider2)
    }

    // ------------------------------------------------------------------
    // SILENT (default) → IdeaLogConsole
    // ------------------------------------------------------------------

    fun testSilentLevelReturnsIdeaLogConsole() {
        withLogLevel(100) {
            val provider = IdeaConsoleProvider.getInstance(project)
            val console = provider.getConsole()
            assertTrue(
                "At SILENT level (100) getConsole should return IdeaLogConsole",
                console is IdeaLogConsole
            )
        }
    }

    fun testSilentLevelReturnsSameSingleton() {
        withLogLevel(100) {
            val provider = IdeaConsoleProvider.getInstance(project)
            val console1 = provider.getConsole()
            val console2 = provider.getConsole()
            assertSame(
                "At SILENT level getConsole should return the IdeaLogConsole singleton",
                console1, console2
            )
        }
    }

    // ------------------------------------------------------------------
    // Non-SILENT → ConfigurableIdeaConsole
    // ------------------------------------------------------------------

    fun testWarnLevelReturnsConfigurableIdeaConsole() {
        withLogLevel(LogLevel.WARN.threshold) {
            val provider = IdeaConsoleProvider.getInstance(project)
            val console = provider.getConsole()
            assertTrue(
                "At WARN level getConsole should return ConfigurableIdeaConsole",
                console is ConfigurableIdeaConsole
            )
        }
    }

    fun testErrorLevelReturnsConfigurableIdeaConsole() {
        withLogLevel(LogLevel.ERROR.threshold) {
            val provider = IdeaConsoleProvider.getInstance(project)
            val console = provider.getConsole()
            assertTrue(
                "At ERROR level getConsole should return ConfigurableIdeaConsole",
                console is ConfigurableIdeaConsole
            )
        }
    }

    fun testNonSilentLevelReturnsSameInstance() {
        withLogLevel(LogLevel.WARN.threshold) {
            val provider = IdeaConsoleProvider.getInstance(project)
            val console1 = provider.getConsole()
            val console2 = provider.getConsole()
            assertSame(
                "At non-SILENT level getConsole should return the same ConfigurableIdeaConsole instance",
                console1, console2
            )
        }
    }

    // ------------------------------------------------------------------
    // Level switching
    // ------------------------------------------------------------------

    fun testConsoleSwitchesWhenLevelChanges() {
        withLogLevel(100) {
            val provider = IdeaConsoleProvider.getInstance(project)

            val silentConsole = provider.getConsole()
            assertTrue("Should start as IdeaLogConsole", silentConsole is IdeaLogConsole)

            // Switch to WARN
            val settings = testSettingBinder.read(GeneralSettings::class)
            settings.logLevel = LogLevel.WARN.threshold
            testSettingBinder.save(settings)

            val warnConsole = provider.getConsole()
            assertTrue("Should switch to ConfigurableIdeaConsole", warnConsole is ConfigurableIdeaConsole)
        }
    }

    // ------------------------------------------------------------------
    // Project.console extension
    // ------------------------------------------------------------------

    fun testProjectConsoleExtensionReturnsSameAsGetConsole() {
        val provider = IdeaConsoleProvider.getInstance(project)
        val directConsole = provider.getConsole()
        val extensionConsole = project.console
        assertSame(
            "Project.console extension should return the same instance as getConsole()",
            directConsole, extensionConsole
        )
    }

    fun testProjectConsoleExtensionReturnsIdeaConsole() {
        val console = project.console
        assertTrue("Project.console should return an IdeaConsole", console is IdeaConsole)
    }

    fun testProjectSettingsExtensionReturnsSettings() {
        val settings = project.settings<GeneralSettings>()
        assertNotNull("settings<GeneralSettings> should return non-null", settings)
        assertTrue(
            "Default logLevel should be SILENT (100)",
            settings.logLevel >= 100
        )
    }
}
