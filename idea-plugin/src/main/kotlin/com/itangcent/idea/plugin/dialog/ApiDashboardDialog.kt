package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.designer.clipboard.SimpleTransferable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiManager
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.ResourceHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.Logger
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragSource
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.ConcurrentHashMap
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
                projectApiTree!!.model = DefaultTreeModel(treeNode, true)
                actionContext!!.runAsync {
                    for (it in moduleNodes) {
                        if (disposed) break
                        loadApiInModule(it)
                    }
                }
            }
        }
    }

    private fun loadApiInModule(moduleNode: DefaultMutableTreeNode) {
        val moduleData = moduleNode.userObject as ModuleNodeData
        moduleData.status = ModuleStatus.loading
        for (contentRoot in moduleData.module.rootManager.contentRoots) {

            if (disposed) return
            val classNodeMap: ConcurrentHashMap<PsiClass, DefaultMutableTreeNode> = ConcurrentHashMap()
            actionContext!!.runInReadUI {
                if (disposed) return@runInReadUI
                val rootDirectory = PsiManager.getInstance(project!!).findDirectory(contentRoot)
                com.itangcent.intellij.util.FileUtils.traversal(rootDirectory!!,
                        { !disposed && !it.isDirectory && it.name.endsWith("java") && (it is PsiClassOwner) }) { psiFile ->
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
                moduleData.status = ModuleStatus.loaded
            }
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
        dispose()
        actionContext!!.unHold()
    }

}
