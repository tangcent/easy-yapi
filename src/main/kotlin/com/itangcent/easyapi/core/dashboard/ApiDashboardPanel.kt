package com.itangcent.easyapi.core.dashboard

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.itangcent.easyapi.core.cache.api.ApiIndex
import com.itangcent.easyapi.core.cache.api.ApiScanLifecycleController
import com.itangcent.easyapi.core.cache.api.ApiScanLifecycleSnapshot
import com.itangcent.easyapi.core.cache.api.ApiScanResult
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureStateEvents
import com.itangcent.easyapi.core.feature.FeatureStateService
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.export.ExportOrchestrator
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.channel.spi.CurlRenderer
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.path
import com.itangcent.easyapi.core.ide.dialog.EndpointSelection
import com.itangcent.easyapi.core.ide.dialog.ExportDialog
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.ide.support.runWithProgress
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.type.areMethodsRelated
import com.itangcent.easyapi.core.script.ScriptEditorPanel
import com.itangcent.easyapi.core.script.ScriptScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.CardLayout
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

internal interface ApiDashboardRuntime {
    fun retainedSnapshot(): List<ApiEndpoint>
    fun subscribe(listener: suspend (List<ApiEndpoint>) -> Unit)
    fun isScanningEffective(): Boolean
    fun lifecycleSnapshot(): ApiScanLifecycleSnapshot
    fun manualRefresh(): Deferred<ApiScanResult>
}

private class ProjectApiDashboardRuntime(private val project: Project) : ApiDashboardRuntime {
    private val apiIndex: ApiIndex
        get() = ApiIndex.getInstance(project)

    private val lifecycleController: ApiScanLifecycleController
        get() = ApiScanLifecycleController.getInstance(project)

    override fun retainedSnapshot(): List<ApiEndpoint> = apiIndex.retainedSnapshot()

    override fun subscribe(listener: suspend (List<ApiEndpoint>) -> Unit) {
        apiIndex.subscribe(listener)
    }

    override fun isScanningEffective(): Boolean =
        FeatureStateService.getInstance(project).isEffective(CoreFeatureIds.API_SCANNING)

    override fun lifecycleSnapshot(): ApiScanLifecycleSnapshot = lifecycleController.snapshot()

    override fun manualRefresh(): Deferred<ApiScanResult> = lifecycleController.manualRefresh()
}

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
 * @requires Swing context for construction and disposal
 * @param project The IntelliJ IDEA project context
 */
