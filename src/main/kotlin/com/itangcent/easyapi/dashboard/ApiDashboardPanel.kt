package com.itangcent.easyapi.dashboard

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.itangcent.easyapi.cache.ApiIndex
import com.itangcent.easyapi.cache.ApiIndexManager
import com.itangcent.easyapi.exporter.ApiExporterRegistry
import com.itangcent.easyapi.exporter.ExportOrchestrator
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ExportFormat
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.ide.dialog.ExportDialog
import com.itangcent.easyapi.ide.support.NotificationUtils
import com.itangcent.easyapi.ide.support.runWithProgress
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.swing
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.logging.IdeaLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Main dashboard panel for displaying and managing API endpoints in a tree structure.
 * 
 * This panel provides:
 * - A tree view of all API endpoints organized by module/class
 * - Search functionality with debounced filtering
 * - Export capabilities to various formats
 * - Navigation to source code
 * - Context menu with copy and export options
 * 
 * @param project The IntelliJ IDEA project context
 */
class ApiDashboardPanel(private val project: Project) : JPanel(BorderLayout()), IdeaLog {

    /** Index for accessing cached API endpoint data */
    private val apiIndex = ApiIndex.getInstance(project)

    /** Manager for triggering API index scans */
    private val apiIndexManager = ApiIndexManager.getInstance(project)

    /** Tree model backing the API tree view */
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Loading..."))

    /** Tree component displaying the API hierarchy */
    private val apiTree: Tree = Tree(treeModel)

    /** Search input field for filtering endpoints */
    private val searchField = JTextField()

    /** Panel for displaying details of the selected endpoint */
    private val endpointDetailsPanel: EndpointDetailsPanel

    /** Cached list of all endpoints for filtering operations */
    private var cachedEndpoints: List<ApiEndpoint> = emptyList()

    /** Timer for debouncing search input to avoid excessive filtering */
    private var searchDebounceTimer: Timer? = null

    /**
     * Initializes the panel with HTTP client, UI components, tree listeners, and API data.
     */
    init {
        val httpClient = HttpClientProvider.getInstance(ActionContext.shared).getClient()
        endpointDetailsPanel = EndpointDetailsPanel(project, httpClient)
        setupUI()
        setupTreeListeners()
        setupApis()
    }

    /**
     * Sets up the main UI layout including toolbar, tree panel, and details panel.
     * Uses a splitter to allow resizing between the tree and details views.
     */
    private fun setupUI() {
        background = UIUtil.getPanelBackground()

        val toolbar = createToolbar()
        val treePanel = createTreePanel()
        val rightPanel = createRightPanel()

        val mainSplitter = JBSplitter(false, 0.25f).apply {
            firstComponent = treePanel
            secondComponent = rightPanel
            dividerWidth = 3
        }

        add(toolbar, BorderLayout.NORTH)
        add(mainSplitter, BorderLayout.CENTER)

        preferredSize = JBUI.size(900, 650)
    }

    /**
     * Creates the toolbar with action buttons and search field.
     * Includes refresh, export, collapse/expand actions and a debounced search input.
     * 
     * @return The configured toolbar component
     */
    private fun createToolbar(): JComponent {
        val toolBar = JToolBar().apply {
            isFloatable = false
            background = UIUtil.getPanelBackground()
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
        }

        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            add(ExportAction())
            addSeparator()
            add(CollapseAllAction())
            add(ExpandAllAction())
        }

        val actionToolbar = ActionManager.getInstance().createActionToolbar("ApiDashboardToolbar", actionGroup, true)
        actionToolbar.targetComponent = this
        toolBar.add(actionToolbar.component)

        toolBar.addSeparator()
        toolBar.add(Box.createRigidArea(Dimension(5, 0)))
        toolBar.add(JLabel("Search: "))
        searchField.preferredSize = Dimension(200, searchField.preferredSize.height)
        searchField.maximumSize = Dimension(300, searchField.preferredSize.height)
        toolBar.add(searchField)

