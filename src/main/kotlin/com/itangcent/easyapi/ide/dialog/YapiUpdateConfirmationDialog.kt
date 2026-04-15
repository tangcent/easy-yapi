package com.itangcent.easyapi.ide.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.itangcent.easyapi.exporter.yapi.UpdateDecision
import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Dialog for confirming whether to update an existing API in YAPI.
 *
 * This dialog is shown when [com.itangcent.easyapi.exporter.yapi.YapiExportMode.ALWAYS_ASK]
 * mode is enabled and an existing API with the same path and method is found.
 *
 * The dialog displays:
 * - The API title, method, and path
 * - "Yes" and "No" buttons for the user's decision
 * - An "Apply for all" checkbox to apply the same decision to all remaining APIs
 *
 * @property doc The API document being exported
 * @property existingApiTitle The title of the existing API found in YAPI, may be null
 */
class YapiUpdateConfirmationDialog(
    project: Project?,
    private val doc: YapiApiDoc,
    private val existingApiTitle: String?
) : DialogWrapper(project) {

    /**
     * Checkbox allowing the user to apply the same decision to all remaining APIs.
     * When checked, the decision (Yes/No) will be remembered and applied automatically
     * to subsequent API exports in the same batch.
     */
    private val applyForAllCheckBox = JCheckBox("Apply for all").apply {
        toolTipText = "Apply this decision to all remaining APIs"
    }

    /**
     * Indicates whether the user selected "Apply for all".
     * This is set after the dialog is closed.
     */
    var shouldApplyForAll: Boolean = false
        private set

    /**
     * The user's decision after closing the dialog.
     * Can be [UpdateDecision.Update], [UpdateDecision.Skip], or [UpdateDecision.ApplyAll].
     */
    var userDecision: UpdateDecision = UpdateDecision.Skip
        private set

    init {
        title = "Update Existing API"
        setOKButtonText("Yes")
        setCancelButtonText("No")
        init()
    }

    override fun createCenterPanel(): JComponent? {
        val panel = JPanel(BorderLayout(10, 10))
        panel.preferredSize = Dimension(450, 120)

        val messagePanel = JPanel(BorderLayout())
        val messageLabel = JLabel(buildMessage())
        messageLabel.border = BorderFactory.createEmptyBorder(10, 0, 10, 0)
        messagePanel.add(messageLabel, BorderLayout.CENTER)

        panel.add(messagePanel, BorderLayout.CENTER)
        panel.add(applyForAllCheckBox, BorderLayout.SOUTH)

        return panel
    }

    /**
     * Builds the HTML message displayed in the dialog.
     * Shows the API title, method, and path for identification.
     */
    private fun buildMessage(): String {
        val apiTitle = existingApiTitle ?: doc.title ?: "Unknown API"
        val path = doc.path
        val method = doc.method.uppercase()
        return """<html>
            <div style='width: 400px;'>
                <b>Do you want to update the existing API?</b><br><br>
                <b>API:</b> $apiTitle<br>
                <b>Method:</b> $method<br>
                <b>Path:</b> $path
            </div>
        </html>""".trimIndent()
    }

    /**
     * Creates the "Yes" action button.
     * When clicked, sets the decision to [UpdateDecision.Update] or [UpdateDecision.ApplyAll]
     * depending on whether "Apply for all" is checked.
     */
    override fun getOKAction(): Action {
        return object : AbstractAction("Yes") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                shouldApplyForAll = applyForAllCheckBox.isSelected
                userDecision = if (shouldApplyForAll) {
                    UpdateDecision.ApplyAll(UpdateDecision.Update)
                } else {
                    UpdateDecision.Update
                }
                close(OK_EXIT_CODE)
            }
        }
    }

    /**
     * Creates the "No" action button.
     * When clicked, sets the decision to [UpdateDecision.Skip] or [UpdateDecision.ApplyAll]
     * depending on whether "Apply for all" is checked.
     */
    override fun getCancelAction(): Action {
        return object : AbstractAction("No") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                shouldApplyForAll = applyForAllCheckBox.isSelected
                userDecision = if (shouldApplyForAll) {
                    UpdateDecision.ApplyAll(UpdateDecision.Skip)
                } else {
                    UpdateDecision.Skip
                }
                close(CANCEL_EXIT_CODE)
            }
        }
    }

    /**
     * Handles the cancel action (e.g., pressing Escape or clicking the X button).
     * Treats it the same as clicking "No".
     */
    override fun doCancelAction() {
        shouldApplyForAll = applyForAllCheckBox.isSelected
        userDecision = if (shouldApplyForAll) {
            UpdateDecision.ApplyAll(UpdateDecision.Skip)
        } else {
            UpdateDecision.Skip
        }
        super.doCancelAction()
    }

    companion object {
        /**
         * Shows the update confirmation dialog and returns the user's decision.
         *
         * @param project The IntelliJ project context, may be null
         * @param doc The API document being exported
         * @param existingApiTitle The title of the existing API found in YAPI
         * @return The user's decision: [UpdateDecision.Update], [UpdateDecision.Skip], or [UpdateDecision.ApplyAll]
         */
        fun show(
            project: Project?,
            doc: YapiApiDoc,
            existingApiTitle: String?
        ): UpdateDecision {
            val dialog = YapiUpdateConfirmationDialog(project, doc, existingApiTitle)
            dialog.show()
            return dialog.userDecision
        }
    }
}
