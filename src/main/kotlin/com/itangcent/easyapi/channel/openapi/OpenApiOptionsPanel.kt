package com.itangcent.easyapi.channel.openapi

import com.intellij.openapi.project.Project
import com.intellij.util.ui.FormBuilder
import com.itangcent.easyapi.channel.spi.ChannelOptionsPanel
import com.itangcent.easyapi.core.logging.IdeaLog
import java.awt.FlowLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JRadioButton

/**
 * Per-export options panel for the OpenAPI channel.
 *
 * The v1 envelope fields (`infoTitle` / `infoVersion` / `infoDescription` /
 * `serverUrl`) were removed — those values vary per project and belong in
 * rule scripts. The options panel now exposes ONLY a two-way format
 * selector:
 *
 *  - **JSON** (radio, default-selected)
 *  - **YAML** (radio)
 *
 * The "Always Ask" option is NOT exposed here — the panel itself is the
 * per-export prompt, so offering "Always Ask" would be redundant (it would
 * just trigger a second `Messages.showDialog` inside `export()`).
 * "Always Ask" remains available in [OpenApiSettings] as the persistent
 * default: when the quick-export path is used (no options panel shown) and
 * the setting is `ALWAYS_ASK`, `OpenApiChannel.export` prompts the user at
 * export time.
 *
 * Mirrors the shape of
 * [com.itangcent.easyapi.channel.hoppscotch.HoppscotchOptionsPanel] and
 * [com.itangcent.easyapi.channel.curl.CurlOptionsPanel] (the latter uses
 * a similar radio group for render-mode selection, including an
 * `ALWAYS_ASK`-equivalent).
 *
 * @param project the IntelliJ project context (unused today but kept for
 *  future rule-context lookups; consistent with the ChannelOptionsPanel SPI).
 * @see OpenApiChannel
 * @see OpenApiConfig
 */
class OpenApiOptionsPanel(@Suppress("UNUSED_PARAMETER") private val project: Project) :
    ChannelOptionsPanel, IdeaLog {

    private val jsonRadio = JRadioButton("JSON", true)
    private val yamlRadio = JRadioButton("YAML", false)

    init {
        ButtonGroup().apply {
            add(jsonRadio)
            add(yamlRadio)
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent(
            "Format:",
            JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(jsonRadio)
                add(yamlRadio)
            },
        )
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun buildConfig(): OpenApiConfig = OpenApiConfig(
        outputFormat = if (yamlRadio.isSelected) OpenApiOutputFormat.YAML
        else OpenApiOutputFormat.JSON,
    )

    /**
     * Populates the radio selection from a pre-built [OpenApiConfig].
     *
     * Resets every radio so the panel reflects [cfg]. When [cfg.outputFormat]
     * is `ALWAYS_ASK` (only reachable from [OpenApiSettings] — the panel
     * itself never produces it), the panel falls back to JSON (the
     * default-of-defaults) since there is no "Always Ask" radio to select.
     */
    fun applyConfig(cfg: OpenApiConfig) {
        jsonRadio.isSelected = cfg.outputFormat != OpenApiOutputFormat.YAML
        yamlRadio.isSelected = cfg.outputFormat == OpenApiOutputFormat.YAML
    }

    /** Test-visible setter for the format radio. */
    internal fun setFormat(format: OpenApiOutputFormat) {
        applyConfig(OpenApiConfig(outputFormat = format))
    }
}