        searchDebounceTimer = Timer(300) { filterTree() }
        searchDebounceTimer?.isRepeats = false
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent) {
                searchDebounceTimer?.start()
            }
        })

        return toolBar
    }

    /**
     * Creates the tree panel with scroll pane for displaying the API hierarchy.
     * Configures tree appearance, selection mode, and custom cell renderer.
     * 
     * @return The configured tree panel with scroll capabilities
     */
    private fun createTreePanel(): JComponent {
        apiTree.apply {
            isRootVisible = true
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            background = UIUtil.getTreeBackground()
            cellRenderer = ApiTreeCellRenderer()
            rowHeight = 22
        }

        return JBScrollPane(apiTree).apply {
            preferredSize = Dimension(280, 400)
            border = BorderFactory.createEmptyBorder()
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }
    }

    /**
     * Creates the right panel for displaying endpoint details.
     * 
     * @return The endpoint details panel
     */
    private fun createRightPanel(): JComponent {
        return endpointDetailsPanel
    }

    /**
     * Sets up tree selection and mouse event listeners.
     * - Selection listener: Updates details panel when endpoint is selected
     * - Mouse listener: Handles right-click context menu and double-click navigation
     */
    private fun setupTreeListeners() {
        apiTree.addTreeSelectionListener { event ->
            val node = event.path?.lastPathComponent as? DefaultMutableTreeNode
            val endpoint = node?.userObject as? ApiEndpoint
            if (endpoint != null) {
                endpointDetailsPanel.showEndpoint(endpoint)
            } else {
                endpointDetailsPanel.clear()
            }
        }

        apiTree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopupMenu(e)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopupMenu(e)
                }
            }

            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = apiTree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
                    val endpoint = node?.userObject as? ApiEndpoint
                    if (endpoint != null) {
                        navigateToSource(endpoint)
                    }
                }
            }
        })
    }

    /**
     * Displays a context-sensitive popup menu based on the clicked node type.
     * - For endpoints: Shows export, copy, and navigation options
     * - For folder/class nodes: Shows batch export and expand/collapse options
     * - For root: Shows export all and expand/collapse options
     * 
     * @param e The mouse event that triggered the popup
     */
    private fun showPopupMenu(e: MouseEvent) {
        val path = apiTree.getPathForLocation(e.x, e.y) ?: return
        apiTree.selectionPath = path

        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val endpoint = node.userObject as? ApiEndpoint
        val nodeInfo = node.userObject as? NodeInfo

        val popupMenu = JPopupMenu()

        if (endpoint != null) {
            val exportMenu = JMenu("Export")
            addExportMenuItems(exportMenu, listOf(endpoint))
            popupMenu.add(exportMenu)
            popupMenu.addSeparator()
            popupMenu.add(createMenuItem("Copy Path") {
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val selection = java.awt.datatransfer.StringSelection(endpoint.path)
                clipboard.setContents(selection, null)
                showCopyNotification()
            })
            popupMenu.add(createMenuItem("Copy as cURL") {
                val host = endpointDetailsPanel.getSelectedHost()
                val curl = com.itangcent.easyapi.exporter.curl.CurlFormatter.format(endpoint, host)
                val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                val selection = java.awt.datatransfer.StringSelection(curl)
                clipboard.setContents(selection, null)
                showCopyNotification()
            })
            popupMenu.addSeparator()
            popupMenu.add(createMenuItem("Navigate to Source") {
                navigateToSource(endpoint)
            })
            popupMenu.addSeparator()
            popupMenu.add(createMenuItem("Reset to Default") {
                endpointDetailsPanel.resetEndpoint(endpoint)
            })
        } else if (nodeInfo != null) {
            val endpoints = collectEndpointsFromNode(node)
            if (endpoints.isNotEmpty()) {
                val exportMenu = JMenu("Export")
                addExportMenuItems(exportMenu, endpoints)
                popupMenu.add(exportMenu)
                popupMenu.addSeparator()
            }
            popupMenu.add(createMenuItem("Collapse All") {
                collapseAll()
            })
            popupMenu.add(createMenuItem("Expand All") {
                expandAll()
            })
        } else {
            val exportMenu = JMenu("Export All")
            addExportMenuItems(exportMenu, cachedEndpoints)
            popupMenu.add(exportMenu)
            popupMenu.addSeparator()
            popupMenu.add(createMenuItem("Collapse All") {
                collapseAll()
            })
            popupMenu.add(createMenuItem("Expand All") {
                expandAll()
            })
        }

        popupMenu.show(apiTree, e.x, e.y)
    }

    /**
     * Adds export menu items for all available export formats.
     * 
     * @param menu The parent menu to add items to
     * @param endpoints The list of endpoints to export
     */
    private fun addExportMenuItems(menu: JMenu, endpoints: List<ApiEndpoint>) {
        ExportFormat.entries.forEach { format ->
            menu.add(createMenuItem("Export to ${format.displayName}") {
                showExportDialog(endpoints, format)
            })
        }
    }

    /**
     * Recursively collects all API endpoints from a tree node and its children.
     * 
     * @param node The starting node to collect from
     * @return List of all endpoints found in the subtree
     */
    private fun collectEndpointsFromNode(node: DefaultMutableTreeNode): List<ApiEndpoint> {
        val endpoints = mutableListOf<ApiEndpoint>()
        collectEndpointsFromNode(node, endpoints)
        return endpoints
    }

    /**
     * Recursive helper function to collect endpoints from a node.
     * 
     * @param node Current node being processed
     * @param endpoints Mutable list to accumulate found endpoints
     */
    private fun collectEndpointsFromNode(node: DefaultMutableTreeNode, endpoints: MutableList<ApiEndpoint>) {
        val userObject = node.userObject
        if (userObject is ApiEndpoint) {
            endpoints.add(userObject)
        }
        for (i in 0 until node.childCount) {
            collectEndpointsFromNode(node.getChildAt(i) as DefaultMutableTreeNode, endpoints)
        }
    }

    /**
     * Navigates to the source code location of the given endpoint.
     * 
     * @param endpoint The endpoint to navigate to
     */
    private fun navigateToSource(endpoint: ApiEndpoint) {
        val method = endpoint.sourceMethod ?: return
        if (method.canNavigate()) {
            method.navigate(true)
        }
    }

    /**
     * Creates a menu item with the specified text and action.
     * 
     * @param text The display text for the menu item
     * @param action The action to perform when clicked
     * @return The configured menu item
     */
    private fun createMenuItem(text: String, action: () -> Unit): JMenuItem {
        return JMenuItem(text).apply {
            addActionListener { action() }
        }
    }

    /**
     * Shows a notification indicating content was copied to clipboard.
     */
    private fun showCopyNotification() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("EasyApi Notifications")
            .createNotification("Copied to clipboard", NotificationType.INFORMATION)
            .notify(project)
    }

    /**
     * Shows the export dialog for exporting endpoints to various formats.
     * If a format is specified, skips the format selection dialog.
     * Performs export in background with progress indicator.
     * 
     * @param endpoints The list of endpoints to export
     * @param format Optional pre-selected export format
     */
    private fun showExportDialog(endpoints: List<ApiEndpoint>, format: ExportFormat? = null) {
        if (endpoints.isEmpty()) {
            Messages.showInfoMessage(project, "No API endpoints to export.", "Export API")
            return
        }

        val orchestrator = ExportOrchestrator.getInstance(project)

        // Determine export format and config on EDT before launching background work
        val exportFormat: ExportFormat
        val outputConfig: com.itangcent.easyapi.exporter.model.OutputConfig
        if (format != null) {
            exportFormat = format
            outputConfig = com.itangcent.easyapi.exporter.model.OutputConfig()
        } else {
            val dialogResult = ExportDialog.show(project, endpoints.size) ?: return
            exportFormat = dialogResult.format
            outputConfig = dialogResult.outputConfig
        }

        backgroundAsync {
            try {
                val result = runWithProgress(project, "Exporting APIs...") { indicator ->
                    orchestrator.exportEndpoints(endpoints, exportFormat, outputConfig, indicator)
                }

                swing {
                    handleExportResult(result, format)
                }
            } catch (e: Exception) {
                LOG.warn("Export failed", e)
                swing {
                    NotificationUtils.notifyError(
                        project,
                        "Export Failed",
                        "Export failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Handles the result of an export operation.
     * Delegates to the appropriate exporter for format-specific handling.
     * 
     * @param result The export result (success, cancelled, or error)
     * @param format The export format used
     */
    private suspend fun handleExportResult(result: ExportResult, format: ExportFormat?) {
        when (result) {
            is ExportResult.Success -> {
                val exporterRegistry = ApiExporterRegistry.getInstance(project)
                val exporter = format?.let { exporterRegistry.getExporter(it) }

                val handled = exporter?.handleExportResult(project, result) ?: false

                if (!handled) {
                    showExportSuccessNotification(result)
                }
            }

            is ExportResult.Cancelled -> {
            }

            is ExportResult.Error -> {
                NotificationUtils.notifyError(
                    project,
                    "Export Failed",
                    result.message
                )
            }
        }
    }

    /**
     * Shows a success notification after export completion.
     * 
     * @param result The successful export result containing count and target info
     */
    private fun showExportSuccessNotification(result: ExportResult.Success) {
        val message = buildString {
            append("Successfully exported ${result.count} endpoints to ${result.target}")
            result.metadata?.formatDisplay()?.let { append(" $it") }
        }
        NotificationUtils.notifyInfo(
            project,
            "Export Successful",
            message
        )
    }

    /**
     * Updates the tree model with the given endpoints.
     * Organizes endpoints by module/folder and class hierarchy.
     * Shows helpful tips if no endpoints are found.
     * 
     * @param endpoints The list of endpoints to display
     */
    private fun updateTree(endpoints: List<ApiEndpoint>) {
        if (endpoints.isEmpty()) {
            val root = DefaultMutableTreeNode("No API endpoints found")
            root.add(DefaultMutableTreeNode("Tips:"))
            root.add(DefaultMutableTreeNode("  - Ensure classes have @RestController or @Controller"))
            root.add(DefaultMutableTreeNode("  - Ensure methods have @RequestMapping or similar"))
            root.add(DefaultMutableTreeNode("  - Click Refresh to rescan"))
            treeModel.setRoot(root)
            return
        }

        val groupedByModule: Map<String, List<ApiEndpoint>> = endpoints
            .filter { !it.folder.isNullOrBlank() }
            .groupBy { it.folder!! }
        val noFolderEndpoints = endpoints.filter { it.folder.isNullOrBlank() }
        val hasNoFolder = noFolderEndpoints.isNotEmpty()

        val isSingleModule = groupedByModule.size + (if (hasNoFolder) 1 else 0) == 1

        val root = if (isSingleModule) {
            if (groupedByModule.isNotEmpty()) {
                val moduleName = groupedByModule.keys.first()
                val moduleEndpoints = groupedByModule.values.first()
                buildModuleNode(moduleName, moduleEndpoints)
            } else {
                buildModuleNode(project.name, endpoints)
            }
        } else {
            val root = DefaultMutableTreeNode(NodeInfo("${project.name} (${endpoints.size})", null))
            groupedByModule.keys.sorted().forEach { moduleName ->
                val moduleEndpoints = groupedByModule[moduleName]!!
                val moduleNode = buildModuleNode(moduleName, moduleEndpoints)
                root.add(moduleNode)
            }
            if (hasNoFolder) {
                val noFolderNode = buildModuleNode(project.name, noFolderEndpoints)
                root.add(noFolderNode)
            }
            root
        }

        treeModel.setRoot(root)

        for (i in 0 until minOf(3, treeModel.getChildCount(root))) {
            val child = root.getChildAt(i) as? DefaultMutableTreeNode
            if (child != null) {
                apiTree.expandPath(TreePath(child.path))
            }
        }
    }

    /**
     * Builds a tree node for a module/folder containing API endpoints.
     * If the module contains multiple classes, creates class-level grouping.
     * 
     * @param moduleName The name of the module/folder
     * @param endpoints The endpoints belonging to this module
     * @return The constructed tree node
     */
    private fun buildModuleNode(moduleName: String, endpoints: List<ApiEndpoint>): DefaultMutableTreeNode {
        val moduleNode = DefaultMutableTreeNode(NodeInfo("$moduleName (${endpoints.size})", null))

        val groupedByClass = endpoints.groupBy { it.className ?: it.sourceClass?.qualifiedName ?: "Unknown" }
        val singleClass = groupedByClass.size == 1

        if (singleClass) {
            // Single class in folder — put endpoints directly under the folder node
            for (endpoint in endpoints.sortedBy { it.path }) {
                moduleNode.add(DefaultMutableTreeNode(endpoint))
            }
        } else {
            // Multiple classes share this folder — add class-level grouping
            for ((_, classEndpoints) in groupedByClass.toSortedMap()) {
                val firstEndpoint = classEndpoints.firstOrNull() ?: continue
                val classTitle = firstEndpoint.folder?.takeIf { it.isNotBlank() }
                    ?: firstEndpoint.className?.substringAfterLast('.')
                    ?: firstEndpoint.sourceClass?.name
                    ?: "Unknown"

                val classNode =
                    DefaultMutableTreeNode(NodeInfo("$classTitle (${classEndpoints.size})", firstEndpoint.sourceClass))

                for (endpoint in classEndpoints.sortedBy { it.path }) {
                    classNode.add(DefaultMutableTreeNode(endpoint))
                }

                moduleNode.add(classNode)
            }
        }

        return moduleNode
    }

    /**
     * Data class for holding display text and optional PsiClass reference for tree nodes.
     * Used for folder and class level nodes in the tree.
     */
    private data class NodeInfo(val text: String, val psiClass: PsiClass?) {
        override fun toString(): String = text
    }

    /**
     * Filters the tree based on the current search field text.
     * Searches across endpoint name, path, folder, description, and class name.
     * Uses case-insensitive matching.
     */
    private fun filterTree() {
        val searchText = searchField.text.lowercase().trim()
        if (searchText.isEmpty()) {
            updateTree(cachedEndpoints)
            return
        }

        val filtered = cachedEndpoints.filter { endpoint ->
            endpoint.name?.lowercase()?.contains(searchText) == true ||
                    endpoint.path.lowercase().contains(searchText) ||
                    endpoint.folder?.lowercase()?.contains(searchText) == true ||
                    endpoint.description?.lowercase()?.contains(searchText) == true ||
                    endpoint.className?.lowercase()?.contains(searchText) == true
        }

        if (filtered.isEmpty()) {
            val root = DefaultMutableTreeNode("No results for '$searchText'")
            treeModel.setRoot(root)
        } else {
            updateTree(filtered)
        }
    }

    /**
     * Initializes API data loading and subscribes to cache updates.
     * When the cache is updated, refreshes the tree display.
     */
    private fun setupApis() {
        backgroundAsync {
            updateTree(apiIndex.endpoints())
            apiIndex.subscribe { endpoints ->
                LOG.debug("Cache updated, refreshing tree with ${endpoints.size} endpoints")
                cachedEndpoints = endpoints
                swing {
                    updateTree(endpoints)
                }
            }
        }
    }

    /**
     * Refreshes the API endpoint list by invalidating the cache and requesting a new scan.
     * Shows a "Scanning..." state during the refresh operation.
     */
    fun refresh() {
        LOG.info("Refreshing API endpoints...")
        backgroundAsync {
            LOG.debug("Invalidating cache...")
            apiIndex.invalidate()
            swing {
                LOG.debug("Setting tree to 'Scanning...' state")
                treeModel.setRoot(DefaultMutableTreeNode("Scanning..."))
            }
            LOG.debug("Requesting re-scan...")
            apiIndexManager.requestScan()
        }
    }

    /**
     * Selects the first endpoint matching the given class in the tree.
     * 
     * @param psiClass The class to find endpoints for
     * @return true if an endpoint was found and selected, false otherwise
     */
    fun selectByClass(psiClass: PsiClass): Boolean {
        val className = psiClass.qualifiedName ?: psiClass.name ?: return false
        return selectNode { node ->
            val userObject = (node as? DefaultMutableTreeNode)?.userObject
            userObject is ApiEndpoint && userObject.className == className
        }
    }

    /**
     * Selects the first endpoint matching the given method in the tree.
     * 
     * @param psiMethod The method to find an endpoint for
     * @return true if the endpoint was found and selected, false otherwise
     */
    fun selectByMethod(psiMethod: PsiMethod): Boolean {
        return selectNode { node ->
            val userObject = (node as? DefaultMutableTreeNode)?.userObject
            userObject is ApiEndpoint && userObject.sourceMethod == psiMethod
        }
    }

    /**
     * Generic helper to find and select a tree node matching a predicate.
     * Scrolls the tree to make the selected node visible and updates the details panel.
     * 
     * @param predicate Function to test if a node matches the search criteria
     * @return true if a matching node was found and selected, false otherwise
     */
    private fun selectNode(predicate: (Any?) -> Boolean): Boolean {
        val root = treeModel.root as? DefaultMutableTreeNode ?: return false

        fun findNode(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
            if (predicate(node)) return node

            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as? DefaultMutableTreeNode
                val found = child?.let { findNode(it) }
                if (found != null) return found
            }
            return null
        }

        val targetNode = findNode(root) ?: return false
        val path = TreePath(targetNode.path)

        apiTree.selectionPath = path
        apiTree.scrollPathToVisible(path)

        val endpoint = targetNode.userObject as? ApiEndpoint
        if (endpoint != null) {
            endpointDetailsPanel.showEndpoint(endpoint)
        }
        return true
    }

    /**
     * Collapses all nodes in the tree, starting from the bottom to avoid affecting indices.
     */
    private fun collapseAll() {
        for (i in apiTree.rowCount - 1 downTo 0) {
            apiTree.collapseRow(i)
        }
    }

    /**
     * Expands all nodes in the tree, starting from the top.
     */
    private fun expandAll() {
        for (i in 0 until apiTree.rowCount) {
            apiTree.expandRow(i)
        }
    }

    /**
     * Cleans up resources when the panel is disposed.
     * Stops timers, cancels coroutines, and disposes child components.
     */
    fun dispose() {
        searchDebounceTimer?.stop()
        endpointDetailsPanel.dispose()
    }

    /**
     * Action for refreshing the API endpoint list.
     */
    private inner class RefreshAction : com.intellij.openapi.actionSystem.AnAction(
        "Refresh",
        "Refresh API endpoints",
        com.intellij.icons.AllIcons.Actions.Refresh
    ) {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            refresh()
        }
    }

    /**
     * Action for exporting API endpoints.
     * Exports selected endpoints if any, otherwise exports all cached endpoints.
     */
    private inner class ExportAction : com.intellij.openapi.actionSystem.AnAction(
        "Export",
        "Export API endpoints",
        com.intellij.icons.AllIcons.ToolbarDecorator.Export
    ) {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            val selectedEndpoints = getSelectedEndpoints()
            val endpointsToExport = selectedEndpoints.ifEmpty { cachedEndpoints }
            showExportDialog(endpointsToExport)
        }

        /**
         * Retrieves the currently selected endpoints from the tree.
         * 
         * @return List of selected API endpoints, empty list if none selected
         */
        private fun getSelectedEndpoints(): List<ApiEndpoint> {
            val paths = apiTree.selectionPaths ?: return emptyList()
            return paths.mapNotNull { path ->
                val node = path.lastPathComponent as? DefaultMutableTreeNode
                node?.userObject as? ApiEndpoint
            }
        }
    }

    /**
     * Action for collapsing all nodes in the tree.
     */
    private inner class CollapseAllAction : com.intellij.openapi.actionSystem.AnAction(
        "Collapse All",
        "Collapse all nodes",
        com.intellij.icons.AllIcons.Actions.Collapseall
    ) {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            collapseAll()
        }
    }

    /**
     * Action for expanding all nodes in the tree.
     */
    private inner class ExpandAllAction : com.intellij.openapi.actionSystem.AnAction(
        "Expand All",
        "Expand all nodes",
        com.intellij.icons.AllIcons.Actions.Expandall
    ) {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            expandAll()
        }
    }
}
