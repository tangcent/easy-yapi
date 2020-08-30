package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.designer.clipboard.SimpleTransferable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.itangcent.common.concurrent.AQSCountLatch
import com.itangcent.common.concurrent.CountLatch
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.safeComputeIfAbsent
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.yapi.YapiApiDashBoardExporter
import com.itangcent.idea.plugin.api.export.yapi.YapiApiHelper
import com.itangcent.idea.psi.PsiResource
import com.itangcent.idea.psi.ResourceHelper
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.idea.swing.EasyApiTreeCellRenderer
import com.itangcent.idea.swing.IconCustomized
import com.itangcent.idea.swing.SafeHashHelper
import com.itangcent.idea.swing.Tooltipable
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.asMap
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.extend.rx.from
import com.itangcent.intellij.extend.sub
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import kotlin.collections.ArrayList


class YapiDashboardDialog : JDialog() {
    private var contentPane: JPanel? = null
    private var projectApiTree: JTree? = null
    private var yapiApiTree: JTree? = null
    private var projectApiPanel: JPanel? = null
    private var yapiPanel: JPanel? = null
    private var projectApModeButton: JButton? = null
    private var projectCollapseButton: JButton? = null

    private var yapiNewProjectButton: JButton? = null
    private var yapiSyncButton: JButton? = null
    private var yapiCollapseButton: JButton? = null

    private var yapiPopMenu: JPopupMenu? = null

    private var projectMode: ProjectMode = ProjectMode.Legible

    @Inject
    private val logger: Logger? = null

    @Inject
    var actionContext: ActionContext? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private val resourceHelper: ResourceHelper? = null

    @Inject
    private val yapiApiHelper: YapiApiHelper? = null

    @Inject
    private val yapiApiDashBoardExporter: YapiApiDashBoardExporter? = null

    @Volatile
    private var disposed = false

    private var autoComputer: AutoComputer = AutoComputer()

    private var safeHashHelper = SafeHashHelper()

    @Inject
    var project: Project? = null

