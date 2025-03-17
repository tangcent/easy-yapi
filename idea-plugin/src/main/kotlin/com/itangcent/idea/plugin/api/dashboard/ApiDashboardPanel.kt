package com.itangcent.idea.plugin.api.dashboard

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.ComboBoxCellEditor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.itangcent.common.logger.Log
import com.itangcent.common.model.FormParam
import com.itangcent.common.model.Header
import com.itangcent.common.model.NamedValue
import com.itangcent.common.model.Param
import com.itangcent.http.HttpResponse
import com.itangcent.http.RequestUtils
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.swing.EasyApiTreeCellRenderer
import com.itangcent.idea.utils.*
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.rx.throttle
import com.itangcent.utils.ActionKeys
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import javax.swing.table.TableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel
import kotlin.concurrent.thread
import com.itangcent.idea.utils.FileSelectHelper

class ApiDashboardPanel(private val project: Project) : JBPanel<ApiDashboardPanel>(BorderLayout()), Disposable {
    companion object : Log()

    private val disabledFormTableBinder: ParamsTableBinder<FormParam> = DisabledFormTableBinder()
    private val noTypedFormTableBinder: ParamsTableBinder<FormParam> = NoTypedFormTableBinder()
    private val typedFormTableBinder: ParamsTableBinder<FormParam> = TypedFormTableBinder()
    private val queryParamsTableBinder: ParamsTableBinder<Param> = QueryParamsTableBinder()

    private val apiTree: Tree = Tree().apply {
        background = UIUtil.getTreeBackground()
        foreground = UIUtil.getTreeForeground()
        setSelectionRow(-1)  // Clear any initial selection
    }
    private val searchField: JTextField
    private val methodComboBox: JComboBox<String>
    private val hostComboBox: JComboBox<String>
    private val urlField: JTextField
    private val sendButton: JButton
    private val responseArea: JTextArea
    private val rawResponseArea: JTextArea
    private val headerArea: JTextArea
    private val bodyArea: JTextArea
    private val tabPane: JTabbedPane
    private val toolBar: JToolBar
    private val searchDebounceTimer = Timer(300) { filterTree() }
    private val apiPopupMenu: JPopupMenu = JPopupMenu()
    private val contentTypeComboBox: JComboBox<String>
    private val formTable: JBTable
    private val paramsTable: JBTable
    private val saveButton: JButton
    private val responseHeadersTextArea: JTextArea
    private val contentTypeChangeThrottle = throttle()
    private val statusLabel: JLabel
    private val statusPanel: JPanel

    private lateinit var service: ApiDashboardService
    private var apis: List<ProjectNodeData> = emptyList()

    private var currentResponse: HttpResponse? = null
    private var formTableBinder: ParamsTableBinder<FormParam> = disabledFormTableBinder

    private val actionContext: ActionContext
        get() = service.actionContext

    init {
        // Initialize components
        searchField = JTextField()
        methodComboBox = JComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"))
        hostComboBox = JComboBox<String>().apply {
            isEditable = true
            preferredSize = Dimension(250, preferredSize.height)
        }
        urlField = JTextField()
        sendButton = JButton("Send")
        responseArea = JTextArea()
        rawResponseArea = JTextArea()
        headerArea = JTextArea()
        bodyArea = JTextArea()
        tabPane = JTabbedPane()
        toolBar = JToolBar()
        contentTypeComboBox =
            JComboBox(arrayOf("application/json", "application/x-www-form-urlencoded", "multipart/form-data"))
        formTable = JBTable()
        paramsTable = JBTable()
        saveButton = JButton("Save").apply {
            isEnabled = false
        }
        responseHeadersTextArea = JTextArea()
        statusLabel = JLabel("unknown")
        statusPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(JLabel("Status: "))
            add(statusLabel)
            isVisible = false
        }

        // Set up the layout
        setupLayout()

        // Set up the tree
        setupTree()

        // Add listeners
        setupListeners()

