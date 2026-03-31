package com.itangcent.easyapi.settings.ui

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.itangcent.easyapi.settings.Settings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Utility object for validating settings input values.
 */
object ValidationUtils {
    /**
     * Validates a URL string.
     * 
     * @param text The URL to validate
     * @return true if valid URL or blank
     */
    fun validateUrl(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        return try {
            val uri = java.net.URI(text)
            uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates an integer string with optional range.
     * 
     * @param text The integer string to validate
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @return true if valid integer within range or blank
     */
    fun validateInteger(text: String?, min: Int? = null, max: Int? = null): Boolean {
        if (text.isNullOrBlank()) return true
        return try {
            val value = text.toInt()
            (min == null || value >= min) && (max == null || value <= max)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates a token string.
     * Requires minimum 8 characters.
     * 
     * @param text The token to validate
     * @return true if valid token or blank
     */
    fun validateToken(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        return text.length >= 8
    }
}

/**
 * Abstract base class for settings panels with validation support.
 * Provides validation error tracking and display.
 */
abstract class ValidatedPanel : JPanel(BorderLayout()), SettingsPanel {
    /** Map of components to their validation error messages */
    protected val validationErrors = mutableMapOf<JComponent, String>()
    
    /**
     * Adds a validation error for a component.
     */
    protected fun addValidationError(component: JComponent, error: String) {
        validationErrors[component] = error
        component.toolTipText = error
        component.background = java.awt.Color(255, 230, 230)
    }
    
    /**
     * Clears the validation error for a component.
     */
    protected fun clearValidationError(component: JComponent) {
        validationErrors.remove(component)
        component.toolTipText = null
        component.background = null
    }
    
    /**
     * Checks if any validation errors exist.
     */
    protected fun hasValidationErrors(): Boolean = validationErrors.isNotEmpty()
    
    /**
     * Returns all validation error messages.
     */
    protected fun getValidationErrors(): String = validationErrors.values.joinToString("\n")
}

/**
 * Enhanced general settings panel with validation.
 * 
 * Provides UI for:
 * - Built-in configuration toggle
 * - Postman export options
 * - YAPI server and token configuration
 */
class EnhancedGeneralSettingsPanel : ValidatedPanel() {
    private val builtInCheckbox = JCheckBox("Enable built-in configuration")
    private val postmanExampleCheckbox = JCheckBox("Build example in Postman export")
    private val postmanMergeScriptCheckbox = JCheckBox("Auto-merge scripts in Postman export")
    
    private val yapiServerField = JTextField(30)
    private val yapiTokenTextArea = JTextArea(4, 30)
    
    private val resetButton = JButton("Reset to Defaults")
    
    override val component: JComponent = this
    
    init {
        val mainPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 5, 5, 5)
        }
        
        var row = 0
        
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        mainPanel.add(createSectionHeader("General Settings"), gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.gridx = 0
        mainPanel.add(builtInCheckbox, gbc)
        
        row++
        gbc.gridy = row
        mainPanel.add(postmanExampleCheckbox, gbc)
        
        row++
        gbc.gridy = row
        mainPanel.add(postmanMergeScriptCheckbox, gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 2
        mainPanel.add(createSectionHeader("Yapi Settings"), gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.gridx = 0
        mainPanel.add(JBLabel("Yapi Server URL:"), gbc)
        gbc.gridx = 1
        mainPanel.add(yapiServerField, gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.gridx = 0
        gbc.anchor = GridBagConstraints.NORTHWEST
        mainPanel.add(JBLabel("tokens:"), gbc)
        gbc.gridx = 1
        gbc.fill = GridBagConstraints.BOTH
        gbc.weighty = 1.0
        mainPanel.add(JScrollPane(yapiTokenTextArea), gbc)
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.CENTER
        
        row++
        gbc.gridy = row
        gbc.gridx = 0
        gbc.gridwidth = 2
        mainPanel.add(createButtonPanel(), gbc)
        
        add(mainPanel, BorderLayout.CENTER)
        
        setupValidation()
        setupTooltips()
        setupResetButton()
    }
    
    private fun createSectionHeader(text: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size + 2f)
            border = BorderFactory.createEmptyBorder(10, 0, 5, 0)
        }
    }
    
    private fun createButtonPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(resetButton)
        }
    }
    
    private fun setupValidation() {
        yapiServerField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = validateYapiServer()
            override fun removeUpdate(e: DocumentEvent?) = validateYapiServer()
            override fun changedUpdate(e: DocumentEvent?) = validateYapiServer()
        })
    }
    
    private fun validateYapiServer() {
        val text = yapiServerField.text
        if (text.isNotBlank() && !ValidationUtils.validateUrl(text)) {
            addValidationError(yapiServerField, "Invalid URL format. Must be http:// or https://")
        } else {
            clearValidationError(yapiServerField)
        }
    }
    
    private fun setupTooltips() {
        builtInCheckbox.toolTipText = "Enable built-in configuration rules for common frameworks"
        postmanExampleCheckbox.toolTipText = "Generate example responses in Postman collections"
        postmanMergeScriptCheckbox.toolTipText = "Automatically merge pre-request and test scripts"
        yapiServerField.toolTipText = "Yapi server URL (e.g., http://yapi.example.com)"
        yapiTokenTextArea.toolTipText = "Yapi tokens in properties format: module=token (one per line). Tokens can also be entered at export time."
    }
    
    private fun setupResetButton() {
        resetButton.addActionListener {
            resetToDefaults()
        }
    }
    
