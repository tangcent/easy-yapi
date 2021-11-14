package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.designer.clipboard.SimpleTransferable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.psi.*
import com.itangcent.common.concurrent.AQSCountLatch
import com.itangcent.common.concurrent.CountLatch
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.safeComputeIfAbsent
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.plugin.api.cache.CachedRequestClassExporter
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.CompletedHandle
import com.itangcent.idea.plugin.api.export.core.docs
import com.itangcent.idea.plugin.api.export.curl.CurlExporter
import com.itangcent.idea.psi.PsiResource
import com.itangcent.idea.psi.UltimateDocHelper
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.idea.swing.*
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.idea.utils.reload
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.FileType
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragSource
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel


abstract class AbstractApiDashboardDialog : JDialog() {
    protected abstract var contentPane: JPanel?
    protected abstract var projectApiTree: JTree?
    protected abstract var projectApiPanel: JPanel?
    protected abstract var projectApiModeButton: JButton?
    protected abstract var projectCollapseButton: JButton?

    private var apiPopMenu: JPopupMenu? = null

    private var projectMode: ProjectMode = ProjectMode.Legible

    @Inject
    protected lateinit var logger: Logger

    @Inject
    protected lateinit var actionContext: ActionContext

    @Inject
    protected val classExporter: ClassExporter? = null

    @Inject
    protected val ultimateDocHelper: UltimateDocHelper? = null

    @Volatile
    protected var disposed = false

    protected var autoComputer: AutoComputer = AutoComputer()

    protected var safeHashHelper = SafeHashHelper()

    @Inject
    var project: Project? = null

    @Inject(optional = true)
    private var activeWindowProvider: ActiveWindowProvider? = null

    protected var apiLoadFuture: Future<*>? = null

    //region project module-----------------------------------------------------

