package com.itangcent.easyapi.channel.spi

import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.settings.DefaultSettingBinder
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ThrowingSettingBinder
import org.junit.Assert.*

/**
 * IDE-fixture tests for [ChannelRegistry.isEnabled] — the single chokepoint
 * that resolves a channel's effective enabled state.
 *
 * Coverage targets (from Codecov PR #736):
 * - The `try { ... } catch { LOG.warn(...); return enabledByDefault }` fallback
 *   path when [SettingBinder.read] throws (Req 2.6).
 * - The happy path: reading stored preferences and delegating to
 *   [ChannelRegistry.resolveEnabled].
 */
class ChannelRegistryIsEnabledTest : EasyApiLightCodeInsightFixtureTestCase() {

    private class StubChannel(
        override val id: String,
        override val displayName: String,
        override val enabledByDefault: Boolean
    ) : Channel {
        override suspend fun export(context: ExportContext): ExportResult =
            ExportResult.Success(0, "")
    }

    private val defaultOn = StubChannel("stub-on", "Stub On", enabledByDefault = true)
    private val defaultOff = StubChannel("stub-off", "Stub Off", enabledByDefault = false)

    override fun tearDown() {
        try {
            // Restore DefaultSettingBinder so a ThrowingSettingBinder registered
            // by a fallback test does not leak into subsequent test classes.
            // The base setUp() calls loadCommonJDKClasses() BEFORE re-registering
            // DefaultSettingBinder, so a stale ThrowingSettingBinder would cause
            // VFS-event listeners to throw during the next class's setUp().
            project.registerServiceInstance(
                SettingBinder::class.java,
                DefaultSettingBinder(project)
            )
        } finally {
            super.tearDown()
        }
    }

    // --- Happy path: stored preferences are honoured ---

    fun testIsEnabled_defaultOn_noPreference_returnsTrue() {
        SettingBinder.getInstance(project).save(GeneralSettings())
        val registry = ChannelRegistry.getInstance(project)
        assertTrue(registry.isEnabled(defaultOn))
    }

    fun testIsEnabled_defaultOff_noPreference_returnsFalse() {
        SettingBinder.getInstance(project).save(GeneralSettings())
        val registry = ChannelRegistry.getInstance(project)
        assertFalse(registry.isEnabled(defaultOff))
    }

    fun testIsEnabled_defaultOn_explicitlyDisabled_returnsFalse() {
        SettingBinder.getInstance(project).save(
            GeneralSettings(disabledChannels = arrayOf("stub-on"))
        )
        val registry = ChannelRegistry.getInstance(project)
        assertFalse(registry.isEnabled(defaultOn))
    }

    fun testIsEnabled_defaultOff_explicitlyEnabled_returnsTrue() {
        SettingBinder.getInstance(project).save(
            GeneralSettings(enabledChannels = arrayOf("stub-off"))
        )
        val registry = ChannelRegistry.getInstance(project)
        assertTrue(registry.isEnabled(defaultOff))
    }

    // --- Fallback path: SettingBinder.read throws → enabledByDefault (Req 2.6) ---

    fun testIsEnabled_fallbackReturnsEnabledByDefault_whenSettingsReadThrows_defaultOn() {
        project.registerServiceInstance(
            SettingBinder::class.java,
            ThrowingSettingBinder()
        )
        val registry = ChannelRegistry.getInstance(project)
        assertTrue(
            "When settings read fails, a default-on channel must fall back to enabled",
            registry.isEnabled(defaultOn)
        )
    }

    fun testIsEnabled_fallbackReturnsEnabledByDefault_whenSettingsReadThrows_defaultOff() {
        project.registerServiceInstance(
            SettingBinder::class.java,
            ThrowingSettingBinder()
        )
        val registry = ChannelRegistry.getInstance(project)
        assertFalse(
            "When settings read fails, a default-off channel must fall back to disabled",
            registry.isEnabled(defaultOff)
        )
    }
}
