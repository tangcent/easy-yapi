package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.io.File
import java.nio.file.Files

/**
 * Tests for [ProjectRulesSubTab].
 *
 * The sub-tab now shows a single table of `.easyapi/` folder files. Legacy
 * `.easy.api.config*` files are no longer displayed, but a previously-disabled
 * legacy path is preserved in `Settings.disabledAutoRuleFiles` across a
 * reset/apply round-trip.
 */
class ProjectRulesSubTabTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var projectDir: File

    override fun setUp() {
        super.setUp()
        projectDir = Files.createTempDirectory("proj-rules-").toFile()
        projectDir.deleteOnExit()
    }

    fun testEasyapiFolderFilesDetected() {
        val easyapiDir = File(projectDir, ".easyapi").apply { mkdirs() }
        val ruleFile = File(easyapiDir, "rule.properties").apply { writeText("name=folder") }

        val panel = ProjectRulesSubTab(project, projectDir.absolutePath)
        val settings = Settings()
        panel.resetFrom(settings)

        val easyapiPaths = easyapiRowPaths(panel)
        assertTrue(".easyapi/ file should be detected: $easyapiPaths", ruleFile.absolutePath in easyapiPaths)
    }

    fun testDisablingEasyapiFileRoundTripsToDisabledAutoSet() {
        val easyapiDir = File(projectDir, ".easyapi").apply { mkdirs() }
        val ruleFile = File(easyapiDir, "rule.properties").apply { writeText("name=folder") }
        val panel = ProjectRulesSubTab(project, projectDir.absolutePath)
        val settings = Settings()
        panel.resetFrom(settings)

        // Toggle the `.easyapi/` row's enabled state via reflection (no UI).
        setRowEnabled(panel, "easyapiRows", ruleFile.absolutePath, enabled = false)
        assertTrue("disabling should mark modified", panel.isModified(settings))

        panel.applyTo(settings)
        assertEquals(
            listOf(ruleFile.absolutePath),
            settings.disabledAutoRuleFiles.toList()
        )

        panel.resetFrom(settings)
        assertFalse("round-trip should be clean", panel.isModified(settings))
    }

    /**
     * Legacy root files are not displayed, but a disabled legacy path recorded
     * in settings must survive a reset/apply round-trip (it must NOT be
     * silently re-enabled by dropping the legacy table).
     */
    fun testDisabledLegacyPathPreserved() {
        val rootConfig = File(projectDir, ".easy.api.config").apply { writeText("name=root") }
        val panel = ProjectRulesSubTab(project, projectDir.absolutePath)
        val settings = Settings().apply {
            disabledAutoRuleFiles = arrayOf(rootConfig.absolutePath)
        }

        panel.resetFrom(settings)
        // The legacy file is not shown in the (empty) `.easyapi/` table.
        assertTrue("no.easyapi/ rows expected", easyapiRowPaths(panel).isEmpty())
        // But it is not "modified" — the disabled legacy path is preserved.
        assertFalse("preserved legacy disable should not be a modification", panel.isModified(settings))

        panel.applyTo(settings)
        assertTrue(
            "disabled legacy path must be preserved",
            rootConfig.absolutePath in settings.disabledAutoRuleFiles
        )
    }

    fun testBasePathWithoutRuleFilesYieldsNoFiles() {
        // A clean project dir with no `.easyapi/` folder and no legacy files.
        val empty = Files.createTempDirectory("proj-empty-").toFile()
        empty.deleteOnExit()
        val panel = ProjectRulesSubTab(project, empty.absolutePath)
        val settings = Settings()
        panel.resetFrom(settings)
        assertEquals(emptyList<String>(), easyapiRowPaths(panel))
        assertFalse(panel.isModified(settings))
    }

    fun testListedEasyapiFilesReflectsDisk() {
        val easyapiDir = File(projectDir, ".easyapi").apply { mkdirs() }
        File(easyapiDir, "a.rules").apply { writeText("a") }
        File(easyapiDir, "b.rules").apply { writeText("b") }
        val panel = ProjectRulesSubTab(project, projectDir.absolutePath)

        val listed = panel.listedEasyapiFiles().map { File(it).name }.sorted()
        assertEquals(listOf("a.rules", "b.rules"), listed)
    }

    // ---- helpers ----

    private fun easyapiRowPaths(panel: ProjectRulesSubTab): List<String> =
        rowPaths(panel, "easyapiRows")

    private fun rowPaths(panel: ProjectRulesSubTab, fieldName: String): List<String> {
        val field = ProjectRulesSubTab::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val rows = field.get(panel) as MutableList<RuleFileRow>
        return rows.map { it.path }
    }

    private fun setRowEnabled(panel: ProjectRulesSubTab, fieldName: String, path: String, enabled: Boolean) {
        val field = ProjectRulesSubTab::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val rows = field.get(panel) as MutableList<RuleFileRow>
        rows.first { it.path == path }.enabled = enabled
    }
}
