package com.itangcent.easyapi.core.ide.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.table.JBTable
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.spi.ChannelOptionsPanel
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.path
import java.awt.*
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellRenderer

class ExportDialog(
    private val project: Project,
    endpointCount: Int,
    private val endpoints: List<ApiEndpoint> = emptyList()
) : DialogWrapper(project) {

    private val channelRegistry = ChannelRegistry.getInstance(project)
    private val availableChannels: List<Channel> = channelRegistry.getAvailableChannels(endpoints)

    private val channelComboBox = JComboBox(availableChannels.map { it.displayName }.toTypedArray()).apply {
        if (availableChannels.isNotEmpty()) {
            selectedIndex = 0
        }
    }

    private val channelOptionsPanels = mutableMapOf<String, ChannelOptionsPanel?>()

    private val preferencesPersistence = ExportDialogPreferencesPersistence(project)

    private val cardLayout = CardLayout()
    private val optionsPanel = JPanel(cardLayout)

    private val hasAnyOptions = mutableMapOf<String, Boolean>()

    private val endpointTableModel = EndpointTableModel(endpoints)
    private val endpointTable = JBTable(endpointTableModel)
    private val selectAllBtn = JButton("Select All")
    private val deselectAllBtn = JButton("Deselect All")

    var selectedChannel: Channel? = null
        private set

    var channelConfig: ChannelConfig = ChannelConfig.Empty
        private set

    private val noChannelsLabel = JLabel("No export channels available").apply {
        foreground = Color.RED
        alignmentX = Component.CENTER_ALIGNMENT
    }

    init {
        title = "Export API Endpoints ($endpointCount endpoints)"

        for (channel in availableChannels) {
            val panel = channel.createOptionsPanel(project)
            channelOptionsPanels[channel.id] = panel
            hasAnyOptions[channel.id] = panel != null
            if (panel != null) {
                optionsPanel.add(panel.component, channel.id)
            }
        }

        val anyChannelHasOptions = hasAnyOptions.values.any { it }
        optionsPanel.isVisible = anyChannelHasOptions

        loadDefaultValues()

        channelComboBox.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                updateOptionsPanel()
            }
        }

        selectAllBtn.addActionListener { endpointTableModel.selectAll() }
        deselectAllBtn.addActionListener { endpointTableModel.deselectAll() }

        init()
        updateOptionsPanel()
        setupEndpointTable()

        if (availableChannels.isEmpty()) {
            okAction.isEnabled = false
        }
    }

    private fun setupEndpointTable() {
        endpointTable.autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS

        val selectCol = endpointTable.columnModel.getColumn(COL_SELECT)
        selectCol.maxWidth = 50
        selectCol.minWidth = 40
        selectCol.preferredWidth = 40
        selectCol.resizable = false
        selectCol.cellRenderer = CheckboxRenderer()
        selectCol.cellEditor = CheckboxEditor()

        val methodCol = endpointTable.columnModel.getColumn(COL_METHOD)
        methodCol.maxWidth = 80
        methodCol.minWidth = 60
        methodCol.preferredWidth = 70
        methodCol.resizable = false
        methodCol.cellRenderer = MethodCellRenderer()

        val pathCol = endpointTable.columnModel.getColumn(COL_PATH)
        pathCol.preferredWidth = 250
        pathCol.minWidth = 100

        val nameCol = endpointTable.columnModel.getColumn(COL_NAME)
        nameCol.preferredWidth = 180
        nameCol.minWidth = 80
    }

    private fun loadDefaultValues() {
        val savedPrefs = preferencesPersistence.load()
        savedPrefs.lastExportFormat?.let { channelId ->
            val idx = availableChannels.indexOfFirst { it.id == channelId }
            if (idx >= 0) {
                channelComboBox.selectedIndex = idx
            }
        }
    }

    private fun updateOptionsPanel() {
        val channel = findSelectedChannel()
        val panel = channel?.let { channelOptionsPanels[it.id] }
        if (panel != null) {
            cardLayout.show(optionsPanel, channel.id)
            panel.onShown()
            optionsPanel.isVisible = true
        } else {
            optionsPanel.isVisible = false
        }
    }

    private fun findSelectedChannel(): Channel? {
        val idx = channelComboBox.selectedIndex
        return if (idx >= 0 && idx < availableChannels.size) availableChannels[idx] else null
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            val topPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(createChannelPanel())
                add(Box.createVerticalStrut(10))
                add(optionsPanel)
            }
            add(topPanel, BorderLayout.NORTH)
            add(createEndpointPanel(), BorderLayout.CENTER)
        }
    }

    private fun createChannelPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            if (availableChannels.isEmpty()) {
                add(noChannelsLabel, BorderLayout.CENTER)
            } else {
                add(JLabel("Export Channel:"), BorderLayout.WEST)
                add(channelComboBox, BorderLayout.CENTER)
            }
        }
    }

    private fun createEndpointPanel(): JPanel {
        val headerPanel = JPanel(BorderLayout()).apply {
            add(JLabel("API Endpoints:"), BorderLayout.WEST)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(selectAllBtn)
                add(Box.createHorizontalStrut(4))
                add(deselectAllBtn)
            }, BorderLayout.EAST)
        }

        endpointTable.rowHeight = 24

        val scrollPane = JScrollPane(endpointTable).apply {
            preferredSize = Dimension(0, 200)
            minimumSize = Dimension(0, 120)
        }

        return JPanel(BorderLayout()).apply {
            add(headerPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
        }
    }

    override fun doOKAction() {
        val channel = findSelectedChannel()
        selectedChannel = channel

        val panel = channel?.let { channelOptionsPanels[it.id] }
        channelConfig = panel?.buildConfig() ?: ChannelConfig.Empty

        saveDialogState()
        super.doOKAction()
    }

    private fun saveDialogState() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = selectedChannel?.id
        )
        preferencesPersistence.save(prefs)
    }

    companion object {
        fun show(
            project: Project,
            endpointCount: Int,
            endpoints: List<ApiEndpoint> = emptyList()
        ): ExportDialogResult? {
            val dialog = ExportDialog(project, endpointCount, endpoints)
            return if (dialog.showAndGet()) {
                val selectedEndpoints = dialog.endpointTableModel.getSelectedEndpoints()
                ExportDialogResult(
                    channelId = dialog.selectedChannel?.id ?: "markdown",
                    channelConfig = dialog.channelConfig,
                    selectedEndpoints = selectedEndpoints
                )
            } else {
                null
            }
        }
    }
}

