package com.itangcent.easyapi.channel.hoppscotch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.itangcent.easyapi.core.logging.IdeaLog
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

/**
 * Dialog for selecting a Hoppscotch login method.
 *
 * Presents three options:
 * 1. **Magic Link (Recommended)** — email-based passwordless login
 * 2. **Browser Login** — JCEF browser-based login (disabled when JCEF unavailable)
 * 3. **Manual Token** — manual access token input
 *
 * @param project the IntelliJ project context
 * @param jcefAvailable whether JCEF browser is available on this platform
 * @see HoppscotchAuthService.login
 */
class HoppscotchLoginMethodDialog(
    private val project: Project,
    parent: Component?,
    private val jcefAvailable: Boolean
) : DialogWrapper(
    parent ?: com.intellij.openapi.wm.WindowManager.getInstance().suggestParentWindow(project)!!,
    parent != null
), IdeaLog {

    enum class Method { MAGIC_LINK, BROWSER, MANUAL_TOKEN }

    private val buttonGroup = ButtonGroup()
    private val magicLinkRadio = JRadioButton("Magic Link (Recommended)", true)
    private val browserRadio = JRadioButton("Browser Login")
    private val manualTokenRadio = JRadioButton("Manual Token")

    init {
        title = "Login to Hoppscotch"
        setOKButtonText("Continue")
        setCancelButtonText("Cancel")

        buttonGroup.add(magicLinkRadio)
        buttonGroup.add(browserRadio)
        buttonGroup.add(manualTokenRadio)

        magicLinkRadio.toolTipText = "Email-based passwordless login (supports both Cloud and Self-hosted)"

        if (!jcefAvailable) {
            browserRadio.isEnabled = false
            browserRadio.toolTipText = "JCEF browser is not available on this platform"
        }

        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(JLabel("Choose a login method:"))
        panel.add(Box.createVerticalStrut(10))
        panel.add(magicLinkRadio)
        panel.add(Box.createVerticalStrut(5))
        panel.add(browserRadio)
        panel.add(Box.createVerticalStrut(5))
        panel.add(manualTokenRadio)

        return panel
    }

    fun getSelectedMethod(): Method {
        return when {
            magicLinkRadio.isSelected -> Method.MAGIC_LINK
            browserRadio.isSelected -> Method.BROWSER
            manualTokenRadio.isSelected -> Method.MANUAL_TOKEN
            else -> Method.MAGIC_LINK
        }
    }

    companion object {
        /**
         * Shows the login method selection dialog and returns the chosen method,
         * or null if the user cancelled.
         */
        fun showDialog(project: Project, parent: Component?, jcefAvailable: Boolean): Method? {
            val dialog = HoppscotchLoginMethodDialog(project, parent, jcefAvailable)
            if (dialog.showAndGet()) {
                return dialog.getSelectedMethod()
            }
            return null
        }
    }
}
