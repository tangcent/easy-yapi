package com.itangcent.easyapi.exporter.channel.curl

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.settings
import com.itangcent.easyapi.settings.update
import com.itangcent.easyapi.settings.ui.SettingsPanel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Settings panel for the cURL channel.
 *
 * Fields are read from / written to the unified
 * [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState] via
 * [SettingBinder] — never touching state components directly. Mirrors
 * [com.itangcent.easyapi.exporter.channel.hoppscotch.HoppscotchSettingsPanel]
 * (typed as `SettingsPanel<Settings>` so the configurable's
 * `ChannelPanelEntry.panel` cast works).
 *
 * The combo box items and ordering are derived from [CurlRenderMode] values +
 * their [CurlRenderMode.desc] (the YapiExportMode.desc pattern), so the enum is
 * the single source of truth for both the settings UI and stored value.
 *
 * Field semantics:
 *  - Variable resolution: when to resolve `{{var}}`/`${var}` placeholders against
 *    the active environment before formatting. See [CurlRenderMode.desc] for the
 *    per-option description shown in the combo.
 *  - Copy from edited endpoint: when on, the Dashboard "Copy as cURL" action
 *    uses the endpoint with dashboard UI edits applied (path, headers, params,
 *    body). When off, the original source-code endpoint is used.
 *  - 5 formatting flags: defaults for the [CurlFormatOptions] used at export
 *    time. The export dialog's options panel can override per-export.
 */
class CurlSettingsPanel(private val project: Project) : SettingsPanel<Settings> {

    /**
     * Combo entries — `[CurlRenderMode.desc]` strings in enum-declaration order.
     * Index ↔ enum mapping: 0=NEVER_RENDER, 1=ALWAYS_RENDER, 2=ALWAYS_ASK.
     */
    private val modeEntries: List<CurlRenderMode> = CurlRenderMode.entries

    private val renderModeCombo = ComboBox(modeEntries.map { it.desc }.toTypedArray()).apply {
        toolTipText = buildString {
            append("Controls when {{var}}/\${var} placeholders are resolved against the active environment. ")
            modeEntries.forEach { mode ->
                append("\"${mode.desc}\" — ")
                append(when (mode) {
                    CurlRenderMode.NEVER_RENDER -> "leave placeholders in the output. "
                    CurlRenderMode.ALWAYS_RENDER -> "resolve using the active environment without prompting. "
                    CurlRenderMode.ALWAYS_ASK -> "prompt to pick an environment at export time (default). "
                })
            }
        }
    }

    private val copyFromEditedCb = JBCheckBox("Copy as cURL uses dashboard-edited endpoint").apply {
        toolTipText = "When on, the Dashboard right-click \"Copy as cURL\" action uses the endpoint with " +
            "dashboard edits applied (path, headers, params, body from the UI fields). " +
            "When off (default), the original source-code endpoint is used."
    }

    private val includeCommentsCb = JBCheckBox("Include comments and section dividers").apply {
        toolTipText = "Wrap each endpoint in a `## name\\n```bash\\n...\\n``` block separated by `---` (default: on)"
    }
    private val prettyPrintBodyCb = JBCheckBox("Pretty-print JSON body (2-space indent)").apply {
        toolTipText = "Run JSON bodies through a pretty-printer (default: on, 2-space indent)"
    }
    private val multiLineFormatCb = JBCheckBox("Multi-line format (one flag per line with \\ continuation)").apply {
        toolTipText = "Join cURL parts with ` \\\\\\n  ` instead of a single space (default: off, single line)"
    }
    private val longFlagsCb = JBCheckBox("Use long flags (--header/--data/--request/--form)").apply {
        toolTipText = "Prefer --request/--header/--data/--form over -X/-H/-d/-F (default: off, short flags)"
    }
    private val includeResponseExampleCb = JBCheckBox("Append response body example as comment").apply {
        toolTipText = "Append a `# Response:` comment with the response body JSON (default: off). No-op for gRPC."
    }
    private val runPreScriptsCb = JBCheckBox("Run pre-request scripts (folder + API)").apply {
        toolTipText = "When on, folder- and class-level pre-request scripts are run before generating each " +
            "cURL command (export + Copy-as-cURL), so script-driven header injection / auth tokens / body " +
            "rewriting are reflected in the output. Default: off (no script machinery invoked, " +
            "byte-identical output). The export dialog can override per-export."
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Variable resolution:", renderModeCombo)
        .addComponent(copyFromEditedCb)
        .addComponent(JSeparator())
        .addComponent(includeCommentsCb)
        .addComponent(prettyPrintBodyCb)
        .addComponent(multiLineFormatCb)
        .addComponent(longFlagsCb)
        .addComponent(includeResponseExampleCb)
        .addComponent(JSeparator())
        .addComponent(runPreScriptsCb)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        val s = project.settings<CurlSettings>()
        renderModeCombo.selectedIndex = modeToIndex(s.renderModeEnum())
        copyFromEditedCb.isSelected = s.copyFromEdited
        includeCommentsCb.isSelected = s.includeComments
        prettyPrintBodyCb.isSelected = s.prettyPrintBody
        multiLineFormatCb.isSelected = s.multiLineFormat
        longFlagsCb.isSelected = s.longFlags
        includeResponseExampleCb.isSelected = s.includeResponseExample
        runPreScriptsCb.isSelected = s.runPreScripts
    }

    override fun applyTo(settings: Settings) {
        SettingBinder.getInstance(project).update(CurlSettings::class) {
            renderMode = indexToMode(renderModeCombo.selectedIndex).name
            copyFromEdited = copyFromEditedCb.isSelected
            includeComments = includeCommentsCb.isSelected
            prettyPrintBody = prettyPrintBodyCb.isSelected
            multiLineFormat = multiLineFormatCb.isSelected
            longFlags = longFlagsCb.isSelected
            includeResponseExample = includeResponseExampleCb.isSelected
            runPreScripts = runPreScriptsCb.isSelected
        }
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = project.settings<CurlSettings>()
        if (renderModeCombo.selectedIndex != modeToIndex(s.renderModeEnum())) return true
        if (copyFromEditedCb.isSelected != s.copyFromEdited) return true
        if (includeCommentsCb.isSelected != s.includeComments) return true
        if (prettyPrintBodyCb.isSelected != s.prettyPrintBody) return true
        if (multiLineFormatCb.isSelected != s.multiLineFormat) return true
        if (longFlagsCb.isSelected != s.longFlags) return true
        if (includeResponseExampleCb.isSelected != s.includeResponseExample) return true
        if (runPreScriptsCb.isSelected != s.runPreScripts) return true
        return false
    }

    private fun modeToIndex(mode: CurlRenderMode): Int = modeEntries.indexOf(mode).coerceAtLeast(0)

    private fun indexToMode(index: Int): CurlRenderMode = modeEntries.getOrNull(index) ?: CurlRenderMode.NEVER_RENDER
}