    private fun resetToDefaults() {
        builtInCheckbox.isSelected = true
        postmanExampleCheckbox.isSelected = false
        postmanMergeScriptCheckbox.isSelected = true
        yapiServerField.text = ""
        yapiTokenTextArea.text = ""
    }
    
    override fun resetFrom(settings: Settings?) {
        builtInCheckbox.isSelected = true
        postmanExampleCheckbox.isSelected = settings?.postmanBuildExample ?: false
        postmanMergeScriptCheckbox.isSelected = settings?.autoMergeScript ?: true
        yapiServerField.text = settings?.yapiServer ?: ""
        yapiTokenTextArea.text = settings?.yapiTokens ?: ""
    }
    
    override fun applyTo(settings: Settings) {
        if (hasValidationErrors()) {
            throw IllegalArgumentException("Validation errors:\n${getValidationErrors()}")
        }
        
        settings.postmanBuildExample = postmanExampleCheckbox.isSelected
        settings.autoMergeScript = postmanMergeScriptCheckbox.isSelected
        settings.yapiServer = yapiServerField.text.takeIf { it.isNotBlank() }
        settings.yapiTokens = yapiTokenTextArea.text.takeIf { it.isNotBlank() }
    }
    
    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return postmanExampleCheckbox.isSelected != s.postmanBuildExample ||
            postmanMergeScriptCheckbox.isSelected != s.autoMergeScript ||
            yapiServerField.text != (s.yapiServer ?: "") ||
            yapiTokenTextArea.text != (s.yapiTokens ?: "")
    }
}

class EnhancedOtherSettingsPanel : ValidatedPanel() {
    private val charsetField = JTextField(20)
    private val unsafeSslCheckbox = JCheckBox("Allow unsafe SSL connections")
    private val httpTimeoutField = JTextField(10)
    
    private val resetButton = JButton("Reset to Defaults")
    
    override val component: JComponent = this
    
    init {
        val mainPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }
        
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(5, 5, 5, 5)
        }
        
        var row = 0
        
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        mainPanel.add(createSectionHeader("Output Settings"), gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        gbc.gridx = 0
        mainPanel.add(JBLabel("Output Charset:"), gbc)
        gbc.gridx = 1
        mainPanel.add(charsetField, gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 2
        mainPanel.add(createSectionHeader("HTTP Settings"), gbc)
        
        row++
        gbc.gridy = row
        gbc.gridwidth = 1
        mainPanel.add(unsafeSslCheckbox, gbc)
        
        row++
        gbc.gridy = row
        gbc.gridx = 0
        mainPanel.add(JBLabel("HTTP Timeout (ms):"), gbc)
        gbc.gridx = 1
        mainPanel.add(httpTimeoutField, gbc)
        
        row++
        gbc.gridy = row
        gbc.gridx = 0
        gbc.gridwidth = 2
        mainPanel.add(createButtonPanel(), gbc)
        
        add(mainPanel, BorderLayout.CENTER)
        
        setupValidation()
        setupTooltips()
        setupResetButton()
    }
    
    private fun createSectionHeader(text: String): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(java.awt.Font.BOLD, font.size + 2f)
            border = BorderFactory.createEmptyBorder(10, 0, 5, 0)
        }
    }
    
    private fun createButtonPanel(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(resetButton)
        }
    }
    
    private fun setupValidation() {
        httpTimeoutField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = validateTimeout()
            override fun removeUpdate(e: DocumentEvent?) = validateTimeout()
            override fun changedUpdate(e: DocumentEvent?) = validateTimeout()
        })
    }
    
    private fun validateTimeout() {
        val text = httpTimeoutField.text
        if (text.isNotBlank() && !ValidationUtils.validateInteger(text, 1000, 600_000)) {
            addValidationError(httpTimeoutField, "Timeout must be between 1000 and 600000 ms")
        } else {
            clearValidationError(httpTimeoutField)
        }
    }
    
    private fun setupTooltips() {
        charsetField.toolTipText = "Character encoding for output files (e.g., UTF-8, ISO-8859-1)"
        unsafeSslCheckbox.toolTipText = "Allow connections to servers with self-signed certificates"
        httpTimeoutField.toolTipText = "HTTP request timeout in milliseconds (1000-600000)"
    }
    
    private fun setupResetButton() {
        resetButton.addActionListener {
            resetToDefaults()
        }
    }
    
    private fun resetToDefaults() {
        charsetField.text = "UTF-8"
        unsafeSslCheckbox.isSelected = false
        httpTimeoutField.text = "30000"
    }
    
    override fun resetFrom(settings: Settings?) {
        charsetField.text = settings?.outputCharset ?: "UTF-8"
        unsafeSslCheckbox.isSelected = settings?.unsafeSsl ?: false
        httpTimeoutField.text = (settings?.httpTimeOut ?: 30_000).toString()
    }
    
    override fun applyTo(settings: Settings) {
        if (hasValidationErrors()) {
            throw IllegalArgumentException("Validation errors:\n${getValidationErrors()}")
        }
        
        settings.outputCharset = charsetField.text.ifBlank { "UTF-8" }
        settings.unsafeSsl = unsafeSslCheckbox.isSelected
        settings.httpTimeOut = httpTimeoutField.text.toIntOrNull() ?: 30_000
    }
    
    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        return charsetField.text != s.outputCharset ||
            unsafeSslCheckbox.isSelected != s.unsafeSsl ||
            httpTimeoutField.text != s.httpTimeOut.toString()
    }
}
