package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.settings.DefaultSettingBinder
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ThrowingSettingBinder
import org.junit.Assert.*

/**
 * IDE-fixture tests for [FieldFormatChannelRegistry.isEnabled] — the single
 * chokepoint that resolves a field-format channel's effective enabled state.
 *
 * Mirrors [com.itangcent.easyapi.exporter.channel.ChannelRegistryIsEnabledTest].
 *
 * Coverage targets (from Codecov PR #736):
 * - The `try { ... } catch { LOG.warn(...); return enabledByDefault }` fallback
 *   path when [SettingBinder.read] throws (Req A2.6).
 * - The happy path: reading stored preferences and delegating to
 *   [FieldFormatChannelRegistry.resolveEnabled].
 */
class FieldFormatChannelRegistryIsEnabledTest : EasyApiLightCodeInsightFixtureTestCase() {

    private class StubFieldFormatChannel(
        override val id: String,
        override val displayName: String,
        override val actionText: String = "To$id",
        override val enabledByDefault: Boolean
    ) : FieldFormatChannel {
        override suspend fun format(project: Project, psiClass: PsiClass): String = ""
    }

    private val defaultOn = StubFieldFormatChannel("stub-on", "Stub On", enabledByDefault = true)
    private val defaultOff = StubFieldFormatChannel("stub-off", "Stub Off", enabledByDefault = false)

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
        val registry = FieldFormatChannelRegistry.getInstance(project)
        assertTrue(registry.isEnabled(defaultOn))
    }

    fun testIsEnabled_defaultOff_noPreference_returnsFalse() {
        SettingBinder.getInstance(project).save(GeneralSettings())
        val registry = FieldFormatChannelRegistry.getInstance(project)
        assertFalse(registry.isEnabled(defaultOff))
    }

    fun testIsEnabled_defaultOn_explicitlyDisabled_returnsFalse() {
        SettingBinder.getInstance(project).save(
            GeneralSettings(disabledFieldFormatChannels = arrayOf("stub-on"))
        )
        val registry = FieldFormatChannelRegistry.getInstance(project)
        assertFalse(registry.isEnabled(defaultOn))
    }

    fun testIsEnabled_defaultOff_explicitlyEnabled_returnsTrue() {
        SettingBinder.getInstance(project).save(
            GeneralSettings(enabledFieldFormatChannels = arrayOf("stub-off"))
        )
        val registry = FieldFormatChannelRegistry.getInstance(project)
        assertTrue(registry.isEnabled(defaultOff))
    }

    // --- Fallback path: SettingBinder.read throws → enabledByDefault (Req A2.6) ---

    fun testIsEnabled_fallbackReturnsEnabledByDefault_whenSettingsReadThrows_defaultOn() {
        project.registerServiceInstance(
            SettingBinder::class.java,
            ThrowingSettingBinder()
        )
        val registry = FieldFormatChannelRegistry.getInstance(project)
        assertTrue(
            "When settings read fails, a default-on format must fall back to enabled",
            registry.isEnabled(defaultOn)
        )
    }

    fun testIsEnabled_fallbackReturnsEnabledByDefault_whenSettingsReadThrows_defaultOff() {
        project.registerServiceInstance(
            SettingBinder::class.java,
            ThrowingSettingBinder()
        )
        val registry = FieldFormatChannelRegistry.getInstance(project)
        assertFalse(
            "When settings read fails, a default-off format must fall back to disabled",
            registry.isEnabled(defaultOff)
        )
    }
}
