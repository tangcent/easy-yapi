package com.itangcent.easyapi.settings.ui

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.ListTableModel
import com.itangcent.easyapi.grpc.*
import com.itangcent.easyapi.settings.Settings
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.*
import javax.swing.border.TitledBorder

class GrpcSettingsPanel(private val project: com.intellij.openapi.project.Project) : SettingsPanel {

    private val grpcEnableCheckbox = JCheckBox("Enable gRPC support", true)
    private val grpcCallEnabledCheckbox = JCheckBox("Enable gRPC call", false)
    private val autoDetectButton = JButton("Auto Detect")

    private val runtimeResolver: GrpcRuntimeResolver by lazy {
        GrpcRuntimeResolver.getInstance(project)
    }

    private val artifactTableModel = ListTableModel<GrpcArtifactConfig>(
        arrayOf(
            object : ColumnInfo<GrpcArtifactConfig, String>("Artifact") {
                override fun valueOf(item: GrpcArtifactConfig?): String? = item?.artifact?.coordinate
            },
            object : ColumnInfo<GrpcArtifactConfig, String>("Version") {
                override fun valueOf(item: GrpcArtifactConfig?): String? {
                    if (item == null) return null
                    val version = item.resolvedVersion ?: return when (item.versionMode) {
                        ArtifactVersionMode.LATEST -> "latest"
                        ArtifactVersionMode.FIXED -> item.fixedVersion ?: "latest"
                    }
                    return when (item.versionMode) {
                        ArtifactVersionMode.LATEST -> "$version (latest)"
                        ArtifactVersionMode.FIXED -> "${item.fixedVersion ?: version} (fixed)"
                    }
                }
            },
            object : ColumnInfo<GrpcArtifactConfig, Boolean>("Enable") {
                override fun valueOf(item: GrpcArtifactConfig?): Boolean = item?.enabled ?: true
                override fun getColumnClass(): Class<*> = java.lang.Boolean::class.java
                override fun isCellEditable(item: GrpcArtifactConfig?): Boolean = true
                override fun setValue(item: GrpcArtifactConfig?, value: Boolean) {
                    item?.enabled = value
                }
            }
        ),
        mutableListOf()
    )

    private val artifactTable = TableView(artifactTableModel)

    private val additionalJarsModel = DefaultListModel<String>()
    private val additionalJarsList = JList(additionalJarsModel)

    private var runtimePackagesPanel: JPanel? = null
    private var isLoadingSettings = false

    override val component: JComponent

    init {
        grpcCallEnabledCheckbox.addItemListener {
            updateRuntimePackagesVisibility()
            if (!isLoadingSettings && grpcCallEnabledCheckbox.isSelected && artifactTableModel.items.isEmpty()) {
                autoDetectRuntime()
            }
        }

        autoDetectButton.addActionListener {
            autoDetectRuntime()
        }

        artifactTable.setShowGrid(false)
        artifactTable.intercellSpacing = Dimension(0, 0)
        artifactTable.columnModel.getColumn(0).preferredWidth = 280
        artifactTable.columnModel.getColumn(1).preferredWidth = 150
        artifactTable.columnModel.getColumn(2).preferredWidth = 60

        val artifactPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Runtime Packages",
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
            val toolbarDecorator = ToolbarDecorator.createDecorator(artifactTable)
                .setAddAction {
                    showAddArtifactDialog()
                }
                .setRemoveAction {
                    val selected = artifactTable.selectedRow
                    if (selected >= 0) {
                        artifactTableModel.removeRow(selected)
                    }
                }
                .setEditAction {
                    val selected = artifactTable.selectedRow
                    if (selected >= 0) {
                        val config = artifactTableModel.getItem(selected)
                        showEditArtifactDialog(config)
                    }
                }
                .disableUpDownActions()
            add(toolbarDecorator.createPanel(), BorderLayout.CENTER)
        }

