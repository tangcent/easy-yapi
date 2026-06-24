package com.itangcent.easyapi.exporter.channel.yapi

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.ChannelOptionsPanel
import com.itangcent.easyapi.settings.settings
import com.itangcent.easyapi.ide.dialog.ExportDialogPreferences
import com.itangcent.easyapi.ide.dialog.ExportDialogPreferencesPersistence
import java.awt.BorderLayout
import java.awt.event.ItemEvent
import javax.swing.*

class YapiOptionsPanel(private val project: Project) : ChannelOptionsPanel {

    private data class YapiProject(val projectId: String, val token: String) {
        override fun toString(): String {
            val maskedToken = if (token.length > 8) token.take(4) + "..." + token.takeLast(4) else token
            return "$projectId ($maskedToken)"
        }
    }

    private val yapiProjects = mutableListOf<YapiProject>()
    private val yapiProjectComboBox = ComboBox<String>().apply { isEnabled = true }
    private val yapiNewTokenField = com.intellij.ui.components.JBTextField().apply { columns = 30 }
    private val yapiModeComboBox = ComboBox(arrayOf(YAPI_MODE_SELECT, YAPI_MODE_NEW_TOKEN)).apply {
        addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) updateYapiMode()
        }
    }
    private val yapiSelectPanel = JPanel(BorderLayout(8, 0))
    private val yapiNewTokenPanel = JPanel(BorderLayout(8, 0))

    override val component: JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JPanel(BorderLayout(8, 0)).apply {
            add(JLabel("Mode:"), BorderLayout.WEST)
            add(yapiModeComboBox, BorderLayout.CENTER)
        })
        add(Box.createVerticalStrut(5))
        add(yapiSelectPanel)
        add(yapiNewTokenPanel)
    }

    init {
        yapiSelectPanel.add(JLabel("Project:"), BorderLayout.WEST)
        yapiSelectPanel.add(yapiProjectComboBox, BorderLayout.CENTER)

        yapiNewTokenPanel.add(JLabel("Token:"), BorderLayout.WEST)
        yapiNewTokenPanel.add(yapiNewTokenField, BorderLayout.CENTER)

        loadYapiProjects()
        updateYapiMode()
    }

    override fun onShown() {}

    override fun buildConfig(): ChannelConfig.YapiConfig {
        val isNew = yapiModeComboBox.selectedItem == YAPI_MODE_NEW_TOKEN
        return if (isNew) {
            val token = yapiNewTokenField.text.trim()
            if (token.isNotBlank()) {
                ChannelConfig.YapiConfig(selectedToken = token, useCustomProject = true)
            } else {
                ChannelConfig.YapiConfig()
            }
        } else {
            val idx = yapiProjectComboBox.selectedIndex
            if (idx >= 0 && idx < yapiProjects.size) {
                val proj = yapiProjects[idx]
                ChannelConfig.YapiConfig(selectedToken = proj.token, useCustomProject = false)
            } else {
                ChannelConfig.YapiConfig()
            }
        }
    }

    private fun loadYapiProjects() {
        val settings = project.settings
        val yapiTokens = settings.yapiTokens
        if (!yapiTokens.isNullOrBlank()) {
            yapiTokens.lines()
                .map { it.trim() }
                .filter { it.contains("=") && !it.startsWith("#") }
                .forEach { line ->
                    val projectId = line.substringBefore("=").trim()
                    val token = line.substringAfter("=").trim()
                    if (projectId.isNotBlank() && token.isNotBlank()) {
                        yapiProjects.add(YapiProject(projectId, token))
                    }
                }
        }

        if (yapiProjects.isNotEmpty()) {
            yapiProjectComboBox.model = DefaultComboBoxModel(yapiProjects.map { it.toString() }.toTypedArray())

            val prefsPersistence = ExportDialogPreferencesPersistence(project)
            val savedYapiToken = prefsPersistence.load().lastYapiToken?.takeIf { it.isNotBlank() }
            val matchedIdx = if (savedYapiToken != null) {
                yapiProjects.indexOfFirst { it.token == savedYapiToken }
            } else -1

            if (matchedIdx >= 0) {
                yapiProjectComboBox.selectedIndex = matchedIdx
            } else {
                yapiProjectComboBox.selectedIndex = 0
            }
        } else {
            yapiModeComboBox.selectedItem = YAPI_MODE_NEW_TOKEN
            yapiModeComboBox.isEnabled = false
        }
    }

    private fun updateYapiMode() {
        val isNew = yapiModeComboBox.selectedItem == YAPI_MODE_NEW_TOKEN
        yapiSelectPanel.isVisible = !isNew
        yapiNewTokenPanel.isVisible = isNew
    }

    companion object {
        private const val YAPI_MODE_SELECT = "Select Existing Project"
        private const val YAPI_MODE_NEW_TOKEN = "Input New Token"
    }
}
