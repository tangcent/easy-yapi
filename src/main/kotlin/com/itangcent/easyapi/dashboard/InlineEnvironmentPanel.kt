package com.itangcent.easyapi.dashboard

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.itangcent.easyapi.dashboard.env.Environment
import com.itangcent.easyapi.dashboard.env.EnvironmentScope
import com.itangcent.easyapi.dashboard.env.EnvironmentService
import com.itangcent.easyapi.settings.ui.EasyApiSettingsConfigurable
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableModel

class InlineEnvironmentPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val environmentService: EnvironmentService = EnvironmentService.getInstance(project)

    private val nameField = JTextField(16)
    private val scopeCombo = JComboBox(arrayOf("Project", "Global"))

    private val varTableModel = object : DefaultTableModel(arrayOf("Key", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = true
    }
    private val varTable = JBTable(varTableModel).apply {
        autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        columnModel.getColumn(0).preferredWidth = 120
        columnModel.getColumn(1).preferredWidth = 250
        setShowGrid(false)
        intercellSpacing = Dimension(0, 0)
    }

    private val saveButton = JButton("Save").apply {
        toolTipText = "Save changes to current environment"
        isEnabled = false
    }
    private val settingsLink = JButton("Settings...").apply {
        toolTipText = "Open full environment settings"
        isBorderPainted = false
        isContentAreaFilled = false
        foreground = UIManager.getColor("Link.activeForeground")
    }

    private var currentEnvName: String? = null
    private var isUpdatingUI: Boolean = false
    private var savedState: EnvSnapshot? = null

    var onEnvironmentSaved: (() -> Unit)? = null

    init {
        border = JBUI.Borders.empty(4, 8)
        buildUI()
        wireActions()
    }

    private fun buildUI() {
        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JBLabel("Environment:"))
            add(Box.createHorizontalStrut(4))
            add(nameField)
            add(Box.createHorizontalStrut(4))
            add(JBLabel("Scope:"))
            add(Box.createHorizontalStrut(4))
            add(scopeCombo)
            add(Box.createHorizontalGlue())
            add(saveButton)
            add(Box.createHorizontalStrut(4))
            add(settingsLink)
        }

        val varScrollPane = JBScrollPane(varTable).apply {
            preferredSize = Dimension(0, 120)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                    BorderFactory.createEtchedBorder(),
                    "Variables",
                    javax.swing.border.TitledBorder.LEFT,
                    javax.swing.border.TitledBorder.TOP
                ),
                JBUI.Borders.empty(2)
            )
        }

        add(headerPanel, BorderLayout.NORTH)
        add(varScrollPane, BorderLayout.CENTER)
    }

    private fun wireActions() {
        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = checkDirty()
            override fun removeUpdate(e: DocumentEvent?) = checkDirty()
            override fun changedUpdate(e: DocumentEvent?) = checkDirty()
        })

        scopeCombo.addActionListener { checkDirty() }

        varTableModel.addTableModelListener { e ->
            if (isUpdatingUI) return@addTableModelListener
            if (e.type == javax.swing.event.TableModelEvent.UPDATE &&
                e.firstRow == varTableModel.rowCount - 1
            ) {
                val key = varTableModel.getValueAt(varTableModel.rowCount - 1, 0)?.toString()?.trim().orEmpty()
                val value = varTableModel.getValueAt(varTableModel.rowCount - 1, 1)?.toString()?.trim().orEmpty()
                if (key.isNotEmpty() || value.isNotEmpty()) {
                    varTableModel.addRow(arrayOf("", ""))
                }
            }
            checkDirty()
        }

        saveButton.addActionListener { saveCurrentEnvironment() }
        settingsLink.addActionListener { openSettings() }
    }

    private fun checkDirty() {
        if (isUpdatingUI) return
        saveButton.isEnabled = isDirty()
    }

    private fun isDirty(): Boolean {
        val snap = savedState ?: return nameField.text.trim().isNotEmpty()
        if (nameField.text.trim() != snap.name) return true
        if (scopeCombo.selectedItem != snap.scopeLabel) return true
        val currentVars = collectVariables()
        return currentVars != snap.variables
    }

    fun loadActiveEnvironment() {
        isUpdatingUI = true
        try {
            val env = environmentService.getActiveEnvironment()
            if (env != null) {
                currentEnvName = env.name
                nameField.text = env.name
                scopeCombo.selectedItem = when (env.scope) {
                    EnvironmentScope.GLOBAL -> "Global"
                    EnvironmentScope.PROJECT -> "Project"
                }
                populateVariables(env.variables)
                savedState = EnvSnapshot(
                    name = env.name,
                    scopeLabel = when (env.scope) {
                        EnvironmentScope.GLOBAL -> "Global"
                        EnvironmentScope.PROJECT -> "Project"
                    },
                    variables = env.variables
                )
            } else {
                currentEnvName = null
                nameField.text = ""
                scopeCombo.selectedItem = "Project"
                populateVariables(emptyMap())
                savedState = null
            }
            saveButton.isEnabled = false
        } finally {
            isUpdatingUI = false
        }
    }

    private fun populateVariables(variables: Map<String, String>) {
        varTableModel.rowCount = 0
        variables.forEach { (k, v) ->
            varTableModel.addRow(arrayOf(k, v))
        }
        varTableModel.addRow(arrayOf("", ""))
    }

    private fun saveCurrentEnvironment() {
        val name = nameField.text.trim()
        if (name.isEmpty()) return

        val scope = when (scopeCombo.selectedItem) {
            "Global" -> EnvironmentScope.GLOBAL
            else -> EnvironmentScope.PROJECT
        }
        val variables = collectVariables()

        val env = Environment(name = name, scope = scope, variables = variables)

        val existingName = currentEnvName
        if (existingName != null && existingName != name) {
            environmentService.updateEnvironment(existingName, env)
        } else {
            environmentService.addEnvironment(env)
        }

        currentEnvName = name
        environmentService.setActiveEnvironment(name)
        savedState = EnvSnapshot(
            name = name,
            scopeLabel = scopeCombo.selectedItem as String,
            variables = variables
        )
        saveButton.isEnabled = false
        onEnvironmentSaved?.invoke()
    }

    private fun collectVariables(): Map<String, String> {
        val vars = mutableMapOf<String, String>()
        for (i in 0 until varTableModel.rowCount) {
            val key = varTableModel.getValueAt(i, 0)?.toString()?.trim().orEmpty()
            val value = varTableModel.getValueAt(i, 1)?.toString()?.trim().orEmpty()
            if (key.isNotEmpty()) {
                vars[key] = value
            }
        }
        return vars
    }

    private fun openSettings() {
        EasyApiSettingsConfigurable.selectTab(EasyApiSettingsConfigurable.TAB_ENVIRONMENT)
        com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            EasyApiSettingsConfigurable::class.java
        )
    }

    private data class EnvSnapshot(
        val name: String,
        val scopeLabel: String,
        val variables: Map<String, String>
    )
}