        val additionalJarsPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "Additional JARs",
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
            val toolbarDecorator = ToolbarDecorator.createDecorator(additionalJarsList)
                .setAddAction {
                    showAddJarDialog()
                }
                .setRemoveAction {
                    val selected = additionalJarsList.selectedIndex
                    if (selected >= 0) {
                        additionalJarsModel.remove(selected)
                    }
                }
                .disableUpDownActions()
            add(toolbarDecorator.createPanel(), BorderLayout.CENTER)
        }

        runtimePackagesPanel = JPanel(GridLayout(0, 1, 0, 8)).apply {
            add(artifactPanel)
            add(additionalJarsPanel)
        }

        val grpcRuntimePanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                "gRPC Runtime",
                TitledBorder.LEFT,
                TitledBorder.TOP
            )
            val callRow = JPanel(GridLayout(1, 2, 8, 0)).apply {
                add(grpcCallEnabledCheckbox)
                add(autoDetectButton)
            }
            add(callRow, BorderLayout.NORTH)
            add(runtimePackagesPanel, BorderLayout.CENTER)
        }

        component = FormBuilder.createFormBuilder()
            .addComponent(grpcEnableCheckbox)
            .addComponent(grpcRuntimePanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        updateRuntimePackagesVisibility()
    }

    private fun updateRuntimePackagesVisibility() {
        runtimePackagesPanel?.isVisible = grpcCallEnabledCheckbox.isSelected
    }

    private fun autoDetectRuntime() {
        autoDetectButton.isEnabled = false
        autoDetectButton.text = "Detecting..."

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Detecting gRPC Runtime", true) {
            override fun run(indicator: ProgressIndicator) {
                val resolvedVersions = runtimeResolver.resolveVersions()
                val resolved = runtimeResolver.resolve()
                SwingUtilities.invokeLater {
                    autoDetectButton.isEnabled = true
                    autoDetectButton.text = "Auto Detect"

                    val configs = GrpcRequiredArtifacts.defaultConfigs()
                    for (config in configs) {
                        config.resolvedVersion = resolvedVersions[config.artifact.coordinate]
                    }
                    artifactTableModel.items = configs.toMutableList()

                    if (resolved != null) {
                        JOptionPane.showMessageDialog(
                            component,
                            "gRPC runtime detected successfully.\nVersion: ${resolved.version}",
                            "Detection Complete",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    } else {
                        JOptionPane.showMessageDialog(
                            component,
                            "gRPC runtime not found in project.\nDefault packages have been added.\nYou may need to download them manually.",
                            "Detection Result",
                            JOptionPane.WARNING_MESSAGE
                        )
                    }
                    updateRuntimePackagesVisibility()
                }
            }
        })
    }

    private fun showAddArtifactDialog() {
        val dialog = AddArtifactDialog()
        if (dialog.showAndGet()) {
            artifactTableModel.addRow(dialog.config)
        }
    }

    private fun showEditArtifactDialog(config: GrpcArtifactConfig) {
        val dialog = EditArtifactDialog(config)
        if (dialog.showAndGet()) {
            artifactTableModel.fireTableDataChanged()
        }
    }

    private inner class AddArtifactDialog : DialogWrapper(false) {
        private val groupIdField = JTextField(20)
        private val artifactIdField = JTextField(20)
        private val versionModeCombo = JComboBox(arrayOf("Latest", "Fixed"))
        private val fixedVersionField = JTextField(10)

        lateinit var config: GrpcArtifactConfig

        init {
            title = "Add Artifact"
            fixedVersionField.isEnabled = false
            versionModeCombo.addActionListener {
                fixedVersionField.isEnabled = versionModeCombo.selectedItem == "Fixed"
            }
            init()
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(GridLayout(0, 2, 4, 4)).apply {
                add(JLabel("Group ID:"))
                add(groupIdField)
                add(JLabel("Artifact ID:"))
                add(artifactIdField)
                add(JLabel("Version Mode:"))
                add(versionModeCombo)
                add(JLabel("Fixed Version:"))
                add(fixedVersionField)
                preferredSize = Dimension(350, preferredSize.height)
            }
        }

        override fun doOKAction() {
            val groupId = groupIdField.text.trim()
            val artifactId = artifactIdField.text.trim()
            if (groupId.isEmpty() || artifactId.isEmpty()) {
                return
            }
            val versionMode = if (versionModeCombo.selectedItem == "Fixed") {
                ArtifactVersionMode.FIXED
            } else {
                ArtifactVersionMode.LATEST
            }
            val fixedVersion = fixedVersionField.text.trim().takeIf { it.isNotEmpty() && versionMode == ArtifactVersionMode.FIXED }
            config = GrpcArtifactConfig(
                Artifact(groupId, artifactId),
                versionMode,
                fixedVersion
            )
            super.doOKAction()
        }
    }

    private inner class EditArtifactDialog(private val config: GrpcArtifactConfig) : DialogWrapper(false) {
        private val versionModeCombo = JComboBox(arrayOf("Latest", "Fixed"))
        private val fixedVersionField = JTextField(10)

        init {
            title = "Edit Artifact: ${config.artifact.coordinate}"
            versionModeCombo.selectedItem = when (config.versionMode) {
                ArtifactVersionMode.LATEST -> "Latest"
                ArtifactVersionMode.FIXED -> "Fixed"
            }
            fixedVersionField.text = config.fixedVersion ?: ""
            fixedVersionField.isEnabled = config.versionMode == ArtifactVersionMode.FIXED
            versionModeCombo.addActionListener {
                fixedVersionField.isEnabled = versionModeCombo.selectedItem == "Fixed"
            }
            init()
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(GridLayout(0, 2, 4, 4)).apply {
                add(JLabel("Artifact:"))
                add(JLabel(config.artifact.coordinate))
                add(JLabel("Version Mode:"))
                add(versionModeCombo)
                add(JLabel("Fixed Version:"))
                add(fixedVersionField)
                preferredSize = Dimension(350, preferredSize.height)
            }
        }

        override fun doOKAction() {
            val versionMode = if (versionModeCombo.selectedItem == "Fixed") {
                ArtifactVersionMode.FIXED
            } else {
                ArtifactVersionMode.LATEST
            }
            val fixedVersion = fixedVersionField.text.trim().takeIf { it.isNotEmpty() && versionMode == ArtifactVersionMode.FIXED }
            config.versionMode = versionMode
            config.fixedVersion = fixedVersion
            super.doOKAction()
        }
    }

    private fun showAddJarDialog() {
        val fileChooser = JFileChooser()
        fileChooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("JAR Files (*.jar)", "jar")
        fileChooser.isMultiSelectionEnabled = true

        val result = fileChooser.showOpenDialog(component)
        if (result == JFileChooser.APPROVE_OPTION) {
            for (file in fileChooser.selectedFiles) {
                val path = file.absolutePath
                if (!additionalJarsModel.contains(path)) {
                    additionalJarsModel.addElement(path)
                }
            }
        }
    }

    override fun resetFrom(settings: Settings?) {
        isLoadingSettings = true
        try {
            grpcEnableCheckbox.isSelected = settings?.grpcEnable ?: true
            grpcCallEnabledCheckbox.isSelected = settings?.grpcCallEnabled ?: false

            val userConfigs = settings?.grpcArtifactConfigs?.mapNotNull { GrpcArtifactConfig.parse(it) }
            if (!userConfigs.isNullOrEmpty()) {
                artifactTableModel.items = GrpcRequiredArtifacts.mergeWithDefaults(userConfigs).toMutableList()
            } else {
                artifactTableModel.items = mutableListOf()
            }

            additionalJarsModel.clear()
            settings?.grpcAdditionalJars?.forEach { additionalJarsModel.addElement(it) }

            updateRuntimePackagesVisibility()

            if (grpcCallEnabledCheckbox.isSelected && artifactTableModel.items.isNotEmpty()) {
                resolveVersionsInBackground()
            }
        } finally {
            isLoadingSettings = false
        }
    }

    private fun resolveVersionsInBackground() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Resolving Artifact Versions", true) {
            override fun run(indicator: ProgressIndicator) {
                val resolvedVersions = runtimeResolver.resolveVersions()
                SwingUtilities.invokeLater {
                    for (config in artifactTableModel.items) {
                        if (config.resolvedVersion == null) {
                            config.resolvedVersion = resolvedVersions[config.artifact.coordinate]
                        }
                    }
                    artifactTableModel.fireTableDataChanged()
                }
            }
        })
    }

    override fun applyTo(settings: Settings) {
        settings.grpcEnable = grpcEnableCheckbox.isSelected
        settings.grpcCallEnabled = grpcCallEnabledCheckbox.isSelected

        val configs = artifactTableModel.items.map { config ->
            when (config.versionMode) {
                ArtifactVersionMode.LATEST -> "${config.artifact.coordinate}:latest:${config.enabled}"
                ArtifactVersionMode.FIXED -> "${config.artifact.coordinate}:${config.fixedVersion ?: "latest"}:${config.enabled}"
            }
        }
        settings.grpcArtifactConfigs = configs.toTypedArray()

        val jars = mutableListOf<String>()
        for (i in 0 until additionalJarsModel.size()) {
            jars.add(additionalJarsModel.getElementAt(i))
        }
        settings.grpcAdditionalJars = jars.toTypedArray()
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = settings ?: return false
        if (grpcEnableCheckbox.isSelected != s.grpcEnable) return true
        if (grpcCallEnabledCheckbox.isSelected != s.grpcCallEnabled) return true

        val currentConfigs = artifactTableModel.items.map { config ->
            when (config.versionMode) {
                ArtifactVersionMode.LATEST -> "${config.artifact.coordinate}:latest:${config.enabled}"
                ArtifactVersionMode.FIXED -> "${config.artifact.coordinate}:${config.fixedVersion ?: "latest"}:${config.enabled}"
            }
        }.toTypedArray()
        if (!currentConfigs.contentEquals(s.grpcArtifactConfigs)) return true

        val currentJars = mutableListOf<String>()
        for (i in 0 until additionalJarsModel.size()) {
            currentJars.add(additionalJarsModel.getElementAt(i))
        }
        if (!currentJars.toTypedArray().contentEquals(s.grpcAdditionalJars)) return true

        return false
    }

    companion object {
        fun selectUnavailableArtifact(project: com.intellij.openapi.project.Project, artifact: Artifact) {
            val settings = com.itangcent.easyapi.settings.SettingBinder.getInstance(project).read()
            val configs = settings.grpcArtifactConfigs.mapNotNull { GrpcArtifactConfig.parse(it) }
            val index = configs.indexOfFirst { it.artifact == artifact }
            if (index >= 0) {
            }
        }
    }
}
