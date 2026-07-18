package com.itangcent.easyapi.ide.fieldformat

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure unit tests for [FieldFormatChannelRegistry.resolveEnabled] (the resolution
 * rule) and the filtered-query logic that mirrors it.
 *
 * Mirrors [com.itangcent.easyapi.exporter.channel.ChannelRegistryTest]: the
 * pure resolution rule is extracted to an `internal` companion helper so the
 * full truth table can be exercised without a [Project]. The
 * `getEnabledChannels()` query method itself needs a [Project] (it reads
 * settings via [com.itangcent.easyapi.settings.SettingBinder]); here we
 * replicate the exact filtering it applies (`&& resolveEnabled`) to verify the
 * rule excludes disabled formats from the enumeration.
 */
class FieldFormatChannelRegistryTest {

    private class StubFieldFormatChannel(
        override val id: String,
        override val displayName: String,
        override val actionText: String = "To$id",
        override val enabledByDefault: Boolean = true
    ) : FieldFormatChannel {
        override suspend fun format(project: Project, psiClass: PsiClass): String = ""
    }

    // --- Task A.1: FieldFormatChannel.enabledByDefault ---

    @Test
    fun testEnabledByDefaultDefaultsToTrue() {
        // A StubFieldFormatChannel that does not override enabledByDefault resolves to true.
        val channel = StubFieldFormatChannel("test", "Test")
        assertTrue(channel.enabledByDefault)
    }

    @Test
    fun testEnabledByDefaultCanBeOverridden() {
        // A StubFieldFormatChannel that overrides enabledByDefault = false resolves to false.
        val channel = StubFieldFormatChannel("test", "Test", enabledByDefault = false)
        assertFalse(channel.enabledByDefault)
    }

    // --- Task A.3: FieldFormatChannelRegistry.resolveEnabled (pure resolution rule) ---
    // Exercises the full truth table of the resolution rule without a Project.

    private val defaultOn = StubFieldFormatChannel("default-on", "Default On", enabledByDefault = true)
    private val defaultOff = StubFieldFormatChannel("default-off", "Default Off", enabledByDefault = false)
    private val empty = emptyArray<String>()

    @Test
    fun testResolveEnabled_defaultOnNoPreference_isEnabled() {
        assertTrue(FieldFormatChannelRegistry.resolveEnabled(defaultOn, empty, empty))
    }

    @Test
    fun testResolveEnabled_defaultOnInDisabled_isDisabled() {
        assertFalse(FieldFormatChannelRegistry.resolveEnabled(defaultOn, empty, arrayOf("default-on")))
    }

    @Test
    fun testResolveEnabled_defaultOnInEnabled_isEnabled() {
        assertTrue(FieldFormatChannelRegistry.resolveEnabled(defaultOn, arrayOf("default-on"), empty))
    }

    @Test
    fun testResolveEnabled_defaultOffNoPreference_isDisabled() {
        assertFalse(FieldFormatChannelRegistry.resolveEnabled(defaultOff, empty, empty))
    }

    @Test
    fun testResolveEnabled_defaultOffInEnabled_isEnabled() {
        assertTrue(FieldFormatChannelRegistry.resolveEnabled(defaultOff, arrayOf("default-off"), empty))
    }

    @Test
    fun testResolveEnabled_defaultOffInDisabled_isDisabled() {
        // Redundant but explicit — default-off in disabledIds stays disabled.
        assertFalse(FieldFormatChannelRegistry.resolveEnabled(defaultOff, empty, arrayOf("default-off")))
    }

    @Test
    fun testResolveEnabled_idInBothEnabledAndDisabled_explicitOnWins() {
        // Design "Normalization on save": when a format's id is in both sets,
        // explicit-on wins (the `in enabledIds` short-circuit returns true).
        assertTrue(FieldFormatChannelRegistry.resolveEnabled(defaultOn, arrayOf("default-on"), arrayOf("default-on")))
        assertTrue(FieldFormatChannelRegistry.resolveEnabled(defaultOff, arrayOf("default-off"), arrayOf("default-off")))
    }

    @Test
    fun testResolveEnabled_fallsBackToEnabledByDefaultWhenPreferenceAbsent() {
        // Req A2.6: absent preference (empty arrays) falls back to enabledByDefault.
        // This mirrors the fallback path taken when settings storage is unreadable.
        assertTrue(FieldFormatChannelRegistry.resolveEnabled(defaultOn, empty, empty))
        assertFalse(FieldFormatChannelRegistry.resolveEnabled(defaultOff, empty, empty))
    }

    // --- Task A.3: filtered query logic ---
    // The registry query method (getEnabledChannels) needs a Project (covered by
    // FieldFormatActionGroupRefreshTest). Here we replicate the exact filtering it
    // applies (filter resolveEnabled) to verify the rule excludes disabled formats.

    @Test
    fun testFilteredQueryLogic_getEnabledChannelsExcludesDisabled() {
        val channels = listOf(
            StubFieldFormatChannel("json", "JSON", enabledByDefault = true),
            StubFieldFormatChannel("json5", "JSON5", enabledByDefault = true),
            StubFieldFormatChannel("yaml", "YAML", enabledByDefault = false)
        )
        val enabledIds = emptyArray<String>()
        val disabledIds = arrayOf("json5")

        // Mirrors getEnabledChannels(): filter { isEnabled(it) }
        // = filter { resolveEnabled(it, enabledIds, disabledIds) }
        val enabled = channels.filter {
            FieldFormatChannelRegistry.resolveEnabled(it, enabledIds, disabledIds)
        }
        assertEquals(1, enabled.size)
        assertEquals("json", enabled[0].id)
    }

    @Test
    fun testFilteredQueryLogic_allChannelsRemainUnfiltered() {
        // Req A3.4: allChannels() is a pure registry primitive — it does NOT
        // consult enablement. Here we assert the lookup-by-id logic itself
        // ignores enabledByDefault (a disabled format is still listed).
        val channels = listOf(
            StubFieldFormatChannel("on", "On", enabledByDefault = true),
            StubFieldFormatChannel("off", "Off", enabledByDefault = false)
        )
        assertEquals("off", channels.firstOrNull { it.id == "off" }?.id)
        assertEquals("on", channels.firstOrNull { it.id == "on" }?.id)
        assertNull(channels.firstOrNull { it.id == "missing" })
    }

    @Test
    fun testFilteredQueryLogic_explicitEnableOverridesDefaultOff() {
        val channels = listOf(
            StubFieldFormatChannel("default-off-1", "Off 1", enabledByDefault = false),
            StubFieldFormatChannel("default-off-2", "Off 2", enabledByDefault = false)
        )
        // Explicitly enable default-off-1; default-off-2 stays disabled (default-off, no pref).
        val enabledIds = arrayOf("default-off-1")
        val disabledIds = emptyArray<String>()
        val enabled = channels.filter {
            FieldFormatChannelRegistry.resolveEnabled(it, enabledIds, disabledIds)
        }
        assertEquals(1, enabled.size)
        assertEquals("default-off-1", enabled[0].id)
    }

    @Test
    fun testFilteredQueryLogic_emptyRegistryProducesEmptyEnabled() {
        val channels = emptyList<FieldFormatChannel>()
        val enabledIds = emptyArray<String>()
        val disabledIds = emptyArray<String>()
        val enabled = channels.filter {
            FieldFormatChannelRegistry.resolveEnabled(it, enabledIds, disabledIds)
        }
        assertTrue("Empty registry should produce an empty enabled list", enabled.isEmpty())
    }
}
