package com.itangcent.easyapi.channel.apipost

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.itangcent.easyapi.channel.spi.ChannelOptionsPanel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Per-export options panel for the ApiPost channel.
 *
 * Exposes exactly the two values a user may want to override for one export:
 *
 *  - **token** — a temporary `api-token`. It is returned in [ApipostConfig] and
 *    used for this export only; it is **never** written to [ApipostSettings]
 *    (AC-4). Leaving it blank falls back to the persisted token.
 *  - **project id** — overrides the persisted target project. Blank falls back.
 *
 * There is no output-dir / file-name field: those come from
 * [ChannelConfig.FileConfig], which this channel's config cannot extend (P0-2),
 * and no export-mode field: every API is upserted individually now, so there is
 * nothing to choose.
 *
 * @param project the IntelliJ project context
 */
class ApipostOptionsPanel(@Suppress("UNUSED_PARAMETER") private val project: Project) : ChannelOptionsPanel {

    // Same cap as the settings panel: the value is a ~150-character JWT.
    private val tokenField = JBPasswordField().apply { columns = 30 }
    private val projectIdField = JBTextField()

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("ApiPost Token:", tokenField)
        .addLabeledComponent("Project ID:", projectIdField)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun buildConfig(): ApipostConfig = ApipostConfig(
        selectedToken = tokenText().takeIf { it.isNotBlank() },
        projectId = projectIdField.text?.trim()?.takeIf { it.isNotBlank() },
    )

    // --- Test-visible accessors ---

    internal fun tokenText(): String = String(tokenField.password)

    internal fun projectIdText(): String = projectIdField.text.orEmpty()

    internal fun setToken(value: String) {
        tokenField.text = value
    }

    internal fun setProjectId(value: String) {
        projectIdField.text = value
    }
}
