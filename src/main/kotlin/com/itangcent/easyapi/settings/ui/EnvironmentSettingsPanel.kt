package com.itangcent.easyapi.settings.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import com.itangcent.easyapi.dashboard.env.Environment
import com.itangcent.easyapi.dashboard.env.EnvironmentData
import com.itangcent.easyapi.dashboard.env.EnvironmentScope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.util.GsonUtils
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.table.DefaultTableModel

class EnvironmentSettingsPanel(private val project: Project) : SettingsPanel {

    private val envTableModel = ListTableModel<EnvironmentRow>(
        arrayOf(
            object : ColumnInfo<EnvironmentRow, String>("Name") {
                override fun valueOf(item: EnvironmentRow?): String? = item?.name
                override fun setValue(item: EnvironmentRow?, value: String?) {
                    item?.name = value ?: ""
                }

                override fun isCellEditable(item: EnvironmentRow?): Boolean = true
            },
            object : ColumnInfo<EnvironmentRow, String>("Scope") {
                override fun valueOf(item: EnvironmentRow?): String? = item?.scope?.label()
                override fun setValue(item: EnvironmentRow?, value: String?) {
                    item?.scope = when (value) {
                        "Global" -> EnvironmentScope.GLOBAL
                        else -> EnvironmentScope.PROJECT
                    }
                }

                override fun isCellEditable(item: EnvironmentRow?): Boolean = true
                override fun getColumnClass(): Class<*> = String::class.java
            },
            object : ColumnInfo<EnvironmentRow, String>("Variables") {
                override fun valueOf(item: EnvironmentRow?): String? {
                    if (item == null) return null
                    return item.variables.entries.joinToString(", ") { "${it.key}=${it.value}" }
                }

                override fun isCellEditable(item: EnvironmentRow?): Boolean = false
            }
        ),
        mutableListOf()
    )

    private val envTable = TableView(envTableModel)

    override val component: JComponent

    init {
        envTable.setShowGrid(false)
        envTable.intercellSpacing = Dimension(0, 0)
        envTable.columnModel.getColumn(0).preferredWidth = 150
        envTable.columnModel.getColumn(1).preferredWidth = 80
        envTable.columnModel.getColumn(2).preferredWidth = 300

        val scopeColumn = envTable.columnModel.getColumn(1)
        scopeColumn.cellEditor = DefaultCellEditor(JComboBox(arrayOf("Project", "Global")))

        val envPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Environments",
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
            val toolbarDecorator = ToolbarDecorator.createDecorator(envTable)
                .setAddAction {
                    showAddEnvironmentDialog()
                }
                .setEditAction {
                    val selected = envTable.selectedRow
                    if (selected >= 0) {
                        val row = envTableModel.getItem(selected)
                        showEditEnvironmentDialog(row)
                    }
                }
                .setRemoveAction {
                    val selected = envTable.selectedRow
                    if (selected >= 0) {
                        envTableModel.removeRow(selected)
                    }
                }
                .disableUpDownActions()
            add(toolbarDecorator.createPanel(), BorderLayout.CENTER)
        }