    init {
        setContentPane(contentPane)
        isModal = true

        // call onCancel() when cross is clicked
        defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                onCancel()
            }
        })

        if (EasyIcons.CollapseAll != null) {
            this.projectCollapseButton!!.icon = EasyIcons.CollapseAll
            this.projectCollapseButton!!.text = ""

            this.yapiCollapseButton!!.icon = EasyIcons.CollapseAll
            this.yapiCollapseButton!!.text = ""
        }

        if (EasyIcons.Add != null) {
            this.yapiNewProjectButton!!.icon = EasyIcons.Add
            this.yapiNewProjectButton!!.text = ""
        }

        if (EasyIcons.Refresh != null) {
            this.yapiSyncButton!!.icon = EasyIcons.Refresh
            this.yapiSyncButton!!.text = ""
        }

        try {
            val projectCellRenderer = EasyApiTreeCellRenderer()

            this.projectApiTree!!.cellRenderer = projectCellRenderer

            projectCellRenderer.leafIcon = EasyIcons.Method
            projectCellRenderer.openIcon = EasyIcons.WebFolder
            projectCellRenderer.closedIcon = EasyIcons.WebFolder

            val yapiCellRenderer = EasyApiTreeCellRenderer()

            this.yapiApiTree!!.cellRenderer = yapiCellRenderer

            yapiCellRenderer.leafIcon = EasyIcons.Link
            yapiCellRenderer.openIcon = EasyIcons.WebFolder
            yapiCellRenderer.closedIcon = EasyIcons.WebFolder

        } catch (e: Exception) {
        }

        yapiPopMenu = JPopupMenu()

        val addItem = JMenuItem("Add Cart")

        addItem.addActionListener {
            newYapiCart()
        }

        val unloadItem = JMenuItem("Unload")

        unloadItem.addActionListener {
            unloadYapiProject()
        }

        val syncItem = JMenuItem("Sync")

        syncItem.addActionListener {
            try {
                syncYapiProject()
            } catch (e: Exception) {
                logger!!.error("sync failed:" + ExceptionUtils.getStackTrace(e))
            }
        }

        yapiPopMenu!!.add(addItem)
        yapiPopMenu!!.add(unloadItem)
        yapiPopMenu!!.add(syncItem)

        this.yapiApiTree!!.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                if (e == null) return
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = yapiApiTree!!.getPathForLocation(e.x, e.y) ?: return

                    val targetComponent = path.lastPathComponent
                    val yapiNodeData = (targetComponent as DefaultMutableTreeNode).userObject

                    addItem.isEnabled = yapiNodeData is YapiProjectNodeData
                    unloadItem.isEnabled = yapiNodeData is YapiProjectNodeData
                    yapiPopMenu!!.show(yapiApiTree!!, e.x, e.y)
                    yapiApiTree!!.selectionPath = path
                }
            }
        })
    }

    @PostConstruct
    fun postConstruct() {
        actionContext!!.hold()

        initProjectApiModule()

        initYapiInfo()
    }

    private var apiLoadFuture: Future<*>? = null

    private var yapiLoadFuture: Future<*>? = null

    //region project module-----------------------------------------------------
    private fun initProjectApiModule() {

        projectApiTree!!.model = null

        projectApiTree!!.dragEnabled = true

        projectApiTree!!.transferHandler = ApiTreeTransferHandler(this)

        val dragSource = DragSource.getDefaultDragSource()

        dragSource.createDefaultDragGestureRecognizer(projectApiTree, DnDConstants.ACTION_COPY_OR_MOVE
        ) { dge ->
            val lastSelectedPathComponent = projectApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

            if (lastSelectedPathComponent != null) {
                val projectNodeData = lastSelectedPathComponent.userObject
                dge.startDrag(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), SimpleTransferable(wrapData(projectNodeData), getWrapDataFlavor()))
            }
        }

        actionContext!!.runAsync {
            actionContext!!.runInReadUI {

                val moduleManager = ModuleManager.getInstance(project!!)
                val treeNode = DefaultMutableTreeNode()
//            var moduleNodeMap: HashMap<Module, DefaultMutableTreeNode> = HashMap()
                val modules = moduleManager.sortedModules.reversed()

                val moduleNodes: ArrayList<DefaultMutableTreeNode> = ArrayList()
                for (module in modules) {
                    val moduleProjectNode = DefaultMutableTreeNode(ModuleProjectNodeData(module))
                    treeNode.add(moduleProjectNode)
                    moduleNodes.add(moduleProjectNode)
                }

                actionContext!!.runInSwingUI {
                    val rootTreeModel = DefaultTreeModel(treeNode, true)
                    projectApiTree!!.model = rootTreeModel
                    apiLoadFuture = actionContext!!.runAsync {
                        for (moduleNode in moduleNodes) {
                            if (disposed) break
                            loadApiInModule(moduleNode, rootTreeModel)
                            KitUtils.safe(ArrayIndexOutOfBoundsException::class,
                                    NullPointerException::class) {
                                rootTreeModel.reload(moduleNode)
                            }
                        }
                        apiLoadFuture = null
                    }
                }
            }
        }

        this.projectCollapseButton!!.addActionListener {
            try {
                SwingUtils.expandOrCollapseNode(this.projectApiTree!!, false)
            } catch (e: Exception) {
                logger!!.error("try collapse project apis failed!")
            }
        }

        autoComputer.bindText(this.projectApModeButton!!)
                .with(this::projectMode)
                .eval { it.next().desc }

        autoComputer.value(this::projectMode, ProjectMode.Legible)
        this.projectApModeButton!!.addActionListener {
            autoComputer.value(this::projectMode, this.projectMode.next())
            (this.projectApiTree!!.model as DefaultTreeModel).reload()
        }
    }

    private fun loadApiInModule(moduleNode: DefaultMutableTreeNode, rootTreeModel: DefaultTreeModel) {
        val moduleData = moduleNode.userObject as ModuleProjectNodeData

        val sourceRoots = moduleData.module.rootManager.getSourceRoots(false)
        if (sourceRoots.isNullOrEmpty()) {
            moduleData.status = NodeStatus.Loaded
            moduleNode.removeFromParent()
            return
        }

        val countLatch: CountLatch = AQSCountLatch()
        moduleData.status = NodeStatus.Loading
        var anyFound = false
        for (contentRoot in moduleData.module.rootManager.getSourceRoots(false)) {
            if (disposed) return
            countLatch.down()
            val classNodeMap: ConcurrentHashMap<PsiClass, DefaultMutableTreeNode> = ConcurrentHashMap()
            actionContext!!.runInReadUI {
                try {
                    if (disposed) return@runInReadUI
                    val rootDirectory = PsiManager.getInstance(project!!).findDirectory(contentRoot)
                    traversal(rootDirectory!!,
                            { !disposed },
                            {
                                !disposed &&
                                        (it.name.endsWith("java") || it.name.endsWith("kt"))
                                        && (it is PsiClassOwner)
                            }) { psiFile ->
                        if (disposed) return@traversal
                        for (psiClass in (psiFile as PsiClassOwner).classes) {

                            if (disposed) return@traversal
                            classExporter!!.export(psiClass) { doc ->
                                if (disposed) return@export
                                if (doc.resource == null) return@export
                                anyFound = true
                                val resourceClass = resourceHelper!!.findResourceClass(doc.resource!!)

                                val clsTreeNode = classNodeMap.safeComputeIfAbsent(resourceClass!!) {
                                    val classProjectNodeData = ClassProjectNodeData(this, resourceClass, resourceHelper.findAttrOfClass(resourceClass))
                                    val node = DefaultMutableTreeNode(classProjectNodeData)
                                    moduleNode.add(node)
                                    (moduleNode.userObject as ModuleProjectNodeData).addSubProjectNodeData(classProjectNodeData)
                                    return@safeComputeIfAbsent node
                                }!!

                                val apiProjectNodeData = ApiProjectNodeData(this, doc)

                                val apiTreeNode = DefaultMutableTreeNode(apiProjectNodeData)
                                apiTreeNode.allowsChildren = false
                                clsTreeNode.add(apiTreeNode)
                                (clsTreeNode.userObject as ClassProjectNodeData).addSubProjectNodeData(apiProjectNodeData)
                            }
                        }
                    }
                } finally {
                    countLatch.up()
                }
            }
        }
        actionContext!!.runAsync {
            countLatch.waitFor(60000)//60s
            if (anyFound) {
                moduleData.status = NodeStatus.Loaded
            } else {
                moduleNode.removeFromParent()
            }
            KitUtils.safe(ArrayIndexOutOfBoundsException::class,
                    NullPointerException::class) {
                rootTreeModel.reload(moduleNode)
            }
        }
    }

    private fun traversal(
            psiDirectory: PsiDirectory,
            keepRunning: () -> Boolean,
            fileFilter: (PsiFile) -> Boolean,
            fileHandle: (PsiFile) -> Unit
    ) {
        val dirStack: Stack<PsiDirectory> = Stack()
        var dir: PsiDirectory? = psiDirectory
        while (dir != null && keepRunning()) {
            for (file in dir.files) {
                if (!keepRunning()) {
                    return
                }
                if (fileFilter(file)) {
                    fileHandle(file)
                }
            }

            if (keepRunning()) {
                for (subdirectory in dir.subdirectories) {
                    dirStack.push(subdirectory)
                }
            }
            if (dirStack.isEmpty()) break
            dir = dirStack.pop()
            Thread.yield()
        }
    }
    //endregion project module-----------------------------------------------------

    //region yapi module-----------------------------------------------------

    private var yapiAvailable: Boolean = true

    private fun initYapiInfo() {

        yapiApiTree!!.model = null

        autoComputer.bindEnable(this.yapiCollapseButton!!)
                .from(this::yapiAvailable)
        autoComputer.bindEnable(this.yapiSyncButton!!)
                .from(this::yapiAvailable)
        autoComputer.bindEnable(this.yapiNewProjectButton!!)
                .from(this::yapiAvailable)

        if (yapiApiHelper!!.findServer().isNullOrEmpty()) {
            autoComputer.value(this::yapiAvailable, false)

            tryInputYapiServer()
        } else {
            loadYapiInfo()
        }

        //drop drag from api to yapi
        DropTarget(this.yapiApiTree, DnDConstants.ACTION_COPY_OR_MOVE, object : DropTargetAdapter() {

            override fun drop(dtde: DropTargetDropEvent?) {
                if (dtde == null) return


                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)

                    val tp: TreePath = yapiApiTree!!.getPathForLocation(dtde.location.x, dtde.location.y) ?: return
                    val targetComponent = tp.lastPathComponent
                    val yapiNodeData = (targetComponent as DefaultMutableTreeNode).userObject

                    val transferable = dtde.transferable
                    val wrapDataFlavor = getWrapDataFlavor()

                    val transferData = transferable.getTransferData(wrapDataFlavor)

                    val projectNodeData = (transferData as WrapData).wrapHash?.let { safeHashHelper.getBean(it) }
                            ?: return

                    handleDropEvent(projectNodeData, yapiNodeData)

                } catch (e: java.lang.Exception) {
                    logger!!.info("drop failed:" + ExceptionUtils.getStackTrace(e))
                } finally {
                    dtde.dropComplete(true)
                }
            }
        })

        this.yapiCollapseButton!!.addActionListener {
            try {
                SwingUtils.expandOrCollapseNode(this.yapiApiTree!!, false)
            } catch (e: Exception) {
                logger!!.error("try collapse yapi apis failed!")
            }
        }

        this.yapiSyncButton!!.addActionListener {
            ((this.yapiApiTree!!.model as DefaultTreeModel).root as DefaultMutableTreeNode).removeAllChildren()
            loadYapiInfo()
        }

        this.yapiNewProjectButton!!.addActionListener {
            importNewYapiProject()
        }

    }

    private fun tryInputYapiServer() {

        actionContext!!.runAsync {

            Thread.sleep(200)
            actionContext!!.runInSwingUI {
                val yapiServer = Messages.showInputDialog(this, "Input server of yapi",
                        "server of yapi", Messages.getInformationIcon())
                if (yapiServer.isNullOrBlank()) {
                    logger!!.info("No yapi server")
                    return@runInSwingUI
                }

                yapiApiHelper!!.setYapiServer(yapiServer)

                autoComputer.value(this::yapiAvailable, true)
                loadYapiInfo()
            }
        }
    }

    private fun loadYapiInfo() {

        if (yapiApiHelper!!.findServer().isNullOrEmpty()) {
            actionContext!!.runInSwingUI {
                Messages.showErrorDialog(this,
                        "load yapi info failed,no server be found", "Error")
            }
            return
        }

        actionContext!!.runInSwingUI {
            //            yapiApiTree!!.dragEnabled = true
            val treeNode = DefaultMutableTreeNode()
            val rootTreeModel = DefaultTreeModel(treeNode, true)

            actionContext!!.runAsync {

                var projectNodes: ArrayList<DefaultMutableTreeNode>? = null
                try {
                    val yapiTokens = yapiApiHelper.readTokens()

                    if (yapiTokens.isNullOrEmpty()) {
                        actionContext!!.runInSwingUI {
                            Messages.showErrorDialog(actionContext!!.instance(Project::class),
                                    "No token be found", "Error")
                        }
                        return@runAsync
                    }

                    projectNodes = ArrayList()

                    yapiTokens.values.stream().distinct().forEach { token ->

                        logger!!.info("load token:$token")
                        val projectId = yapiApiHelper.getProjectIdByToken(token)
                        if (projectId.isNullOrBlank()) {
                            return@forEach
                        }

                        val projectInfo = yapiApiHelper.getProjectInfo(token, projectId)
                                .sub("data")
                                ?.asMap()

                        if (projectInfo.isNullOrEmpty()) {
                            logger.info("invalid token:$token")
                            return@forEach
                        }

                        val projectNode = YapiProjectNodeData(token, projectInfo).asTreeNode()
                        treeNode.add(projectNode)
                        projectNodes.add(projectNode)
                        rootTreeModel.reload(projectNode)
                    }
                } catch (e: Exception) {
                    logger!!.error("error to load yapi info:" + ExceptionUtils.getStackTrace(e))
                }

                actionContext!!.runInSwingUI {
                    yapiApiTree!!.model = rootTreeModel

                    yapiLoadFuture = actionContext!!.runAsync {
                        Thread.sleep(500)
                        if (projectNodes != null) {
                            for (projectNode in projectNodes) {
                                if (disposed) break
                                Thread.sleep(500)
                                loadYapiProject(projectNode)
                            }
                        }
                        yapiLoadFuture = null
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadYapiProject(projectNode: DefaultMutableTreeNode) {
        val yapiProjectNodeData = projectNode.userObject as YapiProjectNodeData
        val projectId = yapiProjectNodeData.getProjectId()
        val yapiApiTreeModel = yapiApiTree!!.model as DefaultTreeModel
        if (projectId == null) {
            actionContext!!.runInSwingUI {
                projectNode.removeFromParent()
                yapiApiTreeModel.reload(projectNode)
            }
            return
        }

        actionContext!!.runAsync {
            yapiProjectNodeData.status = NodeStatus.Loading
            try {
                val carts = yapiApiHelper!!.findCarts(projectId.toString(), yapiProjectNodeData.getProjectToken()!!)
                if (carts.isNullOrEmpty()) {
                    yapiProjectNodeData.status = NodeStatus.Loaded
                    return@runAsync
                }
                actionContext!!.runInSwingUI {

                    for (cart in carts) {
                        val yapiCartNode = YapiCartNodeData(yapiProjectNodeData, cart as HashMap<String, Any?>)
                        loadYapiCart(projectNode, yapiCartNode)
                    }
                    yapiApiTreeModel.reload(projectNode)
                }
            } finally {
                yapiProjectNodeData.status = NodeStatus.Loaded
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadYapiCart(parentNode: DefaultMutableTreeNode, yapiCartNodeData: YapiCartNodeData) {

        actionContext!!.runInSwingUI {
            val parentNodeData = parentNode.userObject as YapiProjectNodeData
            val yapiCartNode = yapiCartNodeData.asTreeNode()
            parentNode.add(yapiCartNode)
            val cartInfo = yapiCartNodeData.info

            val apis = yapiApiHelper!!.findApis(parentNodeData.getProjectToken()!!,
                    cartInfo["_id"].toString())
            if (apis.isNullOrEmpty()) return@runInSwingUI
            for (api in apis) {
                loadYapiApi(yapiCartNode, api as HashMap<String, Any?>)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadYapiApi(parentNode: DefaultMutableTreeNode, item: HashMap<String, Any?>) {
        if (item.isNullOrEmpty()) return

        actionContext!!.runInSwingUI {
            val parentNodeData = parentNode.userObject as YapiCartNodeData
            val apiTreeNode = YapiApiNodeData(parentNodeData, item).asTreeNode()
            apiTreeNode.allowsChildren = false
            parentNode.add(apiTreeNode)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun importNewYapiProject() {

        //todo:input token and module at one dialog
        actionContext!!.runInSwingUI {
            val projectToken = Messages.showInputDialog(this,
                    "Input Project Token",
                    "Project Token",
                    Messages.getInformationIcon())
            if (projectToken.isNullOrBlank()) return@runInSwingUI

            actionContext!!.runAsync {

                val projectId = yapiApiHelper!!.getProjectIdByToken(projectToken)

                if (projectId.isNullOrEmpty()) {
                    return@runAsync
                }

                val projectInfo = yapiApiHelper.getProjectInfo(projectToken, projectId)
                        .sub("data")
                        ?.asMap()

                if (projectInfo.isNullOrEmpty()) {
                    logger!!.error("invalid token:$projectToken")
                    return@runAsync
                }

                actionContext!!.runInSwingUI {
                    val yapiProjectName = projectInfo["name"].toString()
                    val moduleName = Messages.showInputDialog(this,
                            "Input Module Name Of Project",
                            "Module Name",
                            Messages.getInformationIcon(),
                            yapiProjectName,
                            null)

                    @Suppress("LABEL_NAME_CLASH")
                    if (moduleName.isNullOrBlank()) return@runInSwingUI

                    actionContext!!.runAsync {

                        yapiApiHelper.setToken(moduleName, projectToken)
                        actionContext!!.runInSwingUI {
                            val projectTreeNode = YapiProjectNodeData(projectToken, projectInfo).asTreeNode()
                            var model = yapiApiTree!!.model
                            if (model == null) {
                                val treeNode = DefaultMutableTreeNode()
                                model = DefaultTreeModel(treeNode, true)
                                yapiApiTree!!.model = model
                            }

                            val yapiTreeModel = model as DefaultTreeModel

                            (yapiTreeModel.root as DefaultMutableTreeNode).add(projectTreeNode)
                            yapiTreeModel.reload()

                            loadYapiProject(projectTreeNode)
                        }
                    }
                }
            }
        }

    }

    //endregion yapi module-----------------------------------------------------

    //region yapi pop action---------------------------------------------------------

    private fun newYapiCart() {
        val lastSelectedPathComponent = yapiApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

        if (lastSelectedPathComponent != null) {
            val yapiNodeData = lastSelectedPathComponent.userObject as YapiNodeData

            actionContext!!.runInSwingUI {
                val cartName = Messages.showInputDialog(this,
                        "Input Cart Name",
                        "Cart Name",
                        Messages.getInformationIcon())
                if (cartName.isNullOrBlank()) return@runInSwingUI

                yapiApiHelper!!.addCart(yapiNodeData.getProjectToken()!!, cartName, "")

                syncYapiNode(yapiNodeData.getRootNodeData()!!)
            }
        }
    }

    private fun unloadYapiProject() {
        val lastSelectedPathComponent = yapiApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

        if (lastSelectedPathComponent != null) {
            val yapiNodeData = lastSelectedPathComponent.userObject as YapiNodeData

            yapiApiHelper!!.removeToken(yapiNodeData.getProjectToken()!!)

            val treeModel = yapiApiTree!!.model as DefaultTreeModel
            (treeModel.root as DefaultMutableTreeNode)
                    .remove(yapiNodeData.getRootNodeData()!!.asTreeNode())
            treeModel.reload()
        }

    }

    private fun syncYapiProject() {

        val lastSelectedPathComponent = yapiApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

        if (lastSelectedPathComponent != null) {
            val yapiNodeData = lastSelectedPathComponent.userObject
            logger!!.info("reload:[$yapiNodeData]")
            syncYapiNode(yapiNodeData as YapiNodeData)
        }
    }

    private fun syncYapiNode(yapiNodeData: YapiNodeData) {
        actionContext!!.runInSwingUI {
            when (yapiNodeData) {
                is YapiApiNodeData -> {
                    yapiNodeData.getParentNodeData()?.let { syncYapiNode(it) }
                }
                is YapiProjectNodeData -> {

                    //clear
                    yapiNodeData.asTreeNode().removeAllChildren()
                    //reload
                    loadYapiProject(yapiNodeData.asTreeNode())
                }
                is YapiCartNodeData -> {
                    //clear
                    yapiNodeData.asTreeNode().removeAllChildren()
                    loadYapiCart(yapiNodeData.getParentNodeData()!!.asTreeNode(), yapiNodeData)
                    (yapiApiTree!!.model as DefaultTreeModel).reload(yapiNodeData.getRootNodeData()!!.asTreeNode())
                }
            }
        }
    }
    //endregion yapi pop action---------------------------------------------------------

    //region project Node Data--------------------------------------------------

    abstract class ProjectNodeData<C> {

        private var subProjectNodeData: ArrayList<C>? = null

        fun addSubProjectNodeData(projectNodeData: C) {
            if (subProjectNodeData == null) {
                subProjectNodeData = ArrayList()
            }
            subProjectNodeData!!.add(projectNodeData)
        }

        fun getSubProjectNodeData(): ArrayList<C>? {
            return this.subProjectNodeData
        }
    }

    class ModuleProjectNodeData : ProjectNodeData<ClassProjectNodeData>, IconCustomized {
        override fun icon(): Icon? {
            return when (status) {
                NodeStatus.Loading -> EasyIcons.Refresh
                else -> null
            } ?: EasyIcons.WebFolder
        }

        var module: Module

        var status = NodeStatus.Unload

        constructor(module: Module) {
            this.module = module
        }

        override fun toString(): String {
            return status.desc + module.name
        }
    }

    class ClassProjectNodeData : ProjectNodeData<ApiProjectNodeData>, IconCustomized, Tooltipable {
        override fun toolTip(): String? {
            return cls.qualifiedName
        }

        override fun icon(): Icon? {
            return EasyIcons.Class
        }

        var cls: PsiClass

        var attr: String? = null

        private val apiDashboardDialog: YapiDashboardDialog

        constructor(apiDashboardDialog: YapiDashboardDialog, cls: PsiClass) {
            this.cls = cls
            this.apiDashboardDialog = apiDashboardDialog
        }

        constructor(apiDashboardDialog: YapiDashboardDialog, cls: PsiClass, attr: String?) {
            this.cls = cls
            this.attr = attr
            this.apiDashboardDialog = apiDashboardDialog
        }

        override fun toString(): String {
            if (apiDashboardDialog.projectMode == ProjectMode.Legible) {
                return attr ?: cls.name ?: "anonymous"
            } else {
                return cls.name ?: "anonymous"
            }
        }
    }

    enum class NodeStatus(var desc: String) {
        Unload("(unload)"),
        Loading("(loading)"),
        Uploading("(uploading)"),
        Loaded("")
    }

    enum class ProjectMode {
        Original("original") {
            override fun next(): ProjectMode {
                return Legible
            }
        },
        Legible("legible") {
            override fun next(): ProjectMode {
                return Original
            }
        };

        var desc: String

        constructor(desc: String) {
            this.desc = desc
        }

        abstract fun next(): ProjectMode
    }

    class ApiProjectNodeData : IconCustomized, Tooltipable {
        override fun toolTip(): String? {
            val psiResource = (doc.resource ?: return "") as PsiResource
            return when (doc) {
                is Request -> "${PsiClassUtils.fullNameOfMethod(psiResource.resourceClass()!!, psiResource.resource() as PsiMethod)}\n${(doc as Request).method}:${(doc as Request).path}"
                else -> "${PsiClassUtils.fullNameOfMethod(psiResource.resourceClass()!!, psiResource.resource() as PsiMethod)}\n${(doc as MethodDoc).name}"
            }
        }

        private val apiDashboardDialog: YapiDashboardDialog

        override fun icon(): Icon? {
            return EasyIcons.Method
        }

        var doc: Doc

        constructor(apiDashboardDialog: YapiDashboardDialog, doc: Doc) {
            this.doc = doc
            this.apiDashboardDialog = apiDashboardDialog
        }

        override fun toString(): String {
            if (this.apiDashboardDialog.projectMode == ProjectMode.Original) {
                return doc.resourceMethod()?.name ?: ""
            }
            return doc.name ?: "anonymous"

        }
    }

    class WrapData : Serializable {

        var wrapClass: String? = null

        var wrapHash: Int? = null

        var wrapString: String? = null

        override fun toString(): String {
            return wrapString ?: "null"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as WrapData

            if (wrapHash != other.wrapHash) return false

            return true
        }

        override fun hashCode(): Int {
            return wrapHash ?: 0
        }
    }

    private fun wrapData(data: Any): WrapData {

        val wrapData = WrapData()

        wrapData.wrapClass = data::class.qualifiedName
        wrapData.wrapHash = safeHashHelper.hash(data)
        wrapData.wrapString = data.toString()

        return wrapData
    }

    private var wrapDataFlavor: DataFlavor? = null

    private fun getWrapDataFlavor(): DataFlavor {
        if (wrapDataFlavor != null) {
            return wrapDataFlavor!!
        }
        try {
            wrapDataFlavor = DataFlavor(WrapData::class.java, WrapData::class.java.simpleName)
        } catch (e: Exception) {
            return DataFlavor.stringFlavor
        }

        return wrapDataFlavor!!
    }

    //endregion project Node Data--------------------------------------------------

    //region yapi Node Data--------------------------------------------------

    abstract class YapiNodeData {
        abstract fun data(): HashMap<String, Any?>

        fun getRootNodeData(): YapiNodeData? {
            val parentCollectionInfo = getParentNodeData()
            return when (parentCollectionInfo) {
                null -> this
                else -> parentCollectionInfo.getRootNodeData()
            }
        }

        abstract fun getParentNodeData(): YapiNodeData?

        var treeNode: DefaultMutableTreeNode? = null

        fun asTreeNode(): DefaultMutableTreeNode {
            if (treeNode != null) return treeNode!!
            treeNode = DefaultMutableTreeNode(this)
            return treeNode!!
        }

        abstract fun getProjectId(): String?

        abstract fun getProjectToken(): String?
    }

    class YapiProjectNodeData : YapiNodeData, IconCustomized {
        override fun icon(): Icon? {
            return when (status) {
                NodeStatus.Loading -> EasyIcons.Refresh
                NodeStatus.Uploading -> EasyIcons.UpFolder
                else -> null
            } ?: EasyIcons.ModuleGroup
        }

        @Suppress("UNCHECKED_CAST")
        override fun data(): HashMap<String, Any?> {
            return projectInfo
        }

        override fun getParentNodeData(): YapiNodeData? {
            return null
        }

        var projectInfo: HashMap<String, Any?>

        private var projectToken: String

        var status = NodeStatus.Unload

        override fun getProjectId(): String? {
            return projectInfo["_id"]?.toString()
        }

        override fun getProjectToken(): String? {
            return projectToken
        }

        constructor(projectToken: String, projectInfo: HashMap<String, Any?>) {
            this.projectToken = projectToken
            this.projectInfo = projectInfo
        }

        override fun toString(): String {
            return status.desc + projectInfo.getOrDefault("name", "unknown")
        }
    }

    class YapiCartNodeData : YapiNodeData, IconCustomized, Tooltipable {

        override fun icon(): Icon? {
            return EasyIcons.Module
        }

        override fun data(): HashMap<String, Any?> {
            return info
        }

        override fun getParentNodeData(): YapiProjectNodeData? {
            return parentNode
        }

        override fun getProjectId(): String? {
            return parentNode.getProjectId()
        }

        override fun getProjectToken(): String? {
            return parentNode.getProjectToken()
        }

        private var parentNode: YapiProjectNodeData

        var info: HashMap<String, Any?>

        constructor(parentNode: YapiProjectNodeData, info: HashMap<String, Any?>) {
            this.info = info
            this.parentNode = parentNode
        }

        override fun toString(): String {
            return info.getOrDefault("name", "unknown").toString()
        }

        override fun toolTip(): String? {
            return info["desc"]?.toString()
        }
    }

    class YapiApiNodeData : YapiNodeData, IconCustomized, Tooltipable {

        override fun icon(): Icon? {
            return EasyIcons.Link
        }

        override fun data(): HashMap<String, Any?> {
            return info
        }

        override fun getParentNodeData(): YapiCartNodeData? {
            return parentNode
        }

        override fun getProjectId(): String? {
            return parentNode.getProjectId()
        }

        override fun getProjectToken(): String? {
            return parentNode.getProjectToken()
        }

        private var parentNode: YapiCartNodeData

        var info: HashMap<String, Any?>

        constructor(parentNode: YapiCartNodeData, info: HashMap<String, Any?>) {
            this.info = info
            this.parentNode = parentNode
        }

        override fun toString(): String {
            return info.getOrDefault("title", "unknown").toString()
        }

        override fun toolTip(): String? {
            val sb = StringBuilder()
            val method = info["method"]
            if (method != null) {
                sb.append(method).append(":")
            }
            val path = info["path"]
            if (path != null) {
                sb.append(path)
            }
            return sb.toString()
        }
    }

    //endregion yapi Node Data--------------------------------------------------

    //region handle drop--------------------------------------------------------

    @Suppress("UNCHECKED_CAST", "LABEL_NAME_CLASH")
    fun handleDropEvent(fromProjectData: Any, toYapiNodeData: Any) {

        //    \to  | api    |cart               |project
        // from\   |        |                   |
        // api     |see ->  |✔️                 |new cart
        // class   |see ->  |new cart/the cart  |new cart
        // module  |see ->  |new cart/the cart  |new cart

        //targetNodeData should be YapiCartNodeData or YapiProjectNodeData
        val targetNodeData: YapiNodeData = when (toYapiNodeData) {
            is YapiApiNodeData -> toYapiNodeData.getParentNodeData()!!
            else -> toYapiNodeData as YapiNodeData
        }

        logger!!.info("export [$fromProjectData] to $targetNodeData")

        actionContext!!.runAsync {
            try {

                logger.info("parse api...")

                if (targetNodeData is YapiCartNodeData) {
                    val cartId: String? = targetNodeData.info["_id"]?.toString()
                    if (fromProjectData is ApiProjectNodeData) {
                        if (cartId == null) {
                            logger.error("target cartId missing!Please try sync")
                            return@runAsync
                        }
                        export(fromProjectData, targetNodeData, cartId)
                        Thread.sleep(200)
                        syncYapiNode(targetNodeData)
                    } else {
                        actionContext!!.runInSwingUI {
                            val yesNoCancel = Messages.showYesNoCancelDialog(project!!, "Add as new cart?",
                                    "Export", "New", "Not", "Cancel", Messages.getInformationIcon())
                            actionContext!!.runAsync {
                                when (yesNoCancel) {
                                    Messages.CANCEL -> return@runAsync
                                    Messages.OK -> {
                                        export(fromProjectData, targetNodeData, null)
                                        Thread.sleep(200)
                                        syncYapiNode(targetNodeData.getRootNodeData()!!)
                                    }
                                    Messages.NO -> {
                                        export(fromProjectData, targetNodeData, cartId)
                                        Thread.sleep(200)
                                        syncYapiNode(targetNodeData.getRootNodeData()!!)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    export(fromProjectData, targetNodeData, null)
                    Thread.sleep(200)
                    syncYapiNode(targetNodeData.getRootNodeData()!!)
                }
            } catch (e: Exception) {
                logger.error("export failed:" + ExceptionUtils.getStackTrace(e))
            }
        }
    }

    private fun export(fromProjectData: Any, targetNodeData: YapiNodeData,
                       cartId: String?) {

        val privateToken = targetNodeData.getProjectToken()
        if (privateToken == null) {
            logger!!.error("target token missing!Please try sync")
            return
        }

        val docHandle: (Doc) -> Unit
        docHandle = if (cartId.isNullOrBlank()) {
            { doc -> yapiApiDashBoardExporter!!.exportDoc(doc, privateToken) }
        } else {
            { doc -> yapiApiDashBoardExporter!!.exportDoc(doc, privateToken, cartId) }
        }

        export(fromProjectData, docHandle)
        logger!!.info("exported success")
    }

    private fun export(fromProjectData: Any, docHandle: (Doc) -> Unit) {
        if (fromProjectData is ApiProjectNodeData) {
            docHandle(fromProjectData.doc)
        } else if (fromProjectData is ProjectNodeData<*>) {
            fromProjectData.getSubProjectNodeData()
                    ?.filter { it != null }
                    ?.forEach { export(it!!, docHandle) }
        }
    }

    //endregion handle drop--------------------------------------------------------

    class ApiTreeTransferHandler(private val apiDashboardDialog: YapiDashboardDialog) : TransferHandler() {

        override fun canImport(comp: JComponent?, transferFlavors: Array<out DataFlavor>?): Boolean {
            return super.canImport(comp, transferFlavors)
        }

        override fun importData(comp: JComponent?, t: Transferable?): Boolean {
            return super.importData(comp, t)
        }

        override fun createTransferable(component: JComponent?): Transferable {
            return super.createTransferable(component)
        }
    }

    private fun onCancel() {
        disposed = true
        try {
            val apiLoadFuture = this.apiLoadFuture
            if (apiLoadFuture != null && !apiLoadFuture.isDone) {
                apiLoadFuture.cancel(true)
            }
        } catch (e: Throwable) {
            logger!!.error("error to cancel api load:" +
                    ExceptionUtils.getStackTrace(e))
        }
        try {
            val yapiLoadFuture = this.yapiLoadFuture
            if (yapiLoadFuture != null && !yapiLoadFuture.isDone) {
                yapiLoadFuture.cancel(true)
            }
        } catch (e: Throwable) {
            logger!!.error("error to cancel yapi load:" +
                    ExceptionUtils.getStackTrace(e))
        }
        actionContext!!.unHold()
        dispose()
    }

}