class ApiDashboardPanel internal constructor(
    private val project: Project,
    private val runtime: ApiDashboardRuntime,
    dispatcher: CoroutineDispatcher
) : JPanel(BorderLayout()), IdeaLog {

    constructor(project: Project) : this(
        project,
        ProjectApiDashboardRuntime(project),
        IdeDispatchers.Background
    )

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in ApiDashboardPanel", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
    private val featureConnection = project.messageBus.connect()

    /** Tree model backing the API tree view */
    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Loading..."))

    /** Tree component displaying the API hierarchy */
    private val apiTree: Tree = Tree(treeModel)

    /** Search input field for filtering endpoints */
    private val searchField = JTextField()

    /** Current scan state shown without replacing retained endpoint content. */
    private val scanStatusLabel = JLabel()

    /** Panel for displaying details of the selected endpoint */
    private val endpointDetailsPanel: EndpointDetailsPanel

    /** Inline panel for quick environment editing */
    private val inlineEnvPanel: InlineEnvironmentPanel

    /** Wrapper for the inline environment panel that can be shown/hidden */
    private val inlineEnvWrapper: JPanel

    /** Script editor panel for module/class-level scripts */
    private lateinit var scriptEditorPanel: ScriptEditorPanel

    /** Card layout for switching between endpoint details and script editor */
    private val rightCardLayout = CardLayout()
    
    /** Panel containing endpoint details and script editor cards */
    private val rightCardPanel = JPanel(rightCardLayout)

    /** Cached list of all endpoints for filtering operations */
    private var cachedEndpoints: List<ApiEndpoint> = emptyList()

    /** Number of manual refresh operations still awaiting completion. */
    private var activeRefreshes: Int = 0

    @Volatile
    private var disposed: Boolean = false

    /** Timer for debouncing search input to avoid excessive filtering */
    private var searchDebounceTimer: Timer? = null

    /**
     * Initializes the panel with HTTP client, UI components, tree listeners, and API data.
     */
    init {
        endpointDetailsPanel = EndpointDetailsPanel(project)
        
        inlineEnvPanel = InlineEnvironmentPanel(project).apply {
            onEnvironmentSaved = {
                endpointDetailsPanel.loadEnvironments()
            }
        }
        inlineEnvWrapper = JPanel(BorderLayout()).apply {
            add(inlineEnvPanel, BorderLayout.CENTER)
            isVisible = false
        }
        
        scriptEditorPanel = ScriptEditorPanel(project)
        
        setupUI()
        setupTreeListeners()
        setupApis()
        setupFeatureStateUpdates()
        setupEnvToggle()
    }

    private fun setupEnvToggle() {
        endpointDetailsPanel.envToggleBtn.addActionListener {
            inlineEnvWrapper.isVisible = !inlineEnvWrapper.isVisible
            if (inlineEnvWrapper.isVisible) {
                inlineEnvPanel.loadActiveEnvironment()
            }
            revalidate()
            repaint()
        }
        
        endpointDetailsPanel.onEnvironmentChangedCallback = {
            if (inlineEnvWrapper.isVisible) {
                inlineEnvPanel.loadActiveEnvironment()
            }
        }
        
        endpointDetailsPanel.scriptScopesProvider = { endpoint ->
            resolveScriptScopesForEndpoint(endpoint)
        }
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

        val topPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(toolbar)
            add(inlineEnvWrapper)
        }

        add(topPanel, BorderLayout.NORTH)
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
            addSeparator()
            add(ClearOrphanedEditsAction())
        }

        val actionToolbar = ActionManager.getInstance().createActionToolbar("ApiDashboardToolbar", actionGroup, true)
        actionToolbar.targetComponent = this
        toolBar.add(actionToolbar.component)

        toolBar.addSeparator()
        toolBar.add(Box.createRigidArea(Dimension(5, 0)))
        toolBar.add(JLabel("Env:"))
        toolBar.add(Box.createRigidArea(Dimension(2, 0)))
        toolBar.add(endpointDetailsPanel.envComboBox)
        toolBar.add(endpointDetailsPanel.envToggleBtn)

        toolBar.addSeparator()
        toolBar.add(Box.createRigidArea(Dimension(5, 0)))
        toolBar.add(JLabel("Search: "))
        searchField.preferredSize = Dimension(200, searchField.preferredSize.height)
        searchField.maximumSize = Dimension(300, searchField.preferredSize.height)
        toolBar.add(searchField)
        toolBar.add(Box.createHorizontalGlue())
        toolBar.add(scanStatusLabel)

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
        rightCardPanel.add(endpointDetailsPanel, CARD_ENDPOINT)
        rightCardPanel.add(scriptEditorPanel, CARD_SCRIPT)
        rightCardLayout.show(rightCardPanel, CARD_ENDPOINT)
        return rightCardPanel
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
                scriptEditorPanel.saveIfDirty()
                endpointDetailsPanel.showEndpoint(endpoint)
                rightCardLayout.show(rightCardPanel, CARD_ENDPOINT)
            } else {
                val nodeInfo = node?.userObject as? NodeInfo
                if (nodeInfo != null && node != null) {
                    val scope = resolveScopeForNode(node, nodeInfo)
                    val endpointCount = countChildEndpoints(node)
                    scriptEditorPanel.loadForScope(scope, endpointCount)
                    rightCardLayout.show(rightCardPanel, CARD_SCRIPT)
                } else {
                    endpointDetailsPanel.clear()
                    rightCardLayout.show(rightCardPanel, CARD_ENDPOINT)
                }
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
                val renderer = CurlRenderer.getInstance()
                val sourceEndpoint = if (renderer.copyFromEdited(project)) {
                    endpointDetailsPanel.buildEditedEndpoint() ?: endpoint
                } else {
                    endpoint
                }
                backgroundAsync {
                    try {
                        // The resolve→format pipeline (incl. format options from settings)
                        // lives in CurlRenderer.formatForCopy (delegating to the cURL
                        // channel's CurlExportResolver); this handler only owns the
                        // dashboard-specific UI concerns: host, edited-vs-original,
                        // clipboard, notification.
                        val curl = renderer.formatForCopy(project, sourceEndpoint, host)
                            ?: return@backgroundAsync  // user cancelled env dialog
                        swing {
                            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(java.awt.datatransfer.StringSelection(curl), null)
                            showCopyNotification()
                        }
                    } catch (_: kotlin.coroutines.cancellation.CancellationException) {
                        LOG.info("Copy as cURL cancelled by user")
                    } catch (e: Throwable) {
                        LOG.warn("Copy as cURL failed", e)
                        swing {
                            NotificationUtils.notifyError(
                                project,
                                "Copy as cURL Failed",
                                "Copy as cURL failed: ${e.message}"
                            )
                        }
                    }
                }
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
        val channelRegistry = ChannelRegistry.getInstance(project)
        val channels = channelRegistry.getAvailableChannels(endpoints)
        channels.forEach { channel ->
            menu.add(createMenuItem("Export to ${channel.displayName}") {
                showExportDialog(endpoints, channel.id)
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
            ApplicationManager.getApplication().invokeLater {
                method.navigate(true)
            }
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
        NotificationUtils.notifyInfo(project, "EasyApi", "Copied to clipboard")
    }

    /**
     * Shows the export dialog for exporting endpoints to various formats.
     * If a format is specified, skips the format selection dialog.
     * Performs export in background with progress indicator.
     * 
     * @param endpoints The list of endpoints to export
     * @param format Optional pre-selected export format
     */
    private fun showExportDialog(endpoints: List<ApiEndpoint>, channelId: String? = null) {
        if (endpoints.isEmpty()) {
            Messages.showInfoMessage(project, "No API endpoints to export.", "Export API")
            return
        }

        val orchestrator = ExportOrchestrator.getInstance(project)

        val selectedChannelId: String
        val channelConfig: ChannelConfig
        val selectedEndpoints: List<EndpointSelection>?

        if (channelId != null) {
            selectedChannelId = channelId
            channelConfig = ChannelConfig.Empty
            selectedEndpoints = null
        } else {
            val dialogResult = ExportDialog.show(project, endpoints.size, endpoints) ?: return
            selectedChannelId = dialogResult.channelId
            channelConfig = dialogResult.channelConfig
            selectedEndpoints = dialogResult.selectedEndpoints
        }

        backgroundAsync {
            try {
                val eps = if (!selectedEndpoints.isNullOrEmpty()) {
                    selectedEndpoints.map { it.endpoint }
                } else {
                    endpoints
                }
                val result = runWithProgress(project, "Exporting APIs...") { indicator ->
                    orchestrator.exportViaChannel(selectedChannelId, eps, channelConfig, indicator)
                }

                handleExportResult(result, selectedChannelId, channelConfig)
            } catch (_: kotlin.coroutines.cancellation.CancellationException) {
                LOG.info("Export cancelled by user")
            } catch (e: Throwable) {
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
    private suspend fun handleExportResult(
        result: ExportResult,
        channelId: String,
        channelConfig: ChannelConfig
    ) {
        when (result) {
            is ExportResult.Success -> {}

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
     * Updates the tree model with the given endpoints.
     * Organizes endpoints by module/folder and class hierarchy.
     * Shows helpful tips if no endpoints are found.
     * 
     * @param endpoints The list of endpoints to display
     * @requires Swing context
     */
    private fun updateTree(endpoints: List<ApiEndpoint>) {
        if (endpoints.isEmpty()) {
            val root = DefaultMutableTreeNode("No API endpoints found")
            root.add(DefaultMutableTreeNode("Tips:"))
            root.add(DefaultMutableTreeNode("  - Ensure classes have @RestController or @Controller"))
            root.add(DefaultMutableTreeNode("  - Ensure methods have @RequestMapping or similar"))
            root.add(DefaultMutableTreeNode("  - For gRPC: Ensure classes extend BindableService or have @GrpcService"))
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

    private fun resolveScopeForNode(node: DefaultMutableTreeNode, nodeInfo: NodeInfo): ScriptScope {
        val psiClass = nodeInfo.psiClass
        return if (psiClass != null) {
            val qualifiedName = ApplicationManager.getApplication().runReadAction<String?> {
                psiClass.qualifiedName ?: psiClass.name
            } ?: nodeInfo.text
            ScriptScope.Class(qualifiedName)
        } else {
            val moduleName = nodeInfo.text.substringBefore(" (").trim()
            ScriptScope.Module(moduleName)
        }
    }

    private fun countChildEndpoints(node: DefaultMutableTreeNode): Int {
        var count = 0
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            if (child.userObject is ApiEndpoint) {
                count++
            } else {
                count += countChildEndpoints(child)
            }
        }
        return count
    }

    fun resolveScriptScopesForEndpoint(endpoint: ApiEndpoint): List<ScriptScope> {
        // Delegates to the CurlRenderer SPI — folder + class
        // scopes only, matching the dashboard's prior behavior. The cURL
        // single-endpoint path (CurlRenderer.formatForCopy → CurlExportResolver)
        // additionally appends the Endpoint scope.
        return CurlRenderer.getInstance().resolveFolderAndClassScopes(endpoint)
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
     * Initializes API data from the retained cache and subscribes to successful updates.
     *
     * @requires Swing context
     */
    private fun setupApis() {
        applySnapshot(runtime.retainedSnapshot())
        updateIdleStatus()
        runtime.subscribe { endpoints ->
            if (disposed) return@subscribe
            LOG.info("Cache updated, refreshing tree with ${endpoints.size} endpoints")
            swing {
                if (!disposed) {
                    applySnapshot(endpoints)
                    if (activeRefreshes == 0) updateIdleStatus()
                }
            }
        }
    }

    private fun setupFeatureStateUpdates() {
        featureConnection.subscribe(
            FeatureStateEvents.TOPIC,
            FeatureStateEvents { change ->
                val scanningChanged = change.entries.any { it.id == CoreFeatureIds.API_SCANNING }
                if (scanningChanged && !disposed) {
                    scope.launch {
                        swing {
                            if (!disposed && activeRefreshes == 0) updateIdleStatus()
                        }
                    }
                }
            }
        )
    }

    /** @requires Swing context */
    private fun applySnapshot(endpoints: List<ApiEndpoint>) {
        cachedEndpoints = endpoints.toList()
        val previousKey = endpointDetailsPanel.currentKey()
        updateTree(cachedEndpoints)
        if (previousKey.isNotEmpty()) {
            restoreSelection(previousKey)
        }
    }

    /**
     * Re-selects the endpoint matching [previousKey] after a rescan rebuilt the tree.
     *
     * `updateTree` calls `treeModel.setRoot()`, which clears the selection but fires a
     * `TreeSelectionEvent` carrying the *old* path — so `setupTreeListeners` re-binds the
     * details panel to the stale `ApiEndpoint` (pre-refresh snapshot). By restoring the
     * selection to the *new* node here, the subsequent selection event carries the fresh
     * `ApiEndpoint`, which re-runs `showEndpoint(newEndpoint)` and re-binds to the latest
     * model skeleton (P0-b). See `.spec/dashboard-request-state.md` decision D1.
     *
     * @requires Swing context
     */
    private fun restoreSelection(previousKey: String) {
        val root = treeModel.root as? DefaultMutableTreeNode ?: return
        val targetNode = findNodeByKey(root, previousKey) ?: return
        if (targetNode.userObject !is ApiEndpoint) return
        val path = TreePath(targetNode.path)
        // Setting the selection fires a TreeSelectionEvent with the *new* path, which
        // re-runs `showEndpoint(newEndpoint)` in `setupTreeListeners` and re-binds the
        // panel to the fresh snapshot. No explicit showEndpoint needed here.
        apiTree.selectionPath = path
        apiTree.scrollPathToVisible(path)
    }

    /**
     * Finds the tree node whose endpoint's cache key equals [key].
     *
     * @param node The subtree root to search
     * @param key  The `className#methodName` key to match
     */
    private fun findNodeByKey(node: DefaultMutableTreeNode, key: String): DefaultMutableTreeNode? {
        val userObject = node.userObject
        if (userObject is ApiEndpoint && endpointCacheKey(userObject) == key) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as? DefaultMutableTreeNode
            val found = child?.let { findNodeByKey(it, key) }
            if (found != null) return found
        }
        return null
    }

    /**
     * Computes the same `className#methodName` cache key used by [EndpointDetailsPanel]
     * to identify an endpoint. `className` is a plain field; `method.name` is read under
     * a read action (the callers run on EDT / Swing context).
     */
    private fun endpointCacheKey(endpoint: ApiEndpoint): String {
        val className = endpoint.className ?: return ""
        val methodName = endpoint.sourceMethod?.let { readSync { it.name } } ?: return ""
        return "$className#$methodName"
    }

    /** @requires Swing context */
    private fun updateIdleStatus(stale: Boolean = false) {
        val paused = !runtime.isScanningEffective()
        scanStatusLabel.text = when {
            stale && paused -> "Paused - stale snapshot"
            stale -> "Stale - showing retained snapshot"
            paused -> "Paused - showing retained snapshot"
            else -> ""
        }
    }

    /** @requires Swing context */
    private fun updateScanningStatus() {
        scanStatusLabel.text = "Scanning - showing retained snapshot"
    }

    /**
     * Requests a lifecycle-controlled manual refresh without clearing retained content.
     *
     * Awaiting scan completion happens on the panel's background dispatcher. The
     * returned completion is intended for callers that need to observe the result;
     * UI callers can safely ignore it.
     */
    fun refresh(): Deferred<ApiScanResult> = scope.async {
        swing {
            activeRefreshes++
            updateScanningStatus()
        }

        val result = try {
            runtime.manualRefresh().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Manual API refresh failed before returning a result", e)
            ApiScanResult.Failed(runtime.lifecycleSnapshot().generation, e)
        }

        val refreshedSnapshot = if (result is ApiScanResult.Success) {
            runtime.retainedSnapshot()
        } else {
            null
        }

        swing {
            refreshedSnapshot?.let(::applySnapshot)
            activeRefreshes = (activeRefreshes - 1).coerceAtLeast(0)
            if (activeRefreshes > 0) {
                updateScanningStatus()
            } else {
                updateIdleStatus(stale = result !is ApiScanResult.Success)
            }
        }
        result
    }

    /** @requires Swing context */
    internal fun displayedEndpoints(): List<ApiEndpoint> = cachedEndpoints.toList()

    /**
     * Prompts the user and, on confirmation, deletes cached request edits whose owning
     * class is no longer present in the current snapshot.
     *
     * The GC unit is the class (see [RequestEditCacheService.orphanKeys] and decision D4),
     * so a method that merely disappears from a still-present class is left untouched.
     * This is an explicit, user-initiated cleanup — automatic GC was deliberately rejected
     * because "class absent from A-side" does not imply the user permanently deleted it.
     *
     * @requires Swing context
     */
    private fun clearOrphanedEdits() {
        val service = RequestEditCacheService.getInstance(project)
        val cachedKeys = service.allKeys()
        if (cachedKeys.isEmpty()) {
            NotificationUtils.notifyInfo(project, "EasyApi", "No cached request edits to clean up.")
            return
        }

        val liveClassNames = cachedEndpoints.mapNotNullTo(mutableSetOf()) { it.className }
        val orphans = RequestEditCacheService.orphanKeys(cachedKeys, liveClassNames)
        if (orphans.isEmpty()) {
            NotificationUtils.notifyInfo(project, "EasyApi", "No orphaned request edits found.")
            return
        }

        val confirmed = Messages.showOkCancelDialog(
            project,
            "Found ${orphans.size} request edit(s) whose API class no longer exists.\n" +
                "Delete them? This cannot be undone.",
            "Clean Up Request Edits",
            "Delete",
            "Cancel",
            Messages.getWarningIcon()
        )
        if (confirmed != Messages.OK) return

        service.deleteAll(orphans)
        NotificationUtils.notifyInfo(project, "EasyApi", "Deleted ${orphans.size} orphaned request edit(s).")
    }

    /** @requires Swing context */
    internal fun dashboardStatusText(): String = scanStatusLabel.text

    /**
     * Selects the first endpoint matching the given class in the tree.
     * 
     * @param psiClass The class to find endpoints for
     * @return true if an endpoint was found and selected, false otherwise
     */
    suspend fun selectByClass(psiClass: PsiClass): Boolean {
        val className = psiClass.qualifiedName ?: psiClass.name ?: return false
        return selectNode { node ->
            val userObject = (node as? DefaultMutableTreeNode)?.userObject
            userObject is ApiEndpoint && userObject.className == className
        }
    }

    /**
     * Selects the first endpoint matching the given method in the tree.
     *
     * First tries direct method match, then falls back to checking super methods
     * for interface/base class method navigation.
     *
     * @param psiMethod The method to find an endpoint for
     * @return true if the endpoint was found and selected, false otherwise
     */
    suspend fun selectByMethod(psiMethod: PsiMethod): Boolean {
        return selectNode { node ->
            val userObject = (node as? DefaultMutableTreeNode)?.userObject
            userObject is ApiEndpoint && userObject.sourceMethod == psiMethod
        } || selectNode { node ->
            val userObject = (node as? DefaultMutableTreeNode)?.userObject
            userObject is ApiEndpoint && userObject.sourceMethod?.let { areMethodsRelated(psiMethod, it) } == true
        }
    }

    /**
     * Generic helper to find and select a tree node matching a predicate.
     * Scrolls the tree to make the selected node visible and updates the details panel.
     * 
     * @param predicate Function to test if a node matches the search criteria
     * @return true if a matching node was found and selected, false otherwise
     */
    private suspend fun selectNode(predicate: suspend (Any?) -> Boolean): Boolean {
        val root = treeModel.root as? DefaultMutableTreeNode ?: return false

        suspend fun findNode(node: DefaultMutableTreeNode): DefaultMutableTreeNode? {
            if (predicate(node)) return node

            for (i in 0 until node.childCount) {
                val child = node.getChildAt(i) as? DefaultMutableTreeNode
                val found = child?.let { findNode(it) }
                if (found != null) return found
            }
            return null
        }

        val targetNode = findNode(root) ?: return false
        val endpoint = targetNode.userObject as? ApiEndpoint

        swing {
            val path = TreePath(targetNode.path)
            apiTree.selectionPath = path
            apiTree.scrollPathToVisible(path)

            if (endpoint != null) {
                endpointDetailsPanel.showEndpoint(endpoint)
            }
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
     *
     * @requires Swing context
     */
    fun dispose() {
        if (disposed) return
        disposed = true
        featureConnection.disconnect()
        scope.cancel()
        searchDebounceTimer?.stop()
        scriptEditorPanel.saveIfDirty()
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

    /**
     * Action for cleaning up orphaned request edits (cached edits whose owning API
     * class no longer exists in the current snapshot).
     */
    private inner class ClearOrphanedEditsAction : com.intellij.openapi.actionSystem.AnAction(
        "Clean Up Request Edits",
        "Delete cached edits whose API class no longer exists",
        com.intellij.icons.AllIcons.Actions.GC
    ) {
        override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
            clearOrphanedEdits()
        }
    }

    companion object {
        private const val CARD_ENDPOINT = "endpoint"
        private const val CARD_SCRIPT = "script"
    }
}
