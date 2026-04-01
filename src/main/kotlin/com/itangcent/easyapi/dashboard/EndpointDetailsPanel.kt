package com.itangcent.easyapi.dashboard

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.itangcent.easyapi.cache.DefaultHttpContextCacheHelper
import com.itangcent.easyapi.cache.HttpContextCacheHelper
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.http.FormParam
import com.itangcent.easyapi.http.FormParam.Text
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.KeyValue
import com.itangcent.easyapi.http.name
import com.itangcent.easyapi.http.value
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.net.URLEncoder
import javax.swing.*
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableModel

/**
 * Panel for displaying and editing API endpoint details.
 * 
 * This panel provides a comprehensive interface for:
 * - Viewing endpoint information (name, method, path)
 * - Editing request parameters (path, query, headers, form data, body)
 * - Sending HTTP requests and viewing responses
 * - Persisting user edits for later sessions
 * 
 * Features:
 * - Tabbed interface for different parameter types
 * - JSON syntax highlighting for body and response
 * - Auto-save of user modifications
 * - Host history management
 * 
 * @param project The IntelliJ IDEA project context
 * @param httpClient The HTTP client for executing requests
 */
class EndpointDetailsPanel(
    private val project: Project,
    private val httpClient: HttpClient
) : JBPanel<EndpointDetailsPanel>(BorderLayout()), IdeaLog {

    /** Coroutine scope for managing background HTTP requests */
    private val scope = CoroutineScope(SupervisorJob() + IdeDispatchers.Background)
    /** Current active HTTP request job, can be cancelled */
    private var currentRequestJob: Job? = null
    /** Timer for showing loading animation during requests */
    private var loadingTimer: Timer? = null
    /** Helper for managing host history cache */
    private val hostCacheHelper: HttpContextCacheHelper = DefaultHttpContextCacheHelper.getInstance(project)
    /** Service for persisting user edits */
    private val editCacheService: RequestEditCacheService = RequestEditCacheService.getInstance(project)

    /** Label displaying the endpoint name */
    private val nameLabel = JBLabel("").apply { font = font.deriveFont(font.size + 2f) }
    /** Label displaying the HTTP method with color coding */
    private val methodLabel = JBLabel("")
    /** Combo box for selecting/editing the host URL */
    private val hostComboBox: JComboBox<String> = JComboBox<String>().apply {
        isEditable = true
        preferredSize = Dimension(200, preferredSize.height)
    }
    /** Text field for the request path */
    private val pathField = JTextField().apply { isEditable = true }
    /** Button to send the HTTP request */
    private val sendButton = JButton("Send")
    /** Button to reset modifications to default values */
    private val resetButton = JButton("Reset").apply {
        toolTipText = "Reset to default values"
    }

    // Path params table (Key / Value / Description)
    /** Table model for path parameters */
    private val pathParamsTableModel = object : DefaultTableModel(arrayOf("Key", "Value", "Description", ""), 0) {
        override fun isCellEditable(row: Int, column: Int) = column != 0 && column < columnCount - 1
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == columnCount - 1) JButton::class.java else Any::class.java
    }
    /** Table for editing path parameters */
    private val pathParamsTable = JBTable(pathParamsTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        setupDeleteButtonColumn(this, pathParamsTableModel)
        setupPathParamsComboBoxEditor()
    }

    /** Map of row index to enum values for path parameter dropdowns */
    private var pathParamsEnumValues: MutableMap<Int, List<String>> = mutableMapOf()

    /**
     * Sets up combo box editor for path parameter value column.
     */
    private fun JBTable.setupPathParamsComboBoxEditor() {
        val valueColumn = this.columnModel.getColumn(1)
        valueColumn.cellEditor = DefaultCellEditor(JComboBox<String>().apply {
            isEditable = true
        })
    }

    /**
     * Updates the combo box editor for a path parameter row with enum values.
     * 
     * @param row The row index
     * @param enumValues The list of allowed values, or null for free-form input
     */
    private fun updatePathParamComboBox(row: Int, enumValues: List<String>?) {
        if (enumValues != null && enumValues.isNotEmpty()) {
            pathParamsEnumValues[row] = enumValues
            val comboBox = JComboBox(enumValues.toTypedArray()).apply {
                isEditable = true
                val currentValue = pathParamsTableModel.getValueAt(row, 1)?.toString() ?: ""
                if (currentValue.isNotBlank()) {
                    selectedItem = currentValue
                } else {
                    selectedItem = enumValues.first()
                    pathParamsTableModel.setValueAt(enumValues.first(), row, 1)
                }
            }
            pathParamsTable.columnModel.getColumn(1).cellEditor = DefaultCellEditor(comboBox)
        }
    }

    // Query params table (Key / Value / Description)
    /** Table model for query parameters */
    private val paramsTableModel = object : DefaultTableModel(arrayOf("Key", "Value", "Description", ""), 0) {
        override fun isCellEditable(row: Int, column: Int) = column < columnCount - 1
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == columnCount - 1) JButton::class.java else Any::class.java
    }
    /** Table for editing query parameters */
    private val paramsTable = JBTable(paramsTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        setupDeleteButtonColumn(this, paramsTableModel)
    }

    // Headers table (editable)
    /** Table model for request headers */
    private val headersTableModel = object : DefaultTableModel(arrayOf("Name", "Value", ""), 0) {
        override fun isCellEditable(row: Int, column: Int) = column < columnCount - 1
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == columnCount - 1) JButton::class.java else Any::class.java
    }
    /** Table for editing request headers */
    private val headersTable = JBTable(headersTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        setupDeleteButtonColumn(this, headersTableModel)
    }

    // Form data table (Key / Value / Description)
    /** Table model for form data parameters */
    private val formTableModel = object : DefaultTableModel(arrayOf("Key", "Value", "Description", ""), 0) {
        override fun isCellEditable(row: Int, column: Int) = column < columnCount - 1
        override fun getColumnClass(columnIndex: Int): Class<*> =
            if (columnIndex == columnCount - 1) JButton::class.java else Any::class.java
    }
    /** Table for editing form data parameters */
    private val formTable = JBTable(formTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        setupDeleteButtonColumn(this, formTableModel)
    }

    private val jsonFileType = FileTypeManager.getInstance().getFileTypeByExtension("json")

    // Body area (editable, raw JSON with syntax highlighting)
    /** Editor for request body with JSON syntax highlighting */
    private val bodyArea = EditorTextField("", project, jsonFileType).apply {
        setOneLineMode(false)
    }

    /** Button to format/beautify JSON body */
    private val formatBodyBtn = JButton("Format").apply {
        toolTipText = "Format/Beautify JSON"
        addActionListener { formatRequestBody() }
    }

    // Response body area
    /** Editor for response body with JSON syntax highlighting (read-only) */
    private val responseBodyArea = EditorTextField("", project, jsonFileType).apply {
        setOneLineMode(false)
        isViewer = true
    }

    // Response headers table (read-only)
    /** Table model for response headers */
    private val responseHeadersTableModel = object : DefaultTableModel(arrayOf("Name", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int) = false
    }
    /** Table for displaying response headers */
    private val responseHeadersTable = JBTable(responseHeadersTableModel).apply {
        autoResizeMode = JBTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
    }

    // Response status label
    /** Label displaying the response status code */
    private val responseStatusLabel = JBLabel("").apply { font = font.deriveFont(font.size + 1f) }

    // Response tabs
    /** Tab pane for response body and headers */
    private val responseTabPane = JTabbedPane()

    // Response body state
    /** Raw response body text */
    private var rawResponseBody: String = ""
    /** Whether response is displayed in pretty JSON format */
    private var isPrettyJson: Boolean = true

    // Response toolbar buttons
    /** Button to toggle between formatted and raw JSON view */
    private val prettyToggleBtn = JButton("Raw").apply {
        toolTipText = "Toggle between formatted and raw JSON"
        addActionListener { togglePrettyJson() }
    }
    /** Button to copy response body to clipboard */
    private val copyResponseBtn = JButton("Copy").apply {
        toolTipText = "Copy response body to clipboard"
        addActionListener { copyResponseBody() }
    }

    /** Tab pane for request parameters (Path, Params, Headers, Form/Body) */
    private val tabPane = JTabbedPane()
    /** The currently displayed API endpoint */
    private var currentEndpoint: ApiEndpoint? = null

    /** Cached key for the current endpoint (computed under read action) */
    private var currentEndpointKey: String = ""

    /** Tracks whether the current endpoint uses form-data body */
    private var hasFormData = false

    /** The content type of the current endpoint (used to distinguish urlencoded vs multipart) */
    private var endpointContentType: String? = null

    /** Flag to prevent auto-save during programmatic updates */
    private var isLoading: Boolean = false

    /** Debounce timer for auto-save */
    private val autoSaveTimer = Timer(500) {
        if (!isLoading && currentEndpointKey.isNotEmpty()) {
            doSaveCurrentEdit()
        }
    }.apply { isRepeats = false }

    /**
     * Initializes the panel with UI components and event listeners.
     */
    init {
        loadHostHistory()
        setupAutoSaveListeners()

        val namePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(6, 8, 2, 8)
            add(nameLabel)
            add(Box.createHorizontalGlue())
        }

        val requestLinePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(2, 8)
            add(methodLabel)
            add(Box.createHorizontalStrut(6))
            add(hostComboBox)
            add(Box.createHorizontalStrut(2))
            add(pathField)
            add(Box.createHorizontalStrut(6))
            add(sendButton)
            add(Box.createHorizontalStrut(4))
            add(resetButton)
        }

        val headerPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(namePanel)
            add(requestLinePanel)
        }

        // Build response tabs
        buildResponseTabs()

        // Title panel with "Response" on left and status/buttons on right
        val responseTitlePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(8)
            val titleLabel = JBLabel("Response").apply {
                font = font.deriveFont(font.size + 1f)
            }
            add(JPanel().apply {
                add(Box.createHorizontalStrut(8))
                add(titleLabel)
            }, BorderLayout.WEST)
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(responseStatusLabel)
                add(Box.createHorizontalStrut(8))
                add(copyResponseBtn)
                add(Box.createHorizontalStrut(8))
            }, BorderLayout.EAST)
        }

        // Request tabs with fixed preferred height
        val requestWrapper = JPanel(BorderLayout()).apply {
            add(tabPane, BorderLayout.CENTER)
            preferredSize = Dimension(0, 200)
            minimumSize = Dimension(0, 120)
        }

        // Response section with fixed preferred height
        val responseContainer = JPanel(BorderLayout()).apply {
            add(responseTitlePanel, BorderLayout.NORTH)
            add(responseTabPane, BorderLayout.CENTER)
            preferredSize = Dimension(0, 300)
            minimumSize = Dimension(0, 200)
        }

        // Stack request + response vertically
        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(requestWrapper)
            add(responseContainer)
        }

        // Wrap in scroll pane so the whole thing scrolls when panel is small
        val scrollPane = JBScrollPane(contentPanel).apply {
            border = BorderFactory.createEmptyBorder()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        add(headerPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        sendButton.addActionListener { sendRequest() }
        resetButton.addActionListener { resetCurrentEndpoint() }
    }

    private fun buildResponseTabs() {
        responseTabPane.removeAll()

        val responseBodyScrollPane = JBScrollPane(responseBodyArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }

        // Float the Raw/Format toggle at the top-right corner of the response body
        val wrapper = object : JPanel(null) {
            override fun getPreferredSize(): java.awt.Dimension = responseBodyScrollPane.preferredSize
            override fun getMinimumSize(): java.awt.Dimension = responseBodyScrollPane.minimumSize

            override fun doLayout() {
                responseBodyScrollPane.setBounds(0, 0, width, height)
                val btnSize = prettyToggleBtn.preferredSize
                prettyToggleBtn.setBounds(
                    width - btnSize.width - 22,
                    4,
                    btnSize.width,
                    btnSize.height
                )
            }
        }
        wrapper.add(prettyToggleBtn)
        wrapper.add(responseBodyScrollPane)

        responseTabPane.addTab("Body", wrapper)
        responseTabPane.addTab("Headers", JBScrollPane(responseHeadersTable).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        })
    }

    private fun setupDeleteButtonColumn(table: JBTable, model: DefaultTableModel) {
        val deleteColIndex = model.columnCount - 1
        val deleteColumn = table.columnModel.getColumn(deleteColIndex)
        deleteColumn.maxWidth = 40
        deleteColumn.minWidth = 40
        deleteColumn.preferredWidth = 40
        deleteColumn.resizable = false

        if (model.columnCount >= 3) {
            val keyColumn = table.columnModel.getColumn(0)
            keyColumn.preferredWidth = 150
            keyColumn.minWidth = 80

            val valueColumn = table.columnModel.getColumn(1)
            valueColumn.preferredWidth = 300
            valueColumn.minWidth = 100

            if (model.columnCount >= 3 && deleteColIndex == 3) {
                val descColumn = table.columnModel.getColumn(2)
                descColumn.preferredWidth = 200
                descColumn.minWidth = 80
            }
        }

        table.setDefaultRenderer(Any::class.java, object : javax.swing.table.DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): java.awt.Component {
                if (column == deleteColIndex) {
                    return JButton("✕").apply {
                        isOpaque = true
                        toolTipText = "Delete row"
                    }
                }
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            }
        })

        table.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val col = table.columnAtPoint(e.point)
                if (col == deleteColIndex) {
                    val row = table.rowAtPoint(e.point)
                    if (row >= 0 && row < model.rowCount) {
                        model.removeRow(row)
                        ensureEmptyRow(model)
                    }
                }
            }
        })

        model.addTableModelListener { e ->
            if (e.type == javax.swing.event.TableModelEvent.UPDATE && e.firstRow == model.rowCount - 1) {
                ensureEmptyRow(model)
            }
        }
    }

    private fun ensureEmptyRow(model: DefaultTableModel) {
        val lastRow = model.rowCount - 1
        val hasEmptyRow = if (lastRow >= 0) {
            (0 until model.columnCount - 1).all { col ->
                model.getValueAt(lastRow, col)?.toString().isNullOrBlank()
            }
        } else false

        if (!hasEmptyRow) {
            model.addRow(arrayOfNulls(model.columnCount))
        }
    }

    private fun createEditableTable(table: JBTable): JPanel {
        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(table).apply {
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
            }, BorderLayout.CENTER)
        }
    }

    private fun rebuildTabs(hasPathParams: Boolean, hasFormParams: Boolean) {
        tabPane.removeAll()
        if (hasPathParams) {
            tabPane.addTab("Path", createEditableTable(pathParamsTable))
        }
        tabPane.addTab("Params", createEditableTable(paramsTable))
        tabPane.addTab("Headers", createEditableTable(headersTable))
        if (hasFormParams) {
            tabPane.addTab("Form", createEditableTable(formTable))
        } else {
            tabPane.addTab("Body", createBodyPanel())
        }
    }

    private fun createBodyPanel(): JPanel {
        val scrollPane = JBScrollPane(bodyArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }

        // Null-layout panel that floats the Format button at the top-right
        // corner over the scroll pane. Preferred size delegates to the scroll pane.
        val wrapper = object : JPanel(null) {
            override fun getPreferredSize(): java.awt.Dimension = scrollPane.preferredSize
            override fun getMinimumSize(): java.awt.Dimension = scrollPane.minimumSize

            override fun doLayout() {
                scrollPane.setBounds(0, 0, width, height)
                val btnSize = formatBodyBtn.preferredSize
                formatBodyBtn.setBounds(
                    width - btnSize.width - 22,
                    4,
                    btnSize.width,
                    btnSize.height
                )
            }
        }
        // Button added first so it paints on top (lower z-index = painted later)
        wrapper.add(formatBodyBtn)
        wrapper.add(scrollPane)

        return wrapper
    }

    private fun loadHostHistory() {
        val hosts = hostCacheHelper.getHosts()
        hostComboBox.removeAllItems()
        if (hosts.isEmpty()) {
            hostComboBox.addItem("http://localhost:8080")
        } else {
            hosts.forEach { hostComboBox.addItem(it) }
        }
        hostComboBox.selectedIndex = 0
    }

    fun getSelectedHost(): String {
        val host = (hostComboBox.editor.item as? String ?: hostComboBox.selectedItem as? String ?: "")
            .trim().trimEnd('/')
        return if (host.isEmpty()) "http://localhost:8080" else host
    }

    fun showEndpoint(endpoint: ApiEndpoint) {
        isLoading = true
        try {
            currentEndpoint = endpoint
            currentEndpointKey = computeCacheKey(endpoint)
            nameLabel.text = endpoint.name ?: "Unnamed"
            methodLabel.text = endpoint.method.name
            methodLabel.foreground = getMethodColor(endpoint.method)

            val cachedEdit = editCacheService.load(endpoint, currentEndpointKey)
            if (cachedEdit != null) {
                loadFromCache(endpoint, cachedEdit)
            } else {
                loadFromEndpoint(endpoint)
            }

            clearResponse()
            autoSelectTab()
        } finally {
            isLoading = false
        }
    }

    private fun loadFromCache(endpoint: ApiEndpoint, cache: RequestEditCache) {
        pathField.text = cache.path ?: endpoint.path

        cache.host?.let { host ->
            val hosts = hostCacheHelper.getHosts()
            hostComboBox.removeAllItems()
            if (hosts.isEmpty()) {
                hostComboBox.addItem(host)
            } else {
                if (host !in hosts) {
                    hostComboBox.addItem(host)
                }
                hosts.forEach { hostComboBox.addItem(it) }
                hostComboBox.selectedItem = host
            }
        }

        val pathParams = endpoint.parameters.filter { it.binding == ParameterBinding.Path }
        pathParamsTableModel.rowCount = 0
        pathParamsEnumValues.clear()
        val cachedPathParams = cache.pathParams.associateBy { it.name }
        pathParams.forEachIndexed { index, p ->
            val cachedValue = cachedPathParams[p.name]?.value ?: p.defaultValue ?: p.example ?: ""
            pathParamsTableModel.addRow(arrayOf(p.name, cachedValue, p.description ?: "", ""))
            updatePathParamComboBox(index, p.enumValues)
        }
        ensureEmptyRow(pathParamsTableModel)

        paramsTableModel.rowCount = 0
        val cachedQueryParams = cache.queryParams.associateBy { it.name }
        endpoint.parameters
            .filter { it.binding == ParameterBinding.Query || it.binding == ParameterBinding.Cookie }
            .forEach { p ->
                val cachedValue = cachedQueryParams[p.name]?.value ?: p.defaultValue ?: p.example ?: ""
                paramsTableModel.addRow(arrayOf(p.name, cachedValue, p.description ?: "", ""))
            }
        ensureEmptyRow(paramsTableModel)

        headersTableModel.rowCount = 0
        val cachedHeaders = cache.headers.associateBy { it.name }
        endpoint.headers.forEach { h ->
            val cachedValue = cachedHeaders[h.name]?.value ?: h.value ?: ""
            headersTableModel.addRow(arrayOf(h.name, cachedValue, ""))
        }
        endpoint.parameters.filter { it.binding == ParameterBinding.Header }
            .forEach { p ->
                val cachedValue = cachedHeaders[p.name]?.value ?: p.defaultValue ?: p.example ?: ""
                headersTableModel.addRow(arrayOf(p.name, cachedValue, ""))
            }
        ensureEmptyRow(headersTableModel)

        val formParams = endpoint.parameters.filter { it.binding == ParameterBinding.Form }
        formTableModel.rowCount = 0
        val cachedFormParams = cache.formParams.associateBy { it.name }
        formParams.forEach { p ->
            val cachedValue = cachedFormParams[p.name]?.value ?: p.defaultValue ?: p.example ?: ""
            formTableModel.addRow(arrayOf(p.name, cachedValue, p.description ?: "", ""))
        }
        ensureEmptyRow(formTableModel)

        val isFormData = formParams.isNotEmpty() ||
                endpoint.contentType?.contains("form-urlencoded", ignoreCase = true) == true ||
                endpoint.contentType?.contains("form-data", ignoreCase = true) == true
        hasFormData = isFormData
        endpointContentType = cache.contentType ?: endpoint.contentType

        bodyArea.text = if (!isFormData) {
            cache.body ?: endpoint.body?.let { ObjectModelJsonConverter.toJson(it) } ?: ""
        } else ""

        rebuildTabs(hasPathParams = pathParams.isNotEmpty(), hasFormParams = isFormData)
    }

    private fun loadFromEndpoint(endpoint: ApiEndpoint) {
        pathField.text = endpoint.path

        val pathParams = endpoint.parameters.filter { it.binding == ParameterBinding.Path }
        pathParamsTableModel.rowCount = 0
        pathParamsEnumValues.clear()
        pathParams.forEachIndexed { index, p ->
            pathParamsTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", p.description ?: "", ""))
            updatePathParamComboBox(index, p.enumValues)
        }
        ensureEmptyRow(pathParamsTableModel)

        paramsTableModel.rowCount = 0
        endpoint.parameters
            .filter { it.binding == ParameterBinding.Query || it.binding == ParameterBinding.Cookie }
            .forEach { p ->
                paramsTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", p.description ?: "", ""))
            }
        ensureEmptyRow(paramsTableModel)

        headersTableModel.rowCount = 0
        endpoint.headers.forEach { h ->
            headersTableModel.addRow(arrayOf(h.name, h.value ?: "", ""))
        }
        endpoint.parameters.filter { it.binding == ParameterBinding.Header }
            .forEach { p ->
                headersTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", ""))
            }
        ensureEmptyRow(headersTableModel)

        val formParams = endpoint.parameters.filter { it.binding == ParameterBinding.Form }
        formTableModel.rowCount = 0
        formParams.forEach { p ->
            formTableModel.addRow(arrayOf(p.name, p.defaultValue ?: p.example ?: "", p.description ?: "", ""))
        }
        ensureEmptyRow(formTableModel)

        val isFormData = formParams.isNotEmpty() ||
                endpoint.contentType?.contains("form-urlencoded", ignoreCase = true) == true ||
                endpoint.contentType?.contains("form-data", ignoreCase = true) == true
        hasFormData = isFormData
        endpointContentType = endpoint.contentType

        bodyArea.text = if (!isFormData) {
            endpoint.body?.let { ObjectModelJsonConverter.toJson(it) } ?: ""
        } else ""

        rebuildTabs(hasPathParams = pathParams.isNotEmpty(), hasFormParams = isFormData)
    }

    private fun autoSelectTab() {
        val tabCount = tabPane.tabCount
        val pathParamsCount = pathParamsTableModel.rowCount - 1
        val paramsCount = paramsTableModel.rowCount - 1
        val headersCount = headersTableModel.rowCount - 1

        tabPane.selectedIndex = when {
            pathParamsCount > 0 -> 0
            hasFormData -> (0 until tabCount).firstOrNull { tabPane.getTitleAt(it) == "Form" } ?: 0
            bodyArea.text.isNotBlank() -> (0 until tabCount).firstOrNull { tabPane.getTitleAt(it) == "Body" } ?: 0
            paramsCount > 0 -> (0 until tabCount).firstOrNull { tabPane.getTitleAt(it) == "Params" } ?: 0
            headersCount > 0 -> (0 until tabCount).firstOrNull { tabPane.getTitleAt(it) == "Headers" } ?: 0
            else -> 0
        }
    }

    private fun clearResponse() {
        currentRequestJob?.cancel()
        currentRequestJob = null
        loadingTimer?.stop()
        loadingTimer = null
        sendButton.isEnabled = true
        sendButton.text = "Send"

        rawResponseBody = ""
        isPrettyJson = true
        prettyToggleBtn.text = "Raw"
        responseBodyArea.text = ""
        responseHeadersTableModel.rowCount = 0
        responseStatusLabel.text = ""
        responseStatusLabel.foreground = null
    }

    fun clear() {
        currentEndpoint = null
        currentEndpointKey = ""
        nameLabel.text = ""
        methodLabel.text = ""
        pathField.text = ""
        pathParamsTableModel.rowCount = 0
        paramsTableModel.rowCount = 0
        headersTableModel.rowCount = 0
        formTableModel.rowCount = 0
        bodyArea.text = ""
        clearResponse()
        hasFormData = false
        endpointContentType = null
        rebuildTabs(hasPathParams = false, hasFormParams = false)
        ensureEmptyRow(pathParamsTableModel)
        ensureEmptyRow(paramsTableModel)
        ensureEmptyRow(headersTableModel)
        ensureEmptyRow(formTableModel)
    }

    private fun sendRequest() {
        val endpoint = currentEndpoint ?: return
        val host = getSelectedHost()

        var path = pathField.text
        for (row in 0 until pathParamsTableModel.rowCount) {
            val key = pathParamsTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
            val value = pathParamsTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                path = path.replace("{$key}", URLEncoder.encode(value, "UTF-8"))
            }
        }

        val fullUrl = host + (if (path.startsWith("/")) path else "/$path")

        hostCacheHelper.addHost(host)
        loadHostHistory()

        LOG.info("Sending ${endpoint.method.name} request to $fullUrl")

        sendButton.isEnabled = false
        sendButton.text = "Sending..."
        responseBodyArea.text = ""
        responseStatusLabel.text = "Sending..."

        var loadingDots = 0
        loadingTimer = Timer(300) {
            loadingDots = (loadingDots + 1) % 4
            val dots = ".".repeat(loadingDots)
            sendButton.text = "Sending$dots"
            responseStatusLabel.text = "Sending$dots"
        }.apply { start() }

        currentRequestJob = scope.launch {
            try {
                val headers = extractKeyValuesFromTable(headersTableModel)
                val query = extractKeyValuesFromTable(paramsTableModel)

                val formParams: List<FormParam>
                val body: String?
                val finalHeaders: List<KeyValue>
                if (hasFormData) {
                    formParams = extractKeyValuesFromTable(formTableModel)
                        .map { Text(it.name, it.value) }
                    body = null
                    finalHeaders = headers
                } else {
                    formParams = emptyList()
                    body = bodyArea.text.takeIf { it.isNotBlank() }
                    finalHeaders = headers
                }

                val request = HttpRequest(
                    url = fullUrl,
                    method = endpoint.method.name,
                    headers = finalHeaders,
                    query = query,
                    body = body,
                    formParams = formParams,
                    contentType = endpointContentType
                )
                LOG.debug("Request: ${request.method} ${request.url}, headers=${request.headers.size}, hasBody=${request.body != null}")
                val response = httpClient.execute(request)
                LOG.info("Response: status=${response.code}, bodyLength=${response.body?.length ?: 0}")
                swing {
                    if (currentEndpoint != endpoint) {
                        LOG.debug("Endpoint changed, discarding response")
                        return@swing
                    }

                    loadingTimer?.stop()
                    loadingTimer = null
                    sendButton.isEnabled = true
                    sendButton.text = "Send"

                    responseStatusLabel.text = "Status: ${response.code}"
                    responseStatusLabel.foreground = when {
                        response.code in 200..299 -> Color(0x49cc90)
                        response.code in 400..499 -> Color(0xfca130)
                        response.code >= 500 -> Color(0xf93e3e)
                        else -> null
                    }

                    rawResponseBody = response.body ?: ""
                    isPrettyJson = true
                    prettyToggleBtn.text = "Raw"
                    responseBodyArea.text = formatJson(rawResponseBody)

                    responseHeadersTableModel.rowCount = 0
                    response.headers.forEach { (k, v) ->
                        responseHeadersTableModel.addRow(arrayOf(k, v.joinToString(", ")))
                    }

                    responseTabPane.selectedIndex = 0
                }
            } catch (_: CancellationException) {
                LOG.debug("Request cancelled")
            } catch (e: Exception) {
                LOG.warn("Request failed: ${e.message}", e)
                swing {
                    if (currentEndpoint != endpoint) {
                        LOG.debug("Endpoint changed, discarding error")
                        return@swing
                    }

                    loadingTimer?.stop()
                    loadingTimer = null
                    sendButton.isEnabled = true
                    sendButton.text = "Send"

                    rawResponseBody = "Error: ${e.message}"
                    responseBodyArea.text = rawResponseBody
                    responseStatusLabel.text = "Error"
                    responseStatusLabel.foreground = Color(0xf93e3e)
                    responseHeadersTableModel.rowCount = 0
                }
            }
        }
    }

    private fun togglePrettyJson() {
        isPrettyJson = !isPrettyJson
        prettyToggleBtn.text = if (isPrettyJson) "Raw" else "Format"
        responseBodyArea.text = if (isPrettyJson) {
            formatJson(rawResponseBody)
        } else {
            rawResponseBody
        }
    }

    private fun copyResponseBody() {
        val text = responseBodyArea.text
        if (text.isNotEmpty()) {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            val selection = java.awt.datatransfer.StringSelection(text)
            clipboard.setContents(selection, null)
            showCopyNotification()
        }
    }

    private fun showCopyNotification() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("EasyApi Notifications")
            .createNotification("Copied to clipboard", NotificationType.INFORMATION)
            .notify(project)
    }

    private fun extractKeyValuesFromTable(model: DefaultTableModel): List<KeyValue> {
        return (0 until model.rowCount)
            .map { row ->
                val name = model.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                val value = model.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                name to value
            }
            .filter { it.name.isNotEmpty() }
    }

    private fun formatJson(json: String): String {
        if (json.isBlank()) return json
        return runCatching {
            val gson = com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create()
            val element = gson.fromJson(json, com.google.gson.JsonElement::class.java)
            gson.toJson(element)
        }.getOrElse { json }
    }

    private fun formatRequestBody() {
        val currentText = bodyArea.text
        val formatted = formatJson(currentText)
        if (formatted != currentText) {
            bodyArea.text = formatted
        }
    }

    private fun getMethodColor(method: HttpMethod): Color = when (method) {
        HttpMethod.GET -> Color(0x61affe)
        HttpMethod.POST -> Color(0x49cc90)
        HttpMethod.PUT -> Color(0xfca130)
        HttpMethod.DELETE -> Color(0xf93e3e)
        HttpMethod.PATCH -> Color(0x50e3c2)
        HttpMethod.HEAD -> Color(0x9012fe)
        HttpMethod.OPTIONS -> Color(0x0d5aa7)
        HttpMethod.NO_METHOD -> Color(0x999999)
    }

    private fun resetCurrentEndpoint() {
        val endpoint = currentEndpoint ?: return
        editCacheService.delete(currentEndpointKey)
        isLoading = true
        try {
            loadFromEndpoint(endpoint)
            autoSelectTab()
            clearResponse()
        } finally {
            isLoading = false
        }
    }

    fun resetEndpoint(endpoint: ApiEndpoint) {
        if (currentEndpoint == endpoint) {
            resetCurrentEndpoint()
        } else {
            val key = readSync {
                computeCacheKey(endpoint)
            }
            editCacheService.delete(key)
        }
    }

    private fun saveCurrentEdit() {
        autoSaveTimer.restart()
    }

    private fun doSaveCurrentEdit() {
        val endpoint = currentEndpoint ?: return

        val headers = (0 until headersTableModel.rowCount)
            .map { row ->
                val name = headersTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                val value = headersTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                EditableKeyValue(name, value)
            }
            .filter { it.name.isNotEmpty() }

        val pathParams = (0 until pathParamsTableModel.rowCount)
            .map { row ->
                val name = pathParamsTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                val value = pathParamsTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                val desc = pathParamsTableModel.getValueAt(row, 2)?.toString()?.trim().orEmpty()
                EditableKeyValue(name, value, desc)
            }
            .filter { it.name.isNotEmpty() }

        val queryParams = (0 until paramsTableModel.rowCount)
            .map { row ->
                val name = paramsTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                val value = paramsTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                val desc = paramsTableModel.getValueAt(row, 2)?.toString()?.trim().orEmpty()
                EditableKeyValue(name, value, desc)
            }
            .filter { it.name.isNotEmpty() }

        val formParams = (0 until formTableModel.rowCount)
            .map { row ->
                val name = formTableModel.getValueAt(row, 0)?.toString()?.trim().orEmpty()
                val value = formTableModel.getValueAt(row, 1)?.toString()?.trim().orEmpty()
                val desc = formTableModel.getValueAt(row, 2)?.toString()?.trim().orEmpty()
                EditableKeyValue(name, value, desc)
            }
            .filter { it.name.isNotEmpty() }

        val cache = RequestEditCache(
            key = currentEndpointKey,
            name = endpoint.name,
            path = pathField.text,
            method = endpoint.method.name,
            host = getSelectedHost(),
            headers = headers,
            pathParams = pathParams,
            queryParams = queryParams,
            formParams = formParams,
            body = bodyArea.text.takeIf { it.isNotBlank() },
            contentType = endpointContentType
        )
        editCacheService.save(endpoint, cache, currentEndpointKey)
    }

    private fun setupAutoSaveListeners() {
        pathField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = saveCurrentEdit()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = saveCurrentEdit()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = saveCurrentEdit()
        })

        hostComboBox.addActionListener { saveCurrentEdit() }

        bodyArea.document.addDocumentListener(object : com.intellij.openapi.editor.event.DocumentListener {
            override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) = saveCurrentEdit()
        }, project)

        val tableModelListener = TableModelListener { e ->
            if (e.type == TableModelEvent.UPDATE) {
                saveCurrentEdit()
            }
        }
        pathParamsTableModel.addTableModelListener(tableModelListener)
        paramsTableModel.addTableModelListener(tableModelListener)
        headersTableModel.addTableModelListener(tableModelListener)
        formTableModel.addTableModelListener(tableModelListener)
    }

    private fun computeCacheKey(endpoint: ApiEndpoint): String {
        val method = endpoint.sourceMethod ?: return ""
        val cls = endpoint.sourceClass ?: method.containingClass ?: return ""

        val className = readSync {
            cls.qualifiedName ?: cls.name ?: ""
        }
        return "$className#${method.name}"
    }

    fun dispose() {
        scope.cancel()
    }
}
