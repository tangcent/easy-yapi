package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlin.reflect.full.memberProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Round-trip tests for [OpenApiOptionsPanel].
 *
 * The panel exposes a two-way format selector (`JSON` default / `YAML`).
 * The "Always Ask" option is NOT exposed in the per-export panel — the panel
 * itself IS the per-export prompt, so "Always Ask" would be redundant (it
 * would just trigger a second `MessagesService.showChooseDialog` inside `export()`).
 * "Always Ask" remains available in [OpenApiSettings] as the persistent
 * default. The v1 envelope setters (`setTitle` / `setVersion` /
 * `setDescription` / `setServerUrl`) and the corresponding `OpenApiConfig`
 * fields were removed — those values vary per project and belong in rule
 * scripts.
 *
 * Pattern: select a radio → call [OpenApiOptionsPanel.buildConfig] →
 * assert the returned [OpenApiConfig] carries the selected format.
 * Conversely, call [OpenApiOptionsPanel.applyConfig] with a pre-built
 * [OpenApiConfig] and assert the radios reflect it.
 *
 * Uses [EasyApiLightCodeInsightFixtureTestCase] so Swing components are
 * properly initialized in an IntelliJ application context (the panel uses
 * `JRadioButton` which needs the IDEA look-and-feel).
 */
class OpenApiOptionsPanelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: OpenApiOptionsPanel

    override fun setUp() {
        super.setUp()
        panel = OpenApiOptionsPanel(project)
    }

    fun testComponentIsNotNull() {
        assertNotNull(panel.component)
    }

    fun testBuildConfigReturnsChannelConfigSubtype() {
        val config = panel.buildConfig()
        assertNotNull(config)
        assertTrue("buildConfig should return OpenApiConfig, got: ${config.javaClass}", config is OpenApiConfig)
    }

    fun testDefaultBuildConfigReturnsJsonFormat() {
        // JSON is the default-selected radio.
        val config = panel.buildConfig() as OpenApiConfig
        assertEquals(OpenApiOutputFormat.JSON, config.outputFormat)
    }

    fun testBuildConfigReturnsOnlyOutputFormatProperty() {
        // The v1 envelope fields (infoTitle / infoVersion / infoDescription /
        // serverUrl) were removed. Reflection-level guard so
        // accidental re-introduction is caught by this test.
        val propertyNames = OpenApiConfig::class.memberProperties.map { it.name }
        assertEquals(
            "OpenApiConfig should have only the outputFormat property, got: $propertyNames",
            listOf("outputFormat"),
            propertyNames,
        )
    }


    fun testBuildConfigJsonFormatWhenSet() {
        panel.setFormat(OpenApiOutputFormat.JSON)
        val config = panel.buildConfig() as OpenApiConfig
        assertEquals(OpenApiOutputFormat.JSON, config.outputFormat)
    }

    fun testBuildConfigYamlFormatWhenSet() {
        panel.setFormat(OpenApiOutputFormat.YAML)
        val config = panel.buildConfig() as OpenApiConfig
        assertEquals(OpenApiOutputFormat.YAML, config.outputFormat)
    }

    fun testBuildConfigAlwaysAskFallsBackToJson() {
        // The panel has no "Always Ask" radio. When applyConfig receives an
        // ALWAYS_ASK config (only reachable from OpenApiSettings), the panel
        // falls back to JSON (the default-of-defaults).
        panel.setFormat(OpenApiOutputFormat.ALWAYS_ASK)
        val config = panel.buildConfig() as OpenApiConfig
        assertEquals(
            "ALWAYS_ASK should fall back to JSON in the options panel",
            OpenApiOutputFormat.JSON,
            config.outputFormat,
        )
    }

    fun testApplyConfigPopulatesRadiosFromConfig() {
        val cfg = OpenApiConfig(outputFormat = OpenApiOutputFormat.YAML)
        panel.applyConfig(cfg)

        val built = panel.buildConfig() as OpenApiConfig
        assertEquals(OpenApiOutputFormat.YAML, built.outputFormat)
    }

    fun testApplyConfigWithDefaultsSelectsJson() {
        // First populate, then apply a default config — JSON should be selected.
        panel.setFormat(OpenApiOutputFormat.YAML)
        panel.applyConfig(OpenApiConfig())

        val built = panel.buildConfig() as OpenApiConfig
        assertEquals(
            "Default OpenApiConfig (ALWAYS_ASK) should select JSON in the panel",
            OpenApiOutputFormat.JSON,
            built.outputFormat,
        )
    }

    fun testRoundTripFromUItoConfigAndBack() {
        // Set UI → buildConfig → applyConfig → buildConfig → assert equality.
        panel.setFormat(OpenApiOutputFormat.YAML)

        val firstConfig = panel.buildConfig() as OpenApiConfig
        // Apply to a fresh panel and verify the second build matches the first.
        val freshPanel = OpenApiOptionsPanel(project)
        freshPanel.applyConfig(firstConfig)

        val secondConfig = freshPanel.buildConfig() as OpenApiConfig
        assertEquals(firstConfig, secondConfig)
    }

    fun testRoundTripBothFormats() {
        // Round-trip each panel-selectable format (JSON, YAML) through a fresh panel.
        for (format in listOf(OpenApiOutputFormat.JSON, OpenApiOutputFormat.YAML)) {
            panel.setFormat(format)
            val config = panel.buildConfig() as OpenApiConfig
            assertEquals(format, config.outputFormat)

            val freshPanel = OpenApiOptionsPanel(project)
            freshPanel.applyConfig(config)
            assertEquals(format, (freshPanel.buildConfig() as OpenApiConfig).outputFormat)
        }
    }

    fun testOnShownDoesNotThrow() {
        // The SPI default is a no-op; the panel may override it to initialize
        // defaults. Either way, it should not throw.
        panel.onShown()
    }
}
