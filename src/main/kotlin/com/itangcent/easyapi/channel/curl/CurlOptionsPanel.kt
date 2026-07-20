package com.itangcent.easyapi.channel.curl

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.itangcent.easyapi.channel.spi.ChannelOptionsPanel
import com.itangcent.easyapi.core.settings.settings
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * Per-export options panel for [CurlChannel].
 *
 * Mirrors the [com.itangcent.easyapi.channel.markdown.MarkdownOptionsPanel]
 * layout: file-output fields at top + channel-specific fields below. The 5
 * formatting checkboxes are seeded from [CurlSettings] in [onShown] so the
 * panel reflects the user's saved defaults; the user's per-export overrides
 * are what get applied.
 */
class CurlOptionsPanel(private val project: Project) : ChannelOptionsPanel {

    private val outputDirField = TextFieldWithBrowseButton().apply {
        text = project.basePath ?: ""
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Output Directory")
                .withDescription("Choose the directory to export the cURL script to")
        )
    }

    private val fileNameField = JBTextField().apply {
        text = "curl_export"
        columns = 30
    }

    private val includeCommentsCb = JBCheckBox("Include comments and section dividers").apply {
        toolTipText = "Wrap each endpoint in a `## name\\n```bash\\n...\\n``` block separated by `---` (default: on)"
    }
    private val prettyPrintBodyCb = JBCheckBox("Pretty-print JSON body (2-space indent)").apply {
        toolTipText = "Run JSON bodies through a pretty-printer (default: off, compact JSON)"
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
            "cURL command, so script-driven header injection / auth tokens / body rewriting are reflected " +
            "in the output. Default: off (no script machinery invoked, byte-identical output)."
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Output Directory:", outputDirField)
        .addLabeledComponent("File Name (without extension):", fileNameField)
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

    override fun onShown() {
        // Seed checkboxes from saved CurlSettings defaults so the panel reflects
        // what the user configured in Settings → EasyApi → Curl.
        val s = project.settings<CurlSettings>()
        includeCommentsCb.isSelected = s.includeComments
        prettyPrintBodyCb.isSelected = s.prettyPrintBody
        multiLineFormatCb.isSelected = s.multiLineFormat
        longFlagsCb.isSelected = s.longFlags
        includeResponseExampleCb.isSelected = s.includeResponseExample
        runPreScriptsCb.isSelected = s.runPreScripts
    }

    override fun buildConfig(): CurlConfig = CurlConfig(
        outputDir = outputDirField.text.takeIf { it.isNotBlank() },
        fileName = fileNameField.text.takeIf { it.isNotBlank() },
        options = CurlFormatOptions(
            includeComments = includeCommentsCb.isSelected,
            prettyPrintBody = prettyPrintBodyCb.isSelected,
            multiLineFormat = multiLineFormatCb.isSelected,
            longFlags = longFlagsCb.isSelected,
            includeResponseExample = includeResponseExampleCb.isSelected,
        ),
        runPreScripts = runPreScriptsCb.isSelected,
    )
}
