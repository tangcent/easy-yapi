package com.itangcent.easyapi.core.rule

import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.internal.PluginInfo.PLUGIN_ID
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * IDE-fixture tests for [RuleProposalValidator]'s A5c disabled-source soft
 * warnings.
 *
 * Registers a stub `"postman"` channel via the `channel` extension point so
 * the channel-prefix check on the general-sourced `postman.*` keys (declared
 * in [RuleKeys]) can be exercised end-to-end, mirroring
 * [RuleKeyRegistryEnabledKeysTest]. The stub channel contributes no rule keys
 * of its own — the `postman.*` keys remain general-sourced, and the prefix
 * check is what triggers the warning.
 */
class RuleProposalValidatorDisabledSourceTest : EasyApiLightCodeInsightFixtureTestCase() {

    /**
     * Minimal [Channel] stub with id `"postman"` so the channel-prefix check
     * in [RuleProposalValidator.checkDisabledSource] can resolve the owner.
     * `enabledByDefault = true` mirrors the real PostmanChannel.
     */
    private class StubPostmanChannel : Channel {
        override val id: String = "postman"
        override val displayName: String = "Postman (stub)"
        override val enabledByDefault: Boolean = true
        override suspend fun export(context: ExportContext): ExportResult =
            ExportResult.Success(0, "")
    }

    override fun setUp() {
        super.setUp()
        // Register the stub postman channel via the project-scoped EP.
        project.extensionArea
            .getExtensionPoint<Channel>("$PLUGIN_ID.channel")
            .registerExtension(StubPostmanChannel(), testRootDisposable)
        // Default: no explicit preferences → postman enabled by default.
        SettingBinder.getInstance(project).save(GeneralSettings())
    }

    private fun disablePostman() {
        SettingBinder.getInstance(project).save(
            GeneralSettings(disabledChannels = arrayOf("postman"))
        )
    }

    // --- postman.test: warns when Postman disabled, silent when enabled ---

    fun testPostmanTestKey_warnsWhenPostmanDisabled() = runTest {
        disablePostman()
        val result = RuleProposalValidator.validate("postman.test=pm.test(\"ok\")", project)
        assertTrue("proposal must still stage (soft warning only)", result.ok)
        assertTrue(
            "should warn that postman.test belongs to disabled postman",
            result.warnings.any { it.contains("postman.test") && it.contains("disabled") }
        )
    }

    fun testPostmanTestKey_noWarningWhenPostmanEnabled() = runTest {
        val result = RuleProposalValidator.validate("postman.test=pm.test(\"ok\")", project)
        assertTrue("errors: ${result.errors}", result.ok)
        assertEquals("no warning expected when Postman enabled", 0, result.warnings.size)
    }

    // --- postman.prerequest: same prefix-based ownership ---

    fun testPostmanPrerequestKey_warnsWhenPostmanDisabled() = runTest {
        disablePostman()
        val result = RuleProposalValidator.validate("postman.prerequest=console.log(1)", project)
        assertTrue(result.ok)
        assertTrue(
            "should warn that postman.prerequest belongs to disabled postman",
            result.warnings.any { it.contains("postman.prerequest") && it.contains("disabled") }
        )
    }

    // --- General key without channel prefix: never warns ---

    fun testGeneralKeyNoWarningWhenPostmanDisabled() = runTest {
        disablePostman()
        val result = RuleProposalValidator.validate("api.name=My API", project)
        assertTrue("errors: ${result.errors}", result.ok)
        assertEquals("api.name has no channel prefix → no warning", 0, result.warnings.size)
    }

    // --- Unknown-key hard-error path unchanged ---

    fun testUnknownKeyHardErrorUnchanged() = runTest {
        // The disabled-source soft-warning path must not mask the unknown-key
        // hard error. An unknown key still blocks the proposal.
        val result = RuleProposalValidator.validate("api.unknown_key=foo", project)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("unknown rule key") })
    }

    // --- Soft warning never blocks: mix of disabled-source key + clean key ---

    fun testDisabledSourceWarningDoesNotBlockOtherValidKeys() = runTest {
        disablePostman()
        val content = """
            api.name=My API
            postman.test=pm.test("ok")
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("proposal must still stage (soft warning only)", result.ok)
        assertTrue(
            "should warn about postman.test",
            result.warnings.any { it.contains("postman.test") }
        )
    }
}
