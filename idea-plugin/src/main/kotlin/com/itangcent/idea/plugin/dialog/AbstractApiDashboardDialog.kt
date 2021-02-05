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
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.docs
import com.itangcent.idea.psi.PsiResource
import com.itangcent.idea.psi.ResourceHelper
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.idea.swing.IconCustomized
import com.itangcent.idea.swing.SafeHashHelper
import com.itangcent.idea.swing.ToolTipAble
import com.itangcent.idea.swing.Tooltipable
import com.itangcent.idea.utils.SwingUtils
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
import java.io.Serializable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.collections.ArrayList


abstract class AbstractApiDashboardDialog : JDialog() {
    protected abstract var contentPane: JPanel?
    protected abstract var projectApiTree: JTree?
    protected abstract var projectApiPanel: JPanel?
    protected abstract var projectApModeButton: JButton?
    protected abstract var projectCollapseButton: JButton?

    private var projectMode: ProjectMode = ProjectMode.Legible

    @Inject
    protected val logger: Logger? = null

    @Inject
    protected var actionContext: ActionContext? = null

    @Inject
    protected val classExporter: ClassExporter? = null

    @Inject
    protected val resourceHelper: ResourceHelper? = null

    @Volatile
    protected var disposed = false

    protected var autoComputer: AutoComputer = AutoComputer()

    protected var safeHashHelper = SafeHashHelper()

    @Inject
    var project: Project? = null

    protected var apiLoadFuture: Future<*>? = null

    //region project module-----------------------------------------------------
    protected fun initProjectApiModule() {

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
                            rootTreeModel.reload(moduleNode)
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
                LOG.info("interrupt parsing api from ${contentRoot.path} because the action ApiDashBoard was disposed")
                return
            }
            countLatch.down()
            val classNodeMap: ConcurrentHashMap<PsiClass, DefaultMutableTreeNode> = ConcurrentHashMap()
            actionContext!!.runInReadUI {
                try {
                    if (disposed) {
                        LOG.info("interrupt parsing api from ${contentRoot.path} because the action ApiDashBoard was disposed")
                        return@runInReadUI
                    }
                    val rootDirectory = PsiManager.getInstance(project!!).findDirectory(contentRoot)
                    traversal(rootDirectory!!,
                            { !disposed },
                            {
                                !disposed
                                        && FileType.acceptable(it.name)
                                        && (it is PsiClassOwner)
                            }) { psiFile ->
                        if (disposed) {
                            LOG.info("interrupt parsing api from ${rootDirectory.name} because the action ApiDashBoard was disposed")
                            return@traversal
                        }
                        for (psiClass in (psiFile as PsiClassOwner).classes) {
                            if (disposed) {
                                LOG.info("interrupt export api from ${contentRoot.path} because the action ApiDashBoard was disposed")
                                return@traversal
                            }
                            countLatch.down()
                            classExporter!!.export(psiClass, docs {
                                filterDoc(it)?.let { doc ->
                                    if (disposed) {
                                        LOG.info("interrupt export api from ${psiClass.name} because the action ApiDashBoard was disposed")
                                        return@docs
                                    }
                                    if (doc.resource == null) {
                                        return@docs
                                    }
                                    anyFound = true
                                    val resourceClass = doc.resourceClass()

                                    val clsTreeNode = classNodeMap.safeComputeIfAbsent(resourceClass!!) {
                                        val classProjectNodeData = ClassProjectNodeData(this, resourceClass, resourceHelper!!.findAttrOfClass(resourceClass))
                                        val node = DefaultMutableTreeNode(classProjectNodeData)
                                        moduleNode.add(node)
                                        (moduleNode.userObject as ModuleProjectNodeData).addSubProjectNodeData(classProjectNodeData)
                                        node
                                    }!!

                                    val apiProjectNodeData = ApiProjectNodeData(this, doc)

                                    val apiTreeNode = DefaultMutableTreeNode(apiProjectNodeData)
                                    apiTreeNode.allowsChildren = false
                                    clsTreeNode.add(apiTreeNode)
                                    (clsTreeNode.userObject as ClassProjectNodeData).addSubProjectNodeData(apiProjectNodeData)
                                }
                            }) {
                                countLatch.up()
                            }
                        }
                    }
                } finally {
                    countLatch.up()
                }
            }
        }
        actionContext!!.runAsync {
            TimeUnit.MILLISECONDS.sleep(2000)//wait 2s
            countLatch.waitFor(60000)//60s
            if (anyFound) {
                LOG.info("load module [${moduleData.module.name}] completed")
                moduleData.status = NodeStatus.Loaded
            } else {
                LOG.info("no api be found from module [${moduleData.module.name}]")
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

    open protected fun filterDoc(doc: Doc): Doc? {
        return doc
    }

    //endregion project module-----------------------------------------------------

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

    class ClassProjectNodeData : ProjectNodeData<ApiProjectNodeData>, IconCustomized, ToolTipAble {
        override fun toolTip(): String? {
            return cls.qualifiedName
        }

        override fun icon(): Icon? {
            return EasyIcons.Class
        }

        var cls: PsiClass

        var attr: String? = null

        private val apiDashboardDialog: AbstractApiDashboardDialog

        constructor(apiDashboardDialog: AbstractApiDashboardDialog, cls: PsiClass) {
            this.cls = cls
            this.apiDashboardDialog = apiDashboardDialog
        }

        constructor(apiDashboardDialog: AbstractApiDashboardDialog, cls: PsiClass, attr: String?) {
            this.cls = cls
            this.attr = attr
            this.apiDashboardDialog = apiDashboardDialog
        }

        override fun toString(): String {
            return if (apiDashboardDialog.projectMode == ProjectMode.Legible) {
                attr ?: cls.name ?: "anonymous"
            } else {
                cls.name ?: "anonymous"
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

        private val apiDashboardDialog: AbstractApiDashboardDialog

        override fun icon(): Icon? {
            return EasyIcons.Method
        }

        var doc: Doc

        constructor(apiDashboardDialog: AbstractApiDashboardDialog, doc: Doc) {
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

        override fun createTransferable(component: JComponent?): Transferable {
            return super.createTransferable(component)
        }
    }
}

private val LOG = org.apache.log4j.Logger.getLogger(AbstractApiDashboardDialog::class.java)