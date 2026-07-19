package com.itangcent.easyapi.channel.hoppscotch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.itangcent.easyapi.core.logging.IdeaLog
import com.intellij.openapi.components.service
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import javax.swing.*

/**
 * Multi-step dialog for Hoppscotch Magic Link authentication.
 *
 * Guides the user through three steps:
 * 1. **EMAIL_INPUT** — Enter email address
 * 2. **SENDING_LINK** — Progress indicator while sending the magic link request
 * 3. **LINK_INPUT** — Paste the magic link URL from the email
 *
 * Supports both self-hosted and cloud (Firebase) authentication:
 * - Self-hosted: magic link contains a `token` parameter
 * - Cloud (Firebase): magic link contains an `oobCode` parameter
 *
 * On success, [getAccessToken] and [getRefreshToken] return the extracted tokens.
 *
 * @param project the IntelliJ project context
 * @param isCloud whether this is a cloud (Firebase) login or self-hosted
 * @see HoppscotchAuthService.loginWithMagicLink
 */
class HoppscotchMagicLinkLoginDialog(
    private val project: Project,
    parent: java.awt.Component? = null,
    private val isCloud: Boolean = false
) : DialogWrapper(
    parent ?: com.intellij.openapi.wm.WindowManager.getInstance().suggestParentWindow(project)!!,
    parent != null
), IdeaLog {

    private enum class Step { EMAIL_INPUT, SENDING_LINK, LINK_INPUT }

    private var currentStep = Step.EMAIL_INPUT

    private val emailField = JTextField().apply { columns = 30 }
    private val linkUrlField = JTextField().apply { columns = 50 }
    private val emailLabel = JLabel("Email address:")
    private val statusLabel = JLabel(" ")
    private val progressLabel = JLabel("Sending magic link...")

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var deviceIdentifier: String? = null
    private var userEmail: String? = null

    private val cardLayout = CardLayout()
    private val cardPanel = JPanel(cardLayout)

    init {
        title = "Login to Hoppscotch — Magic Link"
        setOKButtonText("Send Magic Link")
        setCancelButtonText("Cancel")
        init()
    }

    override fun createCenterPanel(): JComponent {
        // Step 1: Email input
        val emailPanel = JPanel().apply {
            layout = BorderLayout()
            add(JPanel(BorderLayout()).apply {
                add(emailLabel, BorderLayout.WEST)
                add(emailField, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(statusLabel, BorderLayout.SOUTH)
        }

        // Step 2: Sending link progress
        val sendingPanel = JPanel().apply {
            layout = BorderLayout()
            add(progressLabel, BorderLayout.CENTER)
        }

        // Step 3: Link URL input
        val linkPanel = JPanel().apply {
            layout = BorderLayout()
            val infoLabel = JLabel()
            add(infoLabel, BorderLayout.NORTH)
            add(JPanel(BorderLayout()).apply {
                add(JLabel("Magic link URL:"), BorderLayout.WEST)
                add(linkUrlField, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
            add(statusLabel, BorderLayout.SOUTH)
        }

        cardPanel.add(emailPanel, Step.EMAIL_INPUT.name)
        cardPanel.add(sendingPanel, Step.SENDING_LINK.name)
        cardPanel.add(linkPanel, Step.LINK_INPUT.name)

        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(500, 150)
        panel.add(cardPanel, BorderLayout.CENTER)
        return panel
    }

    /**
     * Transitions to the "sending link" step.
     * Called by [HoppscotchAuthService] after the user submits their email.
     */
    fun showSendingStep() {
        currentStep = Step.SENDING_LINK
        cardLayout.show(cardPanel, Step.SENDING_LINK.name)
        isOKActionEnabled = false
    }

    /**
     * Transitions to the "link input" step after the magic link was sent successfully.
     *
     * @param deviceIdentifier the device identifier from the signin response
     * @param email the email address the magic link was sent to
     */
    fun showLinkInputStep(deviceIdentifier: String, email: String) {
        this.deviceIdentifier = deviceIdentifier
        this.userEmail = email
        currentStep = Step.LINK_INPUT
        cardLayout.show(cardPanel, Step.LINK_INPUT.name)
        setOKButtonText("Sign In")
        isOKActionEnabled = true
        statusLabel.text = " "
    }

    /**
     * Shows an error and returns to the email input step.
     *
     * @param message the error message to display
     */
    fun showEmailError(message: String) {
        currentStep = Step.EMAIL_INPUT
        cardLayout.show(cardPanel, Step.EMAIL_INPUT.name)
        setOKButtonText("Send Magic Link")
        isOKActionEnabled = true
        statusLabel.text = message
    }

    /**
     * Shows an error on the link input step.
     *
     * @param message the error message to display
     */
    fun showLinkError(message: String) {
        statusLabel.text = message
        isOKActionEnabled = true
    }

    /**
     * Called when login succeeds — stores tokens and closes the dialog.
     */
    fun onLoginSuccess(accessToken: String?, refreshToken: String?) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        close(OK_EXIT_CODE)
    }

    fun getAccessToken(): String? = accessToken
    fun getRefreshToken(): String? = refreshToken
    fun getDeviceIdentifier(): String? = deviceIdentifier
    fun getEmail(): String = emailField.text.trim()
    fun getLinkUrl(): String = linkUrlField.text.trim()

    override fun doOKAction() {
        when (currentStep) {
            Step.EMAIL_INPUT -> {
                val email = emailField.text.trim()
                if (email.isBlank()) {
                    statusLabel.text = "Please enter your email address."
                    return
                }
                if (!isValidEmail(email)) {
                    statusLabel.text = "Please enter a valid email address."
                    return
                }
                // Notify the service to send the magic link
                onEmailSubmitted?.invoke(email)
            }
            Step.LINK_INPUT -> {
                val url = linkUrlField.text.trim()
                if (url.isBlank()) {
                    statusLabel.text = "Please paste the magic link URL from your email."
                    return
                }
                // Validate the URL contains the expected parameter
                val hasValidParam = if (isCloud) {
                    extractOobCodeFromUrl(url) != null
                } else {
                    extractTokenFromUrl(url) != null
                }
                if (!hasValidParam) {
                    if (isCloud) {
                        statusLabel.text = "Invalid magic link URL. Please paste the full URL from the email (should contain oobCode parameter)."
                    } else {
                        statusLabel.text = "Invalid magic link URL. Please paste the full URL from the email (should contain token parameter)."
                    }
                    return
                }
                // Pass the full URL to the service, which will extract the appropriate parameter
                onLinkSubmitted?.invoke(url)
            }
            Step.SENDING_LINK -> {
                // No action during sending
            }
        }
    }

    /**
     * Callback invoked when the user submits their email.
     * Set by [HoppscotchAuthService] to handle the API call.
     */
    var onEmailSubmitted: ((String) -> Unit)? = null

    /**
     * Callback invoked when the user submits the magic link URL.
     * Set by [HoppscotchAuthService] to handle the verification API call.
     * The parameter is the full URL from the email.
     */
    var onLinkSubmitted: ((String) -> Unit)? = null

    companion object {
        private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)

        /**
         * Extracts the `token` query parameter from a self-hosted magic link URL.
         *
         * @param url the full URL from the email, e.g. "https://example.com/enter?token=abc123"
         * @return the token value, or null if not found
         */
        fun extractTokenFromUrl(url: String): String? {
            val match = Regex("[?&]token=([^&]+)").find(url)
            return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }

        /**
         * Extracts the `oobCode` query parameter from a Firebase magic link URL.
         *
         * @param url the full URL from the email, e.g. "https://hoppscotch.io/enter?oobCode=XXX&..."
         * @return the oobCode value, or null if not found
         */
        fun extractOobCodeFromUrl(url: String): String? {
            val match = Regex("[?&]oobCode=([^&]+)").find(url)
            return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }
}
