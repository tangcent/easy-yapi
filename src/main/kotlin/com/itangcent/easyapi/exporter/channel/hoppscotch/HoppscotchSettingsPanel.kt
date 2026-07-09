package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.UIUtil
import com.itangcent.easyapi.exporter.channel.hoppscotch.HoppscotchAuthService
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.settings
import com.itangcent.easyapi.settings.update
import com.itangcent.easyapi.settings.ui.SettingsPanel
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * Settings panel for the Hoppscotch channel.
 *
 * Hoppscotch-specific fields (token, server URL, backend URL) are read from /
 * written to the unified [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState]
 * via [SettingBinder] — never touching state components directly.
 */
class HoppscotchSettingsPanel(private val project: Project) : SettingsPanel<Settings> {
    private val serverUrlField = JBTextField().apply {
        text = "https://hoppscotch.io"
        columns = 40
        toolTipText = "Hoppscotch server URL (use custom URL for self-hosted instances)"
    }
    private val backendUrlField = JBTextField().apply {
        columns = 40
        toolTipText = "Backend API URL for self-hosted instances (e.g., http://localhost:3170/v1). Leave empty for cloud (hoppscotch.io)."
    }
    private val loginButton = JButton("Login to Hoppscotch (Beta)").apply {
        toolTipText = "Open browser to login to Hoppscotch and capture access token (Beta feature)"
    }
    private val tokenStatusLabel = JLabel("Not logged in").apply {
        foreground = UIUtil.getInactiveTextColor()
    }
    private val logoutButton = JButton("Logout").apply {
        toolTipText = "Clear stored Hoppscotch access token"
        isEnabled = false
    }
    private val manualTokenField = JBPasswordField().apply {
        columns = 40
        toolTipText = "Manually enter Hoppscotch access token (fallback when browser login is not available)"
    }

    init {
        loginButton.addActionListener { performLogin() }
        logoutButton.addActionListener { performLogout() }
    }

    private fun performLogin() {
        loginButton.isEnabled = false
        loginButton.text = "Logging in..."
        val parentWindow = SwingUtilities.getWindowAncestor(loginButton)
        thread {
            try {
                val authService = HoppscotchAuthService.getInstance(project)
                val success = kotlinx.coroutines.runBlocking { authService.login(parentWindow) }
                SwingUtilities.invokeLater {
                    if (success) {
                        updateTokenStatus()
                        Messages.showInfoMessage(project, "Successfully logged in to Hoppscotch!", "Login Success")
                    } else {
                        Messages.showWarningDialog(project, "Login was cancelled or failed.", "Login Failed")
                    }
                    loginButton.isEnabled = true
                    loginButton.text = "Login to Hoppscotch (Beta)"
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, "Login failed: ${e.message}", "Login Error")
                    loginButton.isEnabled = true
                    loginButton.text = "Login to Hoppscotch (Beta)"
                }
            }
        }
    }

    private fun performLogout() {
        val authService = HoppscotchAuthService.getInstance(project)
        authService.logout()
        updateTokenStatus()
        Messages.showInfoMessage(project, "Logged out of Hoppscotch.", "Logout")
    }

    private fun updateTokenStatus() {
        val token = project.settings<HoppscotchSettings>().hoppscotchToken
        if (token.isNullOrBlank()) {
            tokenStatusLabel.text = "Not logged in"
            tokenStatusLabel.foreground = UIUtil.getInactiveTextColor()
            logoutButton.isEnabled = false
        } else {
            tokenStatusLabel.text = "Logged in (token: ${token.take(8)}...)"
            tokenStatusLabel.foreground = java.awt.Color(java.awt.Color.green.darker().rgb)
            logoutButton.isEnabled = true
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Server URL:", serverUrlField)
        .addLabeledComponent("Backend URL (self-hosted only):", backendUrlField)
        .addComponent(createLoginPanel())
        .addLabeledComponent("Manual Token (fallback):", manualTokenField)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    private fun createLoginPanel(): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                add(loginButton)
                add(logoutButton)
            }, BorderLayout.WEST)
            add(tokenStatusLabel, BorderLayout.CENTER)
        }
    }

    override fun resetFrom(settings: Settings?) {
        val s = project.settings<HoppscotchSettings>()
        serverUrlField.text = s.hoppscotchServerUrl ?: "https://hoppscotch.io"
        backendUrlField.text = s.hoppscotchBackendUrl ?: ""
        manualTokenField.text = s.hoppscotchToken ?: ""
        updateTokenStatus()
    }

    override fun applyTo(settings: Settings) {
        val manualToken = String(manualTokenField.password).takeIf { it.isNotBlank() }
        SettingBinder.getInstance(project).update(HoppscotchSettings::class) {
            hoppscotchServerUrl = serverUrlField.text.takeIf { it.isNotBlank() }
            hoppscotchBackendUrl = backendUrlField.text.takeIf { it.isNotBlank() }
            if (manualToken != null && hoppscotchToken.isNullOrBlank()) {
                hoppscotchToken = manualToken
            }
        }
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = project.settings<HoppscotchSettings>()
        return serverUrlField.text != (s.hoppscotchServerUrl ?: "https://hoppscotch.io") ||
                backendUrlField.text != (s.hoppscotchBackendUrl ?: "") ||
                (String(manualTokenField.password).takeIf { it.isNotBlank() } != s.hoppscotchToken && s.hoppscotchToken.isNullOrBlank())
    }

    companion object : IdeaLog
}