data class ExportDialogResult(
    val channelId: String,
    val channelConfig: ChannelConfig = ChannelConfig.Empty,
    val selectedEndpoints: List<EndpointSelection> = emptyList()
)

data class EndpointSelection(
    val endpoint: ApiEndpoint
)

private const val COL_SELECT = 0
private const val COL_METHOD = 1
private const val COL_PATH = 2
private const val COL_NAME = 3

private class EndpointTableModel(
    endpoints: List<ApiEndpoint>
) : AbstractTableModel() {

    data class Row(
        val endpoint: ApiEndpoint,
        var selected: Boolean = true
    )

    val rows = endpoints.map { Row(it, true) }

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = 4

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = when (columnIndex) {
        COL_SELECT -> rows[rowIndex].selected
        COL_METHOD -> rows[rowIndex].endpoint.httpMetadata?.method?.name
            ?: rows[rowIndex].endpoint.metadata.protocol

        COL_PATH -> rows[rowIndex].endpoint.path
        COL_NAME -> rows[rowIndex].endpoint.name ?: ""
        else -> null
    }

    override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
        if (columnIndex == COL_SELECT) {
            rows[rowIndex].selected = aValue as Boolean
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
        columnIndex == COL_SELECT

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        COL_SELECT -> Boolean::class.java
        else -> String::class.java
    }

    override fun getColumnName(column: Int): String = when (column) {
        COL_SELECT -> ""
        COL_METHOD -> "Method"
        COL_PATH -> "Path"
        COL_NAME -> "Name"
        else -> ""
    }

    fun selectAll() {
        rows.forEach { it.selected = true }
        fireTableDataChanged()
    }

    fun deselectAll() {
        rows.forEach { it.selected = false }
        fireTableDataChanged()
    }

    fun getSelectedEndpoints(): List<EndpointSelection> {
        return rows.filter { it.selected }.map { EndpointSelection(it.endpoint) }
    }
}

private class MethodCellRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (c is JLabel && value is String) {
            c.text = value.padEnd(6)
            if (!isSelected) {
                c.foreground = getMethodColor(value)
            }
        }
        return c
    }

    private fun getMethodColor(method: String): Color = when (method) {
        "GET" -> Color(0x61affe)
        "POST" -> Color(0x49cc90)
        "PUT" -> Color(0xfca130)
        "DELETE" -> Color(0xf93e3e)
        "PATCH" -> Color(0x50e3c2)
        "HEAD" -> Color(0x9012fe)
        "OPTIONS" -> Color(0x0d5aa7)
        "gRPC" -> Color(0x8B5CF6)
        else -> Color(0x999999)
    }
}

private class CheckboxRenderer : JCheckBox(), TableCellRenderer {

    init {
        horizontalAlignment = SwingConstants.CENTER
        isOpaque = true
    }

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        this.isSelected = value as? Boolean ?: false
        if (isSelected) {
            foreground = table?.selectionForeground
            background = table?.selectionBackground
        } else {
            foreground = table?.foreground
            background = table?.background
        }
        return this
    }
}

private class CheckboxEditor : DefaultCellEditor(JCheckBox().apply {
    horizontalAlignment = SwingConstants.CENTER
})