    protected fun initProjectApiModule() {

        (activeWindowProvider as? MutableActiveWindowProvider)?.setActiveWindow(this)

        projectApiTree!!.model = null

        projectApiTree!!.dragEnabled = true

        projectApiTree!!.transferHandler = ApiTreeTransferHandler(this)

        val dragSource = DragSource.getDefaultDragSource()

        dragSource.createDefaultDragGestureRecognizer(
            projectApiTree, DnDConstants.ACTION_COPY_OR_MOVE
        ) { dge ->
            val lastSelectedPathComponent = projectApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

            if (lastSelectedPathComponent != null) {
                val projectNodeData = lastSelectedPathComponent.userObject
                dge.startDrag(
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                    SimpleTransferable(wrapData(projectNodeData), getWrapDataFlavor())
                )
            }
        }

        actionContext.runAsync {
            actionContext.runInReadUI {

                val moduleManager = ModuleManager.getInstance(project!!)
                val rootTreeNode = DefaultMutableTreeNode()
                val modules = moduleManager.sortedModules.reversed()

                val moduleNodes: ArrayList<ModuleProjectNodeData> = ArrayList()
                for (module in modules) {
                    val moduleProjectNodeData = ModuleProjectNodeData(module)
                    moduleNodes.add(moduleProjectNodeData)
                    rootTreeNode.add(moduleProjectNodeData.asTreeNode())
                }

                actionContext.runInSwingUI {
                    val rootTreeModel = DefaultTreeModel(rootTreeNode, true)
                    projectApiTree!!.model = rootTreeModel
                    apiLoadFuture = actionContext.runAsync {
                        for (moduleNode in moduleNodes) {
                            if (disposed) break
                            loadApiInModule(moduleNode)
                            rootTreeModel.reload(moduleNode.asTreeNode())
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
                logger.error("try collapse project apis failed!")
            }
        }

        autoComputer.bindText(this.projectApiModeButton!!)
            .with(this::projectMode)
            .eval { it.next().desc }
        autoComputer.value(this::projectMode, ProjectMode.Legible)
        this.projectApiModeButton!!.addActionListener {
            autoComputer.value(this::projectMode, this.projectMode.next())
            this.projectApiTree!!.model.reload()
        }

        apiPopMenu = JPopupMenu()

        val refreshItem = JMenuItem("Refresh")
        refreshItem.addActionListener { refreshProjectNode() }
        apiPopMenu!!.add(refreshItem)

        val curlItem = JMenuItem("Copy Curl")
        curlItem.addActionListener {
            selectedProjectNode()?.let { copyCurl(it) }
        }
        apiPopMenu!!.add(curlItem)

        this.projectApiTree!!.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                if (e == null) return
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = projectApiTree!!.getPathForLocation(e.x, e.y) ?: return

                    val targetComponent = path.lastPathComponent
                    val projectNodeData =
                        (targetComponent as DefaultMutableTreeNode).userObject as? ProjectNodeData ?: return
                    if (!projectNodeData.popupEnable()) {
                        return
                    }
                    curlItem.isEnabled = projectNodeData.curlEnable()
                    refreshItem.isEnabled = projectNodeData.refreshEnable()
                    apiPopMenu!!.show(projectApiTree!!, e.x, e.y)
                    projectApiTree!!.selectionPath = path
                }
            }
        })
    }

    private fun refreshProjectNode() {
        val projectNodeData = selectedProjectNode() ?: return
        if (projectNodeData is ModuleProjectNodeData) {
            (classExporter as? CachedRequestClassExporter)?.notUserCache()
            loadApiInModule(projectNodeData) {
                (classExporter as? CachedRequestClassExporter)?.userCache()
            }
        } else if (projectNodeData is ClassProjectNodeData) {
            val rootTreeModel = projectApiTree!!.model
            projectNodeData.removeAllSub()
            rootTreeModel.reload(projectNodeData.asTreeNode())
            (classExporter as? CachedRequestClassExporter)?.notUserCache()
            loadApiInClass(projectNodeData.cls, { projectNodeData }) {
                (classExporter as? CachedRequestClassExporter)?.userCache()
                actionContext.runInSwingUI {
                    rootTreeModel.reload(projectNodeData.asTreeNode())
                }
            }
        }
    }

    private fun selectedProjectNode(): ProjectNodeData? {
        return (projectApiTree!!.lastSelectedPathComponent as? DefaultMutableTreeNode)
            ?.userObject as? ProjectNodeData
    }

    protected fun copyCurl(docContainer: DocContainer) {
        val requests = arrayListOf<Request>()
        docContainer.docs { doc -> (doc as? Request)?.let { requests.add(it) } }
        actionContext.instance(CurlExporter::class).export(requests)
    }

    private fun loadApiInModule(
        moduleData: ModuleProjectNodeData,
        completedHandle: () -> Unit = {}
    ) {
        val rootTreeModel = projectApiTree!!.model
        moduleData.removeAllSub()
        val moduleNode = moduleData.asTreeNode()
        rootTreeModel.reload(moduleNode)
        val sourceRoots = moduleData.module.rootManager.getSourceRoots(false)
        if (sourceRoots.isNullOrEmpty()) {
            LOG.info("no source files be found in module:${moduleData.module.name}")
            moduleData.status = NodeStatus.Loaded
            moduleNode.removeFromParent()
            return
        }

        val countLatch: CountLatch = AQSCountLatch()
        moduleData.status = NodeStatus.Loading
        var anyFound = false
        for (contentRoot in sourceRoots) {
            if (disposed) {
                LOG.info("interrupt parsing api from ${contentRoot.path} because the action ${this::class.simpleName} was disposed")
                return
            }
            countLatch.down()
            val classNodeMap: ConcurrentHashMap<PsiClass, ClassProjectNodeData> = ConcurrentHashMap()
            actionContext.runInReadUI {
                try {
                    if (disposed) {
                        LOG.info("interrupt parsing api from ${contentRoot.path} because the action ${this::class.simpleName} was disposed")
                        return@runInReadUI
                    }
                    val rootDirectory =
                        PsiManager.getInstance(project!!).findDirectory(contentRoot) ?: return@runInReadUI
                    traversal(
                        rootDirectory,
                        { !disposed },
                        {
                            !disposed
                                    && FileType.acceptable(it.name)
                                    && (it is PsiClassOwner)
                        }) { psiFile ->
                        if (disposed) {
                            LOG.info("interrupt parsing api from ${rootDirectory.name} because the action ${this::class.simpleName} was disposed")
                            return@traversal
                        }
                        for (psiClass in (psiFile as PsiClassOwner).classes) {
                            if (disposed) {
                                LOG.info("interrupt export api from ${contentRoot.path} because the action ${this::class.simpleName} was disposed")
                                return@traversal
                            }
                            countLatch.down()
                            loadApiInClass(psiClass, {
                                anyFound = true
                                val resourceClass = it.doc.resourceClass()
                                classNodeMap.safeComputeIfAbsent(resourceClass!!) {
                                    val classProjectNodeData = ClassProjectNodeData(
                                        this,
                                        resourceClass,
                                        ultimateDocHelper!!.findUltimateDescOfClass(resourceClass)
                                    )
                                    moduleData.addSubNodeData(classProjectNodeData)
                                    classProjectNodeData
                                }!!
                            }) { countLatch.up() }
                        }
                    }
                } finally {
                    countLatch.up()
                }
            }
        }
        actionContext.runAsync {
            TimeUnit.MILLISECONDS.sleep(2000)//wait 2s
            countLatch.waitFor(60000)//60s
            if (anyFound) {
                LOG.info("load module [${moduleData.module.name}] completed")
                moduleData.status = NodeStatus.Loaded
            } else {
                LOG.info("no api be found from module [${moduleData.module.name}]")
                moduleNode.removeFromParent()
            }
            KitUtils.safe { completedHandle() }
            actionContext.runInSwingUI {
                KitUtils.safe(
                    ArrayIndexOutOfBoundsException::class,
                    NullPointerException::class
                ) {
                    rootTreeModel.reload(moduleNode)
                }
            }
        }
    }

    private fun loadApiInClass(
        psiClass: PsiClass,
        classProjectNodeData: (ApiProjectNodeData) -> ClassProjectNodeData,
        completedHandle: CompletedHandle
    ) {
        classExporter!!.export(psiClass, docs {
            filterDoc(it)?.let { doc ->
                if (disposed) {
                    LOG.info("interrupt export api from ${psiClass.name} because the action ${this::class.simpleName} was disposed")
                    return@docs
                }
                if (doc.resource == null) {
                    return@docs
                }
                val apiProjectNodeData = ApiProjectNodeData(this, doc)
                classProjectNodeData(apiProjectNodeData).addSubNodeData(
                    apiProjectNodeData
                )
            }
        }, completedHandle)
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

    protected open fun filterDoc(doc: Doc): Doc? {
        return doc
    }

    //endregion project module-----------------------------------------------------

    //region project Node Data--------------------------------------------------

    enum class NodeStatus(var desc: String) {
        Unload("(unload)"),
        Loading("(loading)"),
        Uploading("(uploading)"),
        Deleted("(deleted)"),
        Loaded("")
    }

    enum class ProjectMode(var desc: String) {
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

        abstract fun next(): ProjectMode
    }

    abstract class TreeNodeData<T> {

        var treeNode: DefaultMutableTreeNode? = null

        private var subProjectNodeData: ArrayList<T>? = null

        private var parentProjectNodeData: T? = null

        @Suppress("UNCHECKED_CAST")
        open fun addSubNodeData(nodeData: T) {
            if (subProjectNodeData == null) {
                subProjectNodeData = ArrayList()
            }
            subProjectNodeData!!.add(nodeData)
            (nodeData as TreeNodeData<Any>).parentProjectNodeData = this
            this.asTreeNode().add(nodeData.asTreeNode())
        }

        open fun getSubNodeData(): ArrayList<T>? {
            return this.subProjectNodeData
        }

        fun removeSub(nodeData: T) {
            subProjectNodeData?.remove(nodeData)
            nodeData.asTreeNode().removeFromParent()
        }

        @Suppress("UNCHECKED_CAST")
        fun removeFromParent() {
            if(parentProjectNodeData==null){
                treeNode?.removeFromParent()
            }else {
                parentProjectNodeData?.asNode()?.removeSub(this as T)
            }
        }

        fun removeAllSub() {
            this.asTreeNode().removeAllChildren()
            this.subProjectNodeData?.clear()
        }

        @Suppress("UNCHECKED_CAST")
        private fun T.asNode(): TreeNodeData<T> {
            return (this as TreeNodeData<T>)
        }

        private fun T.asTreeNode(): DefaultMutableTreeNode {
            return (this as TreeNodeData<*>).asTreeNode()
        }

        open fun getParentNodeData(): T? {
            return this.parentProjectNodeData
        }

        @Suppress("UNCHECKED_CAST")
        fun getRootNodeData(): T {
            return when (val parentNodeData = getParentNodeData()) {
                null -> this as T
                else -> (parentNodeData as TreeNodeData<T>).getRootNodeData()
            }
        }

        open fun asTreeNode(): DefaultMutableTreeNode {
            if (treeNode != null) {
                return treeNode!!
            }
            treeNode = DefaultMutableTreeNode(this)
            return treeNode!!
        }
    }

    interface DocContainer {

        fun docs(handle: (Doc) -> Unit)
    }

    abstract class ProjectNodeData : DocContainer, TreeNodeData<ProjectNodeData>() {

        override fun docs(handle: (Doc) -> Unit) {
            this.getSubNodeData()?.forEach { it.docs(handle) }
        }

        open fun popupEnable(): Boolean {
            return true
        }

        open fun curlEnable(): Boolean {
            return true
        }

        open fun refreshEnable(): Boolean {
            return true
        }
    }

    class ModuleProjectNodeData(var module: Module) : ProjectNodeData(), IconCustomized {
        override fun icon(): Icon? {
            return when (status) {
                NodeStatus.Loading -> EasyIcons.Refresh
                else -> null
            } ?: EasyIcons.WebFolder
        }

        override fun refreshEnable(): Boolean {
            return status ==NodeStatus.Loaded
        }

        var status = NodeStatus.Unload

        override fun toString(): String {
            return status.desc + module.name
        }
    }

    class ClassProjectNodeData(
        private val apiDashboardDialog: AbstractApiDashboardDialog,
        var cls: PsiClass,
        var attr: String?
    ) : ProjectNodeData(), IconCustomized, ToolTipAble {
        override fun toolTip(): String? {
            return cls.qualifiedName
        }

        override fun icon(): Icon? {
            return EasyIcons.Class
        }

        override fun getParentNodeData(): ProjectNodeData? {
            return null
        }

        override fun refreshEnable(): Boolean {
            return true
        }

        override fun toString(): String {
            return if (apiDashboardDialog.projectMode == ProjectMode.Legible) {
                attr ?: cls.name ?: "anonymous"
            } else {
                cls.name ?: "anonymous"
            }
        }
    }

    class ApiProjectNodeData(private val apiDashboardDialog: AbstractApiDashboardDialog, var doc: Doc) :
        ProjectNodeData(), IconCustomized,
        ToolTipAble {
        override fun toolTip(): String {
            val psiResource = (doc.resource ?: return "") as PsiResource
            return when (doc) {
                is Request -> "${
                    PsiClassUtils.fullNameOfMethod(
                        psiResource.resourceClass()!!,
                        psiResource.resource() as PsiMethod
                    )
                }\n${(doc as Request).method}:${(doc as Request).path}"
                else -> "${
                    PsiClassUtils.fullNameOfMethod(
                        psiResource.resourceClass()!!,
                        psiResource.resource() as PsiMethod
                    )
                }\n${(doc as MethodDoc).name}"
            }
        }

        override fun icon(): Icon? {
            return EasyIcons.Method
        }

        override fun addSubNodeData(nodeData: ProjectNodeData) {
            throw IllegalArgumentException("add sub projectNodeData to ApiProjectNodeData failed")
        }

        override fun refreshEnable(): Boolean {
            return false
        }

        override fun asTreeNode(): DefaultMutableTreeNode {
            return super.asTreeNode().also { it.allowsChildren = false }
        }

        override fun toString(): String {
            if (this.apiDashboardDialog.projectMode == ProjectMode.Original) {
                return doc.resourceMethod()?.name ?: ""
            }
            return doc.name ?: "anonymous"
        }

        override fun docs(handle: (Doc) -> Unit) {
            handle(this.doc)
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

    protected fun getWrapDataFlavor(): DataFlavor {
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

    class ApiTreeTransferHandler(private val apiDashboardDialog: AbstractApiDashboardDialog) : TransferHandler() {

        override fun canImport(comp: JComponent?, transferFlavors: Array<out DataFlavor>?): Boolean {
            return super.canImport(comp, transferFlavors)
        }

        override fun importData(comp: JComponent?, t: Transferable?): Boolean {
            return super.importData(comp, t)
        }

        override fun createTransferable(component: JComponent?): Transferable? {
            return super.createTransferable(component)
        }
    }
}

private val LOG = org.apache.log4j.Logger.getLogger(AbstractApiDashboardDialog::class.java)