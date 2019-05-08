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
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.ResourceHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.Logger
import org.apache.commons.lang3.exception.ExceptionUtils
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragSource
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ApiDashboardDialog : JDialog() {
    private var contentPane: JPanel? = null
    private var projectApiTree: JTree? = null
    private var postmanApiTree: JTree? = null
    private var projectApiPanel: JPanel? = null
    private var postmanPanel: JPanel? = null

    @Inject
    private val logger: Logger? = null

    @Inject
    var actionContext: ActionContext? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private val parseHandle: ParseHandle? = null

    @Inject
    private val resourceHelper: ResourceHelper? = null

    @Volatile
    private var disposed = false

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
    }

    @PostConstruct
    fun postConstruct() {
        actionContext!!.hold()

        initProjectApiModule()

        initPostmanInfo()
    }

    private var apiLoadFuture: Future<*>? = null

    private fun initProjectApiModule() {
        projectApiTree!!.dragEnabled = true

        projectApiTree!!.transferHandler = ApiTreeTransferHandler()

        val dragSource = DragSource.getDefaultDragSource()

        dragSource.createDefaultDragGestureRecognizer(projectApiTree, DnDConstants.ACTION_COPY_OR_MOVE
        ) { it.startDrag(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR), SimpleTransferable(projectApiTree!!.lastSelectedPathComponent, DataFlavor.stringFlavor)) }

        actionContext!!.runInReadUI {

            val moduleManager = ModuleManager.getInstance(project!!)
            val treeNode = DefaultMutableTreeNode()
//            var moduleNodeMap: HashMap<Module, DefaultMutableTreeNode> = HashMap()
            val modules = moduleManager.sortedModules.reversed()

            val moduleNodes: ArrayList<DefaultMutableTreeNode> = ArrayList()
            for (module in modules) {
                val moduleApiNodeData = DefaultMutableTreeNode(ModuleNodeData(module))
                treeNode.add(moduleApiNodeData)
                moduleNodes.add(moduleApiNodeData)
            }

            actionContext!!.runInSwingUI {
                val rootTreeModel = DefaultTreeModel(treeNode, true)
                projectApiTree!!.model = rootTreeModel
                apiLoadFuture = actionContext!!.runAsync {
                    for (it in moduleNodes) {
                        if (disposed) break
                        loadApiInModule(it)
                        rootTreeModel.reload(it)
                    }
                    apiLoadFuture = null
                }
            }
        }
    }

    private fun loadApiInModule(moduleNode: DefaultMutableTreeNode) {
        val moduleData = moduleNode.userObject as ModuleNodeData

        val sourceRoots = moduleData.module.rootManager.getSourceRoots(false)
        if (sourceRoots.isNullOrEmpty()) {
            moduleData.status = ModuleStatus.loaded
            moduleNode.removeFromParent()
            return
        }

        val countLatch: CountLatch = AQSCountLatch()
        moduleData.status = ModuleStatus.loading
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
                            { !disposed && it.name.endsWith("java") && (it is PsiClassOwner) }) { psiFile ->
                        if (disposed) return@traversal
                        for (psiClass in (psiFile as PsiClassOwner).classes) {

                            if (disposed) return@traversal
                            classExporter!!.export(psiClass, parseHandle!!) { request ->
                                if (disposed) return@export

                                val resourceClass = resourceHelper!!.findResourceClass(request.resource!!)

                                val clsTreeNode = classNodeMap.computeIfAbsent(resourceClass!!) {
                                    val node = DefaultMutableTreeNode(ModuleClassNodeData(resourceClass, resourceHelper.findAttrOfClass(resourceClass)))
                                    moduleNode.add(node)
                                    return@computeIfAbsent node
                                }
                                val apiTreeNode = DefaultMutableTreeNode(ModuleApiNodeData(request))
                                apiTreeNode.allowsChildren = false
                                clsTreeNode.add(apiTreeNode)
                            }
                        }
                    }
                } finally {
                    countLatch.up()
                }
            }
        }
        actionContext!!.runAsync {
            countLatch.waitFor(60000)
            moduleData.status = ModuleStatus.loaded
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

    private fun initPostmanInfo() {
        postmanApiTree!!.dragEnabled = true
    }

    class ModuleNodeData {
        var module: Module

        var status = ModuleStatus.unload

        constructor(module: Module) {
            this.module = module
        }

        override fun toString(): String {
            return status.desc + module.name
        }
    }

    class ModuleClassNodeData {

        var cls: PsiClass

        var attr: String? = null


        constructor(cls: PsiClass) {
            this.cls = cls
        }

        constructor(cls: PsiClass, attr: String?) {
            this.cls = cls
            this.attr = attr
        }

        override fun toString(): String {
            return attr ?: cls.name ?: "anonymous"
        }
    }

    enum class ModuleStatus(var desc: String) {
        unload("(unload)"),
        loading("(loading)"),
        loaded("")
    }

    class ModuleApiNodeData {

        var request: Request

        constructor(request: Request) {
            this.request = request
        }

        override fun toString(): String {
            return request.name ?: "anonymous"
        }
    }

    class ApiTreeTransferHandler : TransferHandler() {

        override fun canImport(comp: JComponent?, transferFlavors: Array<out DataFlavor>?): Boolean {
            return super.canImport(comp, transferFlavors)
        }

        override fun importData(comp: JComponent?, t: Transferable?): Boolean {
            return super.importData(comp, t)
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
        actionContext!!.unHold()
        dispose()
    }

}
