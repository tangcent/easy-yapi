package com.itangcent.easyapi.channel.apipost

import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

/**
 * Round-trip tests for [ApipostOptionsPanel].
 *
 * Uses [EasyApiLightCodeInsightFixtureTestCase] because the panel builds Swing
 * components (`JBTextField` / `JBPasswordField`) and needs the IDEA
 * look-and-feel initialized.
 *
 * The behaviour worth pinning is that **blank means "no override"**: an empty
 * field must yield `null` so `ApipostChannel.export` falls through to the
 * persisted settings. Producing `""` would win the `takeIf { isNotBlank() }`
 * check and silently override a configured token with nothing.
 */
class ApipostOptionsPanelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: ApipostOptionsPanel

    override fun setUp() {
        super.setUp()
        panel = ApipostOptionsPanel(project)
    }

    fun testComponentIsNotNull() {
        assertNotNull(panel.component)
    }

    fun testEmptyPanelBuildsConfigWithNulls() {
        val cfg = panel.buildConfig()
        assertEquals(null, cfg.selectedToken)
        assertEquals(null, cfg.projectId)
    }

    fun testPanelBuildsConfigFromFields() {
        panel.setToken("tok")
        panel.setProjectId("42")
        val cfg = panel.buildConfig()
        assertEquals("tok", cfg.selectedToken)
        assertEquals("42", cfg.projectId)
    }

    fun testBlankFieldsBecomeNull() {
        // Blank must degrade to "not overridden", not to an empty-string
        // override that would lose to the settings tier by accident.
        panel.setToken("   ")
        panel.setProjectId("   ")
        assertEquals(null, panel.buildConfig().selectedToken)
        assertEquals(null, panel.buildConfig().projectId)
    }

    fun testPanelDoesNotWriteGlobalSettings() {
        // AC-4: a token typed here is per-export only — the persisted
        // ApipostSettings must be untouched by the panel itself.
        val settingsBefore = project.settings<ApipostSettings>().apipostToken
        panel.setToken("temporary-token")
        assertEquals("temporary-token", panel.buildConfig().selectedToken)
        assertEquals(
            "Building a config must not persist the temporary token",
            settingsBefore,
            project.settings<ApipostSettings>().apipostToken,
        )
    }
}