        component = FormBuilder.createFormBuilder()
            .addComponentFillVertically(envPanel, 0)
            .panel
    }

    private fun showAddEnvironmentDialog() {
        val dialog = EnvironmentDialog("Add Environment")
        if (dialog.showAndGet()) {
            envTableModel.addRow(EnvironmentRow(
                name = dialog.envName,
                scope = dialog.envScope,
                variables = dialog.envVariables
            ))
        }
    }

    private fun showEditEnvironmentDialog(row: EnvironmentRow) {
        val dialog = EnvironmentDialog("Edit Environment", row.name, row.scope, row.variables)
        if (dialog.showAndGet()) {
            row.name = dialog.envName
            row.scope = dialog.envScope
            row.variables = dialog.envVariables
            envTableModel.fireTableDataChanged()
        }
    }

    private class EnvironmentDialog(
        title: String,
        initialName: String = "",
        initialScope: EnvironmentScope = EnvironmentScope.PROJECT,
        initialVariables: Map<String, String> = emptyMap()
    ) : DialogWrapper(false) {

        var envName: String = initialName
        var envScope: EnvironmentScope = initialScope
        var envVariables: Map<String, String> = initialVariables

        private val nameField = JTextField(initialName, 20)
        private val scopeCombo = JComboBox(arrayOf("Project", "Global")).apply {
            selectedItem = when (initialScope) {
                EnvironmentScope.PROJECT -> "Project"
                EnvironmentScope.GLOBAL -> "Global"
            }
        }

        private val varTableModel = DefaultTableModel(arrayOf("Key", "Value"), 0)

        init {
            this.title = title
            initialVariables.forEach { (k, v) -> varTableModel.addRow(arrayOf(k, v)) }
            varTableModel.addRow(arrayOf("", ""))
            varTableModel.addTableModelListener { e ->
                if (e.type == javax.swing.event.TableModelEvent.UPDATE && e.firstRow == varTableModel.rowCount - 1) {
                    val key = varTableModel.getValueAt(varTableModel.rowCount - 1, 0)?.toString()?.trim().orEmpty()
                    val value = varTableModel.getValueAt(varTableModel.rowCount - 1, 1)?.toString()?.trim().orEmpty()
                    if (key.isNotEmpty() || value.isNotEmpty()) {
                        varTableModel.addRow(arrayOf("", ""))
                    }
                }
            }
            init()
        }

        override fun createCenterPanel(): JComponent {
            val varTable = JTable(varTableModel).apply {
                autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
                columnModel.getColumn(0).preferredWidth = 120
                columnModel.getColumn(1).preferredWidth = 200
            }

            val varPanel = JPanel(BorderLayout()).apply {
                border = BorderFactory.createTitledBorder("Variables")
                add(JScrollPane(varTable).apply {
                    preferredSize = Dimension(350, 150)
                }, BorderLayout.CENTER)
            }

            return JPanel(BorderLayout(4, 4)).apply {
                val topPanel = JPanel(GridLayout(0, 1, 4, 4)).apply {
                    add(JPanel(BorderLayout(4, 4)).apply {
                        add(JLabel("Name:"), BorderLayout.WEST)
                        add(nameField, BorderLayout.CENTER)
                    })
                    add(JPanel(BorderLayout(4, 4)).apply {
                        add(JLabel("Scope:"), BorderLayout.WEST)
                        add(scopeCombo, BorderLayout.CENTER)
                    })
                }
                add(topPanel, BorderLayout.NORTH)
                add(varPanel, BorderLayout.CENTER)
                preferredSize = Dimension(420, 320)
            }
        }

        override fun doOKAction() {
            val name = nameField.text.trim()
            if (name.isEmpty()) return
            envName = name
            envScope = when (scopeCombo.selectedItem) {
                "Global" -> EnvironmentScope.GLOBAL
                else -> EnvironmentScope.PROJECT
            }
            val vars = mutableMapOf<String, String>()
            for (i in 0 until varTableModel.rowCount) {
                val key = varTableModel.getValueAt(i, 0)?.toString()?.trim().orEmpty()
                val value = varTableModel.getValueAt(i, 1)?.toString()?.trim().orEmpty()
                if (key.isNotEmpty()) {
                    vars[key] = value
                }
            }
            envVariables = vars
            super.doOKAction()
        }
    }

    override fun resetFrom(settings: Settings?) {
        envTableModel.items = mutableListOf()

        val projectEnvJson = settings?.projectEnvironments
        if (!projectEnvJson.isNullOrBlank()) {
            val data = runCatching { GsonUtils.fromJson<EnvironmentData>(projectEnvJson) }.getOrNull()
            data?.environments?.forEach { env ->
                envTableModel.addRow(EnvironmentRow(
                    name = env.name,
                    scope = env.scope,
                    variables = env.variables
                ))
            }
        }

        val globalEnvJson = settings?.globalEnvironments
        if (!globalEnvJson.isNullOrBlank()) {
            val data = runCatching { GsonUtils.fromJson<EnvironmentData>(globalEnvJson) }.getOrNull()
            data?.environments?.forEach { env ->
                envTableModel.addRow(EnvironmentRow(
                    name = env.name,
                    scope = env.scope,
                    variables = env.variables
                ))
            }
        }
    }

    override fun applyTo(settings: Settings) {
        val projectEnvs = envTableModel.items.filter { it.scope == EnvironmentScope.PROJECT }
        val globalEnvs = envTableModel.items.filter { it.scope == EnvironmentScope.GLOBAL }

        settings.projectEnvironments = if (projectEnvs.isNotEmpty()) {
            GsonUtils.toJson(EnvironmentData(
                environments = projectEnvs.map { Environment(it.name, it.scope, it.variables) }
            ))
        } else ""

        settings.globalEnvironments = if (globalEnvs.isNotEmpty()) {
            GsonUtils.toJson(EnvironmentData(
                environments = globalEnvs.map { Environment(it.name, it.scope, it.variables) }
            ))
        } else ""
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        val currentProjectEnvs = envTableModel.items.filter { it.scope == EnvironmentScope.PROJECT }
        val currentGlobalEnvs = envTableModel.items.filter { it.scope == EnvironmentScope.GLOBAL }

        val currentProjectJson = if (currentProjectEnvs.isNotEmpty()) {
            GsonUtils.toJson(EnvironmentData(
                environments = currentProjectEnvs.map { Environment(it.name, it.scope, it.variables) }
            ))
        } else ""

        val currentGlobalJson = if (currentGlobalEnvs.isNotEmpty()) {
            GsonUtils.toJson(EnvironmentData(
                environments = currentGlobalEnvs.map { Environment(it.name, it.scope, it.variables) }
            ))
        } else ""

        return currentProjectJson != s.projectEnvironments || currentGlobalJson != s.globalEnvironments
    }

    private data class EnvironmentRow(
        var name: String,
        var scope: EnvironmentScope,
        var variables: Map<String, String>
    )
}
