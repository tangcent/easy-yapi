package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.module.RuleFileSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for [GlobalRulesSubTab].
 *
 * Verifies that files in the global directory are listed, that toggling
 * Enabled round-trips through `Settings.disabledGlobalRuleFiles`, and that
 * `listedFiles()` reflects disk state. Tests use a temp directory via
 * [GlobalRulesSubTab.globalDirOverride] to avoid touching the real `~/.easyapi/`.
 */
class GlobalRulesSubTabTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var globalDir: Path

    override fun setUp() {
        super.setUp()
        globalDir = Files.createTempDirectory("global-rules-")
        globalDir.toFile().deleteOnExit()
    }

    private fun newPanel(): GlobalRulesSubTab = GlobalRulesSubTab(project, globalDirOverride = globalDir)

    fun testEmptyDirRoundTripsClean() {
        val panel = newPanel()
        val settings = RuleFileSettings()
        panel.resetFrom(settings)
        assertFalse("empty dir should not be modified", panel.isModified(settings))
        panel.applyTo(settings)
        assertEquals(0, settings.disabledGlobalRuleFiles.size)
    }

    fun testFilesInDirDetected() {
        val a = globalDir.resolve("a.rules").apply { Files.writeString(this, "a=1") }
        val panel = newPanel()
        val settings = RuleFileSettings()
        panel.resetFrom(settings)

        val listed = panel.listedFiles()
        assertTrue("a.rules should be listed: $listed", a.toAbsolutePath().toString() in listed)
        assertFalse("nothing disabled yet", panel.isModified(settings))
    }

    fun testDisablingRoundTripsToDisabledGlobalSet() {
        val ruleFile = globalDir.resolve("rule.properties").apply { Files.writeString(this, "x=1") }
        val panel = newPanel()
        val settings = RuleFileSettings()
        panel.resetFrom(settings)

        // Toggle the row's enabled state via reflection (no UI).
        setRowEnabled(panel, ruleFile.toAbsolutePath().toString(), enabled = false)
        assertTrue("disabling should mark modified", panel.isModified(settings))

        panel.applyTo(settings)
        assertEquals(
            listOf(ruleFile.toAbsolutePath().toString()),
            settings.disabledGlobalRuleFiles.toList()
        )

        panel.resetFrom(settings)
        assertFalse("round-trip should be clean", panel.isModified(settings))
    }

    // ---- helpers ----

    private fun setRowEnabled(panel: GlobalRulesSubTab, path: String, enabled: Boolean) {
        val field = GlobalRulesSubTab::class.java.getDeclaredField("rows")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val rows = field.get(panel) as MutableList<RuleFileRow>
        rows.first { it.path == path }.enabled = enabled
    }
}