        // Initial refresh
        thread {
            project.waiteUtilIndexReady()
            ApplicationManager.getApplication().runReadAction {
                service = ApiDashboardService.getInstance(project)
                service.setDashboardPanel(this)
                service.refreshApis()
                refreshHosts()
            }
        }
    }

    private fun setupLayout() {
        background = UIUtil.getPanelBackground()

        // Create toolbar with refresh and collapse buttons
        toolBar.isFloatable = false
        toolBar.background = UIUtil.getPanelBackground()
        val refreshButton = JButton(EasyIcons.Refresh)
        refreshButton.toolTipText = "Refresh APIs"
        refreshButton.addActionListener { service.refreshApis(useCache = false) }

        val collapseButton = JButton(EasyIcons.CollapseAll)
        collapseButton.toolTipText = "Collapse All"
        collapseButton.addActionListener { SwingUtils.expandOrCollapseNode(apiTree, false) }

        toolBar.add(refreshButton)
        toolBar.add(collapseButton)
        toolBar.addSeparator()
        toolBar.add(JLabel("Search: "))
        toolBar.add(searchField)
        searchField.preferredSize = Dimension(200, searchField.preferredSize.height)

        // Create main content panel with toolbar and tree
        val contentPanel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            background = UIUtil.getPanelBackground()
            add(toolBar, BorderLayout.NORTH)
            val treeScrollPane = JBScrollPane(apiTree).apply {
                background = UIUtil.getTreeBackground()
                viewport.background = UIUtil.getTreeBackground()
                border = JBUI.Borders.empty()
            }
            add(treeScrollPane, BorderLayout.CENTER)
        }

        // Create request details panel
        val requestDetailsPanel = JPanel(BorderLayout()).apply {
            // Top panel containing method, URL and send button
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(methodComboBox)
                add(Box.createRigidArea(Dimension(5, 0)))
                add(hostComboBox)
                add(Box.createRigidArea(Dimension(5, 0)))
                add(urlField)
                add(Box.createRigidArea(Dimension(5, 0)))
                add(sendButton)
            }, BorderLayout.NORTH)

            // Center panel containing content type and request body
            add(JPanel(BorderLayout()).apply {
                // Content type selection at the top
                add(JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.X_AXIS)
                    add(JLabel("Content Type:"))
                    add(Box.createRigidArea(Dimension(5, 0)))
                    add(contentTypeComboBox)
                }, BorderLayout.NORTH)

                // Request tabs taking up the remaining space
                add(JTabbedPane().apply {
                    addTab("Headers", JBScrollPane(headerArea))
                    addTab("Params", JBScrollPane(paramsTable))
                    addTab("Form", JBScrollPane(formTable))
                    addTab("Body", JBScrollPane(bodyArea))
                }, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
        }

        // Create response panel
        val responsePanel = JPanel(BorderLayout()).apply {
            // Response toolbar
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(JLabel("Response"))
                add(Box.createHorizontalGlue())
                add(statusPanel)
                add(Box.createRigidArea(Dimension(10, 0)))
                add(saveButton)
            }, BorderLayout.NORTH)

            // Response content
            add(JTabbedPane().apply {
                addTab("Body", JBScrollPane(responseArea))
                addTab("Raw", JBScrollPane(rawResponseArea))
                addTab("Headers", JBScrollPane(responseHeadersTextArea))
            }, BorderLayout.CENTER)
        }

        // Create split pane for request details and response
        val splitPane = com.intellij.ui.JBSplitter(true, 0.5f).apply {
            firstComponent = requestDetailsPanel
            secondComponent = responsePanel
        }

        // Create main split pane
        val mainSplitPane = com.intellij.ui.JBSplitter(true, 0.3f).apply {
            firstComponent = contentPanel
            secondComponent = splitPane
        }

        // Add to main panel
        add(mainSplitPane, BorderLayout.CENTER)

        // Set preferred size for the whole panel
        preferredSize = JBUI.size(800, 600)
    }

    private fun setupTree() {
        apiTree.isRootVisible = false
        apiTree.showsRootHandles = true
        apiTree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        apiTree.background = UIUtil.getTreeBackground()
        apiTree.foreground = UIUtil.getTreeForeground()
        apiTree.isOpaque = true

        // Set cell renderer
        val renderer = EasyApiTreeCellRenderer().apply {
            background = UIUtil.getTreeBackground()
            foreground = UIUtil.getTreeForeground()
            textSelectionColor = UIUtil.getTreeSelectionForeground(true)
            textNonSelectionColor = UIUtil.getTreeForeground()
            backgroundSelectionColor = UIUtil.getTreeSelectionBackground(true)
            backgroundNonSelectionColor = UIUtil.getTreeBackground()
            isOpaque = true
        }
        apiTree.cellRenderer = renderer

        // Setup popup menu
        val refreshItem = JMenuItem("Refresh from Source Code")
        refreshItem.icon = EasyIcons.Refresh
        refreshItem.addActionListener {
            val node = apiTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is ProjectNodeData) {
                actionContext.runAsync {
                    refreshProjectNodeData(userObject)
                }
            }
        }

        val navigateToSourceItem = JMenuItem("Navigate to Source")
        navigateToSourceItem.icon = EasyIcons.Link
        navigateToSourceItem.addActionListener {
            val node = apiTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is ProjectNodeData) {
                userObject.navigateToSource()
            }
        }

        val resetItem = JMenuItem("Reset")
        resetItem.icon = EasyIcons.Reset
        resetItem.addActionListener {
            val node = apiTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is ProjectNodeData) {
                actionContext.runAsync {
                    userObject.reset()
                    actionContext.runInSwingUI {
                        onSelected(userObject)
                    }
                }
            }
        }

        // Add export menu
        val exportMenu = JMenu("Export")
        exportMenu.icon = EasyIcons.Export

        // Add Yapi export item
        val yapiExportItem = JMenuItem("Export to Yapi")
        yapiExportItem.icon = EasyIcons.Export
        yapiExportItem.addActionListener {
            LOG.info("Export to Yapi")
            val node = apiTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is ProjectNodeData) {
                actionContext.runAsync {
                    userObject.exportToYapi(actionContext)
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            }
        }

        // Add Postman export item
        val postmanExportItem = JMenuItem("Export to Postman")
        postmanExportItem.icon = EasyIcons.Export
        postmanExportItem.addActionListener {
            LOG.info("Export to Postman")
            val node = apiTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is ProjectNodeData) {
                actionContext.runAsync {
                    userObject.exportToPostman(actionContext)
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            }
        }

        // Add Markdown export item
        val markdownExportItem = JMenuItem("Export to Markdown")
        markdownExportItem.icon = EasyIcons.Export
        markdownExportItem.addActionListener {
            LOG.info("Export to Markdown")
            val node = apiTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is ProjectNodeData) {
                actionContext.runAsync {
                    userObject.exportToMarkdown(actionContext)
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            }
        }

        // Add Curl export item
        val curlExportItem = JMenuItem("Export to Curl")
        curlExportItem.icon = EasyIcons.Export
        curlExportItem.addActionListener {
            LOG.info("Export to Curl")
            val node = apiTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is ProjectNodeData) {
                actionContext.runAsync {
                    userObject.exportToCurl(actionContext)
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            }
        }

        // Add HttpClient export item
        val httpClientExportItem = JMenuItem("Export to HttpClient")
        httpClientExportItem.icon = EasyIcons.Export
        httpClientExportItem.addActionListener {
            LOG.info("Export to HttpClient")
            val node = apiTree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            if (userObject is ProjectNodeData) {
                actionContext.runAsync {
                    userObject.exportToHttpClient(actionContext)
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            }
        }

        // Add export items to export menu
        exportMenu.add(yapiExportItem)
        exportMenu.add(postmanExportItem)
        exportMenu.add(markdownExportItem)
        exportMenu.add(curlExportItem)
        exportMenu.add(httpClientExportItem)

        apiPopupMenu.add(refreshItem)
        apiPopupMenu.add(navigateToSourceItem)
        apiPopupMenu.add(resetItem)
        apiPopupMenu.addSeparator()
        apiPopupMenu.add(exportMenu)

        // Add mouse listener for popup menu
        apiTree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = apiTree.getPathForLocation(e.x, e.y) ?: return

                    val targetComponent = path.lastPathComponent
                    val projectNodeData =
                        (targetComponent as? DefaultMutableTreeNode)?.userObject as? ProjectNodeData ?: return

                    // Check if popup menu is enabled for this node
                    if (!projectNodeData.popupEnable()) {
                        return
                    }

                    // Enable/disable menu items based on node capabilities
                    refreshItem.isEnabled = projectNodeData.refreshEnable()
                    navigateToSourceItem.isEnabled = projectNodeData.isNavigatable()
                    resetItem.isEnabled = projectNodeData.resetEnable()

                    // Show popup and update selection
                    apiPopupMenu.show(apiTree, e.x, e.y)
                    apiTree.selectionPath = path
                }
            }
        })
    }

    private fun refreshProjectNodeData(nodeData: ProjectNodeData) {
        val refreshedProjectNodeData = service.refreshApis(nodeData)
        if (refreshedProjectNodeData == null) {
            if (nodeData.isRoot) {
                this.apis = this.apis.filter { it != nodeData }
                nodeData.removeFromParent()
                SwingUtilities.invokeLater {
                    apiTree.model.reload()
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            } else {
                val parentNodeData = nodeData.parentNodeData!!
                nodeData.removeFromParent()
                SwingUtilities.invokeLater {
                    apiTree.model.reload(parentNodeData.asTreeNode())
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            }
            return
        } else {
            if (nodeData.isRoot) {
                this.apis = this.apis.map { if (it == nodeData) refreshedProjectNodeData else it }
                updateApiTreeData()
                actionContext.call(ActionKeys.ACTION_COMPLETED)
            } else {
                val parentNodeData = nodeData.parentNodeData!!
                parentNodeData.replaceSubNodeData(nodeData, refreshedProjectNodeData)
                SwingUtilities.invokeLater {
                    apiTree.model.reload(parentNodeData.asTreeNode())
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            }
        }
    }

    private fun setupListeners() {
        // Add tree selection listener
        apiTree.addTreeSelectionListener { e ->
            val node = e.path.lastPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject
            onSelected(userObject)
        }

        // Add content type change listener
        contentTypeComboBox.addActionListener {
            if (contentTypeChangeThrottle.acquire(300)) {
                val selectedContentType = contentTypeComboBox.selectedItem as? String
                val newHeaders = changeHeaderForContentType(selectedContentType)
                if (newHeaders != null) {
                    service.updateCurrentRequest(headers = newHeaders)
                    headerArea.text = newHeaders
                }
            }
        }

        // Add send button listener
        sendButton.addActionListener {
            onCallClick()
        }

        // Add save button listener
        saveButton.addActionListener {
            currentResponse?.let { service.saveResponse(responseArea.text, it) }
        }

        // Add search field listener
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = scheduleFilter()
            override fun removeUpdate(e: DocumentEvent) = scheduleFilter()
            override fun changedUpdate(e: DocumentEvent) = scheduleFilter()

            private fun scheduleFilter() {
                searchDebounceTimer.restart()
            }
        })
    }

    private fun onSelected(userObject: Any?) {
        if (userObject is ApiDashboardService.ApiNodeData) {
            onApiSelected(userObject)
        }
    }

    private fun onApiSelected(userObject: ApiDashboardService.ApiNodeData) {
        // Load saved request content or create new one
        actionContext.runAsync {
            val requestContent = service.loadRequestContent(userObject.request)
            service.setCurrentRequest(requestContent)
            SwingUtilities.invokeLater {
                // Update request details from saved content
                methodComboBox.selectedItem = requestContent.method ?: "GET"
                urlField.text = requestContent.path ?: ""
                contentTypeComboBox.selectedItem = requestContent.contentType()
                headerArea.text = requestContent.headers ?: ""
                bodyArea.text = requestContent.body ?: ""
                formatForm(requestContent)
                formatParams(requestContent)

                // Clear response area and reset response-related UI elements
                responseArea.text = ""
                rawResponseArea.text = ""
                responseHeadersTextArea.text = ""
                statusLabel.text = "unknown"
                currentResponse = null
                saveButton.isEnabled = false
                statusPanel.isVisible = false

                // Add listeners for content changes
                setupContentChangeListeners()
            }
        }
    }

    private fun setupContentChangeListeners() {
        // Remove existing listeners to avoid duplicates
        methodComboBox.actionListeners.forEach { methodComboBox.removeActionListener(it) }
        urlField.document.removeDocumentListener(urlFieldListener)
        headerArea.document.removeDocumentListener(headerAreaListener)
        bodyArea.document.removeDocumentListener(bodyAreaListener)

        // Add new listeners
        methodComboBox.addActionListener {
            service.updateCurrentRequest(method = methodComboBox.selectedItem as String)
        }

        urlField.document.addDocumentListener(urlFieldListener)
        headerArea.document.addDocumentListener(headerAreaListener)
        bodyArea.document.addDocumentListener(bodyAreaListener)
    }

    private val urlFieldListener = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = updatePath()
        override fun removeUpdate(e: DocumentEvent) = updatePath()
        override fun changedUpdate(e: DocumentEvent) = updatePath()
        private fun updatePath() {
            service.updateCurrentRequest(path = urlField.text)
        }
    }

    private val headerAreaListener = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = updateHeaders()
        override fun removeUpdate(e: DocumentEvent) = updateHeaders()
        override fun changedUpdate(e: DocumentEvent) = updateHeaders()
        private fun updateHeaders() {
            if (contentTypeChangeThrottle.acquire(300)) {
                service.updateCurrentRequest(headers = headerArea.text)
            }
        }
    }

    private val bodyAreaListener = object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = updateBody()
        override fun removeUpdate(e: DocumentEvent) = updateBody()
        override fun changedUpdate(e: DocumentEvent) = updateBody()
        private fun updateBody() {
            service.updateCurrentRequest(body = bodyArea.text)
        }
    }

    private fun formatForm(requestContent: ApiDashboardService.RequestRawInfo) {
        val findFormTableBinder = findFormTableBinder(requestContent.contentType())
        changeFormTableBinder(findFormTableBinder)
        findFormTableBinder.refreshTable(formTable, requestContent)
    }

    private fun formatParams(requestContent: ApiDashboardService.RequestRawInfo) {
        queryParamsTableBinder.init(this)
        queryParamsTableBinder.refreshTable(paramsTable, requestContent)
    }

    private fun findFormTableBinder(contentType: String?): ParamsTableBinder<FormParam> {
        val formTableBinder: ParamsTableBinder<FormParam> = when {
            contentType.isNullOrBlank() -> disabledFormTableBinder
            contentType.contains("application/x-www-form-urlencoded") -> noTypedFormTableBinder
            contentType.contains("multipart/form-data") -> typedFormTableBinder
            else -> disabledFormTableBinder
        }

        formTableBinder.init(this)
        return formTableBinder
    }

    private fun changeFormTableBinder(formTableBinder: ParamsTableBinder<FormParam>) {
        if (this.formTableBinder != formTableBinder) {
            this.formTableBinder.cleanTable(this.formTable)
            this.formTableBinder = formTableBinder
        }
    }

    private fun readParamsFromTable(): List<Param> {
        return queryParamsTableBinder.readParams(paramsTable, false)
    }

    private fun buildQueryString(params: List<Param>?): String {
        if (params.isNullOrEmpty()) return ""
        return params.joinToString("&") { param ->
            "${param.name}=${param.value ?: ""}"
        }
    }

    private fun onCallClick() {
        val method = methodComboBox.selectedItem as String
        val host = hostComboBox.selectedItem as String
        val url = urlField.text
        val headers = parseHeaders(headerArea.text)
        val formParams = formTableBinder.readAvailableParams(formTable)
        val body = bodyArea.text.takeIf { it.isNotBlank() }
        val queryParams = readParamsFromTable()
        val queryString = buildQueryString(queryParams)

        responseArea.text = "Sending request..."
        actionContext.runAsync {
            try {
                onNewHost(host)
                val response = service.sendRequest(
                    host = host,
                    path = RequestUtils.addQuery(url, queryString),
                    method = method,
                    headers = headers,
                    formParams = formParams,
                    body = body
                )
                val formattedResponse = service.formatResponse(response)
                val headerText = response.headers()?.joinToString("\n") { "${it.name()}: ${it.value()}" }
                SwingUtilities.invokeLater {
                    currentResponse = response
                    saveButton.isEnabled = true
                    responseArea.text = formattedResponse
                    rawResponseArea.text = response.string() ?: ""
                    responseHeadersTextArea.text = headerText
                    statusLabel.text = response.code().toString()
                    statusPanel.isVisible = true
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    currentResponse = null
                    saveButton.isEnabled = false
                    responseArea.text = "Error: ${e.message}"
                    rawResponseArea.text = ""
                    statusLabel.text = "Error"
                    statusPanel.isVisible = true
                    actionContext.call(ActionKeys.ACTION_COMPLETED)
                }
            }
        }
    }

    interface ParamsTableBinder<T : NamedValue<*>> {
        fun refreshTable(formTable: JBTable, request: ApiDashboardService.RequestRawInfo?)

        fun readParams(formTable: JBTable, onlyAvailable: Boolean): List<T>

        fun readAvailableParams(formTable: JBTable): List<T>? {
            return readParams(formTable, true)
        }

        fun cleanTable(formTable: JBTable) {}

        fun init(panel: ApiDashboardPanel) {}
    }

    abstract class AbstractTableBinder<T : NamedValue<*>> : ParamsTableBinder<T> {
        protected fun optionTableColumn(): TableColumn {
            val tableColumn = TableColumn()
            setUpBooleanTableColumn(tableColumn)
            return tableColumn
        }

        protected fun setUpBooleanTableColumn(tableColumn: TableColumn) {
            tableColumn.headerRenderer = BooleanTableCellRenderer()
            tableColumn.cellEditor = BooleanTableCellEditor()
            tableColumn.cellRenderer = BooleanTableCellRenderer()
            tableColumn.maxWidth = 50
        }

        protected fun textTableColumn(): TableColumn {
            val tableColumn = TableColumn()
            tableColumn.cellEditor = DefaultCellEditor(JTextField())
            return tableColumn
        }

        private var headerAllSelectListener: MouseListener? = null
        private val headerClickThrottle = throttle()

        protected fun listenHeaderAllSelectAction(formTable: JBTable, columnIndex: Int) {
            val tableHeader = formTable.tableHeader

            if (headerAllSelectListener != null) {
                tableHeader.removeMouseListener(headerAllSelectListener)
            }

            var allSelected = false
            headerAllSelectListener = object : MouseListener {
                override fun mouseReleased(e: MouseEvent?) {}
                override fun mouseEntered(e: MouseEvent?) {}
                override fun mouseClicked(e: MouseEvent?) {
                    if (e == null || !headerClickThrottle.acquire(100)) {
                        return
                    }

                    val selectColumn = formTable.tableHeader.columnAtPoint(e.point)
                    if (selectColumn == columnIndex) {
                        allSelected = !allSelected

                        formTable.columnModel.getColumn(columnIndex).headerValue = allSelected
                        for (rowIndex in 0 until formTable.rowCount) {
                            formTable.setValueAt(allSelected, rowIndex, columnIndex)
                        }
                    }
                }

                override fun mouseExited(e: MouseEvent?) {}
                override fun mousePressed(e: MouseEvent?) {}
            }

            tableHeader.addMouseListener(headerAllSelectListener)
        }

        override fun readParams(formTable: JBTable, onlyAvailable: Boolean): ArrayList<T> {
            return readParams(formTable.model, onlyAvailable)
        }

        protected fun readParams(model: TableModel, onlyAvailable: Boolean): ArrayList<T> {
            val params: ArrayList<T> = ArrayList()
            for (row in 0 until model.rowCount) {
                readParamFromRow(model, row, onlyAvailable)?.let { params.add(it) }
            }
            return params
        }

        abstract fun readParamFromRow(tableModel: TableModel, row: Int, onlyAvailable: Boolean): T?

        private var tableModelListener: TableModelListener? = null
        private var tableMouseListener: MouseListener? = null

        fun refreshTable(formTable: JBTable, params: MutableList<T>?) {
            if (tableModelListener != null) {
                formTable.model.removeTableModelListener(tableModelListener)
                this.tableModelListener = null
            }
            formTable.removeAll()
            (formTable.model as DefaultTableModel).columnCount = 0
            (formTable.model as DefaultTableModel).rowCount = 0

            val model = buildTableModel(formTable, params)
            formTable.model = model
            setUpTableModel(formTable)

            tableModelListener = TableModelListener {
                try {
                    if (model === formTable.model && model.rowCount > 0) {
                        onTableChanged(model)
                    }
                } catch (_: Exception) {
                }
            }
            model.addTableModelListener(tableModelListener)

            if (tableMouseListener != null) {
                formTable.removeMouseListener(tableMouseListener)
                this.tableMouseListener = null
            }

            val tablePopMenu = JPopupMenu()
            val insertRowItem = JMenuItem("Insert Row")
            insertRowItem.addActionListener {
                insertRow(formTable)
            }
            tablePopMenu.add(insertRowItem)

            tableMouseListener = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    if (e == null) return
                    if (SwingUtilities.isRightMouseButton(e)) {
                        tablePopMenu.show(e.component!!, e.x, e.y)
                    }
                }
            }
            formTable.addMouseListener(tableMouseListener)
        }

        protected open fun onTableChanged(model: TableModel) {}

        protected open fun insertRow(formTable: JBTable) {
            val row = formTable.selectedRow
            val params = readParams(formTable, false)
            params.add(row + 1, createEmptyParam())
            refreshTable(formTable, params)
        }

        abstract fun createEmptyParam(): T

        abstract fun buildTableModel(formTable: JBTable, params: MutableList<T>?): TableModel

        abstract fun setUpTableModel(formTable: JBTable)

        override fun cleanTable(formTable: JBTable) {
            if (tableModelListener != null) {
                formTable.model.removeTableModelListener(tableModelListener)
                this.tableModelListener = null
            }
        }
    }

    abstract class AbstractFormTableBinder : AbstractTableBinder<FormParam>() {
        override fun refreshTable(formTable: JBTable, request: ApiDashboardService.RequestRawInfo?) {
            super.refreshTable(formTable, request?.formParams)
        }

        override fun createEmptyParam(): FormParam = FormParam()
    }

    class DisabledFormTableBinder : ParamsTableBinder<FormParam> {
        override fun refreshTable(formTable: JBTable, request: ApiDashboardService.RequestRawInfo?) {
            formTable.removeAll()
            (formTable.model as DefaultTableModel).dataVector.clear()
            formTable.repaint()
        }

        override fun readParams(formTable: JBTable, onlyAvailable: Boolean): List<FormParam> {
            return emptyList()
        }

        override fun init(panel: ApiDashboardPanel) {
            // No initialization needed
        }
    }

    inner class NoTypedFormTableBinder : AbstractFormTableBinder() {
        override fun readParamFromRow(tableModel: TableModel, row: Int, onlyAvailable: Boolean): FormParam? {
            val required = tableModel.getValueAt(row, 0) as Boolean
            if (required || !onlyAvailable) {
                val param = FormParam()
                param.required = required
                param.name = tableModel.getValueAt(row, 1).toString()
                param.value = tableModel.getValueAt(row, 2).toString()
                return param
            }
            return null
        }

        override fun buildTableModel(formTable: JBTable, params: MutableList<FormParam>?): TableModel {
            formTable.addColumn(optionTableColumn())
            formTable.addColumn(textTableColumn())
            formTable.addColumn(textTableColumn())

            val columns = arrayOf("", "name", "value")
            val data: ArrayList<Array<Any>> = ArrayList()

            params?.forEach { param ->
                data.add(arrayOf(param.required ?: true, param.name ?: "", param.value ?: ""))
            }

            return DefaultTableModel(data.toTypedArray(), columns)
        }

        override fun setUpTableModel(formTable: JBTable) {
            setUpBooleanTableColumn(formTable.findColumn(0)!!)
            listenHeaderAllSelectAction(formTable, 0)
        }

        override fun onTableChanged(model: TableModel) {
            val formParams = readParams(model, false)
            service.updateCurrentRequest(formParams = formParams)
        }
    }

    inner class TypedFormTableBinder : AbstractFormTableBinder() {
        private fun typeTableColumn(): TableColumn {
            val tableColumn = TableColumn()
            tableColumn.cellEditor = object : ComboBoxCellEditor() {
                override fun getComboBoxItems(): MutableList<String> {
                    return arrayListOf("text", "file")
                }
            }
            return tableColumn
        }

        override fun readParamFromRow(tableModel: TableModel, row: Int, onlyAvailable: Boolean): FormParam? {
            val required = tableModel.getValueAt(row, 0) as Boolean
            if (required || !onlyAvailable) {
                val param = FormParam()
                param.required = required
                param.name = tableModel.getValueAt(row, 1).toString()
                param.type = tableModel.getValueAt(row, 2).toString()
                param.value = tableModel.getValueAt(row, 3).toString()
                return param
            }
            return null
        }

        override fun buildTableModel(formTable: JBTable, params: MutableList<FormParam>?): TableModel {
            formTable.addColumn(optionTableColumn())
            formTable.addColumn(textTableColumn())
            formTable.addColumn(typeTableColumn())
            formTable.addColumn(textTableColumn())

            val columns = arrayOf("", "name", "type", "value")
            val data: ArrayList<Array<Any>> = ArrayList()

            params?.forEach { param ->
                data.add(arrayOf(param.required ?: true, param.name ?: "", param.type ?: "text", param.value ?: ""))
            }

            return DefaultTableModel(data.toTypedArray(), columns)
        }

        override fun setUpTableModel(formTable: JBTable) {
            setUpBooleanTableColumn(formTable.findColumn(0)!!)

            formTable.columnModel.getColumn(2).cellEditor = object : ComboBoxCellEditor() {
                override fun getComboBoxItems(): MutableList<String> {
                    return arrayListOf("text", "file")
                }
            }

            listenHeaderAllSelectAction(formTable, 0)
            addFileSelectListener(formTable)
        }

        override fun cleanTable(formTable: JBTable) {
            if (fileSelectListener != null) {
                formTable.removeMouseListener(fileSelectListener)
                fileSelectListener = null
            }
            super.cleanTable(formTable)
        }

        private var fileSelectListener: MouseListener? = null
        private val fileSelectThrottle = throttle()

        private fun addFileSelectListener(formTable: JBTable) {
            if (fileSelectListener != null) {
                formTable.removeMouseListener(fileSelectListener)
            }
            fileSelectListener = object : MouseListener {
                override fun mouseReleased(e: MouseEvent?) {}
                override fun mouseEntered(e: MouseEvent?) {}
                override fun mouseClicked(e: MouseEvent?) {
                    val column = formTable.selectedColumn
                    if (column != 3) {//only third column can select file
                        return
                    }
                    val row = formTable.selectedRow
                    if (row == -1) {
                        return
                    }
                    try {
                        val type = formTable.getValueAt(row, 2).toString()
                        if (type != "file") {//the type of param should be 'file'
                            return
                        }
                    } catch (e: ArrayIndexOutOfBoundsException) {//error to get type
                        return
                    }

                    if (fileSelectThrottle.acquire(1000)) {
                        actionContext.instance(FileSelectHelper::class).selectFile { file ->
                            formTable.setValueAt(file?.path, row, column)
                        }
                    }
                    formTable.selectionModel.clearSelection()
                }

                override fun mouseExited(e: MouseEvent?) {}
                override fun mousePressed(e: MouseEvent?) {}
            }
            formTable.addMouseListener(fileSelectListener)
        }

        override fun onTableChanged(model: TableModel) {
            val formParams = readParams(model, false)
            service.updateCurrentRequest(formParams = formParams)
        }
    }

    inner class QueryParamsTableBinder : AbstractTableBinder<Param>() {
        override fun refreshTable(formTable: JBTable, request: ApiDashboardService.RequestRawInfo?) {
            val params = if (request?.querys.isNullOrBlank()) {
                null
            } else {
                request!!.querys!!.split("&")
                    .mapNotNull { param ->
                        val parts = param.split("=", limit = 2)
                        if (parts.size == 2) {
                            Param().apply {
                                name = parts[0]
                                value = parts[1]
                                required = true
                            }
                        } else null
                    }.toMutableList()
            }
            super.refreshTable(formTable, params)
        }

        private var currentRequest: ApiDashboardService.RequestRawInfo? = null

        override fun readParamFromRow(tableModel: TableModel, row: Int, onlyAvailable: Boolean): Param? {
            val required = tableModel.getValueAt(row, 0) as Boolean
            if (required || !onlyAvailable) {
                val param = Param()
                param.required = required
                param.name = tableModel.getValueAt(row, 1).toString()
                param.value = tableModel.getValueAt(row, 2).toString()
                return param
            }
            return null
        }

        override fun createEmptyParam(): Param = Param()

        override fun buildTableModel(formTable: JBTable, params: MutableList<Param>?): TableModel {
            formTable.addColumn(optionTableColumn())
            formTable.addColumn(textTableColumn())
            formTable.addColumn(textTableColumn())

            val columns = arrayOf("", "name", "value")
            val data: ArrayList<Array<Any>> = ArrayList()

            params?.forEach { param ->
                data.add(arrayOf(param.required ?: true, param.name ?: "", param.value ?: ""))
            }

            return DefaultTableModel(data.toTypedArray(), columns)
        }

        override fun setUpTableModel(formTable: JBTable) {
            setUpBooleanTableColumn(formTable.findColumn(0)!!)
            listenHeaderAllSelectAction(formTable, 0)
        }

        override fun onTableChanged(model: TableModel) {
            val params = readParams(model, false)
            service.updateCurrentRequest(querys = buildQueryString(params))
        }
    }

    private fun filterTree() {
        val searchText = searchField.text.lowercase()
        if (searchText.isEmpty()) {
            updateApiTreeData()
            return
        }

        val filteredRoot = DefaultMutableTreeNode("APIs")
        apis.forEach { moduleData ->
            val filteredNode = createFilteredNode(moduleData, searchText)
            if (filteredNode != null) {
                filteredRoot.add(filteredNode)
            }
        }

        // Update tree with filtered model
        apiTree.model = DefaultTreeModel(filteredRoot)
        SwingUtils.expandOrCollapseNode(apiTree, true)
    }

    private fun createFilteredNode(nodeData: ProjectNodeData, searchText: String): DefaultMutableTreeNode? {
        // If this node matches the search, include it and all its children
        if (matchesSearch(nodeData, searchText)) {
            return nodeData.asTreeNode()
        }

        // If this node has children, check them recursively
        val subNodes = nodeData.getSubNodeData()
        if (subNodes.isNullOrEmpty()) {
            return null
        }

        val filteredNode by lazy { DefaultMutableTreeNode(nodeData) }
        var hasMatchingChildren = false

        subNodes.forEach { childData ->
            val filteredChild = createFilteredNode(childData, searchText)
            if (filteredChild != null) {
                filteredNode.add(filteredChild)
                hasMatchingChildren = true
            }
        }

        return if (hasMatchingChildren) filteredNode else null
    }

    private fun matchesSearch(nodeData: ProjectNodeData, searchText: String): Boolean {
        return nodeData.text.lowercase().contains(searchText) || nodeData.tooltip?.lowercase()
            ?.contains(searchText) == true
    }

    private fun parseHeaders(headerText: String): List<Header> {
        return headerText.lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    Header(name = parts[0].trim(), value = parts[1].trim())
                } else {
                    null
                }
            }
    }

    fun updateApis(apis: List<ProjectNodeData>) {
        this.apis = extractedOptimizedApis(apis)
        updateApiTreeData()
        actionContext.call(ActionKeys.ACTION_COMPLETED)
    }

    private fun extractedOptimizedApis(apis: List<ProjectNodeData>): List<ProjectNodeData> {
        if (apis.size == 1) {
            val root = apis.first()
            val subNodeData = root.getSubNodeData()
            if (subNodeData != null) {
                val optimizedApis = ArrayList(subNodeData)
                optimizedApis.forEach { it.removeFromParent() }
                return optimizedApis
            }
        }
        return apis
    }

    private fun updateApiTreeData() {
        SwingUtilities.invokeLater {
            val root = DefaultMutableTreeNode("APIs")
            apis.forEach { nodeData ->
                root.add(nodeData.asTreeNode())
            }
            apiTree.model = DefaultTreeModel(root)
        }
    }

    private fun onNewHost(host: String) {
        service.addHost(host)
        refreshHosts()
    }

    private fun refreshHosts() {
        val hosts = service.getHosts()
        SwingUtilities.invokeLater {
            hostComboBox.model = DefaultComboBoxModel(hosts.toTypedArray())
        }
    }

    private fun changeHeaderForContentType(contentType: String?): String? {
        val header = headerArea.text
        if (contentType.isNullOrBlank()) return header

        val newHeader = StringBuilder()
        var found = false
        if (header.isNotBlank()) {
            for (line in header.lines()) {
                if (line.isBlank()) {
                    continue
                }
                if (line.trim().startsWith("Content-Type", ignoreCase = true)) {
                    val value = line.substringAfter(":", "").trim()
                    if (value.contains(contentType)) {
                        return header
                    } else {
                        found = true
                        newHeader.appendLine("Content-Type: $contentType")
                    }
                } else {
                    newHeader.appendLine(line)
                }
            }
        }
        if (!found) {
            if (newHeader.isNotEmpty()) {
                newHeader.appendLine()
            }
            newHeader.appendLine("Content-Type: $contentType")
        }
        return newHeader.toString().trimEnd()
    }

    fun navigateToClass(psiClass: PsiClass) {
        val root = (apiTree.model.root as? DefaultMutableTreeNode) ?: return
        val classNode = findClassNode(root, psiClass)
        if (classNode != null) {
            val path = TreePath(classNode.path)
            apiTree.selectionPath = path
            apiTree.scrollPathToVisible(path)
        }
    }

    private fun findClassNode(root: DefaultMutableTreeNode, targetClass: PsiClass): DefaultMutableTreeNode? {
        val enumeration = root.depthFirstEnumeration()
        while (enumeration.hasMoreElements()) {
            val node = enumeration.nextElement() as DefaultMutableTreeNode
            val userObject = node.userObject
            if (userObject is ApiDashboardService.ClassNodeData && userObject.psiClass == targetClass) {
                return node
            }
        }
        return null
    }

    override fun dispose() {
        searchDebounceTimer.stop()
        service.dispose()
    }
}
