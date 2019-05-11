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
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.ResourceHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanFormatter
import com.itangcent.idea.swing.SafeHashHelper
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.Logger
import org.apache.commons.lang3.exception.ExceptionUtils
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.Serializable
import java.util.Stack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import kotlin.collections.ArrayList


class ApiDashboardDialog : JDialog() {
    private var contentPane: JPanel? = null
    private var projectApiTree: JTree? = null
    private var postmanApiTree: JTree? = null
    private var projectApiPanel: JPanel? = null
    private var postmanPanel: JPanel? = null
    private var projectCollapseButton: JButton? = null

    private var postmanSyncButton: JButton? = null
    private var postmanCollapseButton: JButton? = null

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

    @Inject
    private val postmanCachedHelper: PostmanCachedHelper? = null

    @Inject
    private val postmanFormatter: PostmanFormatter? = null

    @Volatile
    private var disposed = false

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
    }

    @PostConstruct
    fun postConstruct() {
        actionContext!!.hold()

        initProjectApiModule()

        initPostmanInfo()
    }

    private var apiLoadFuture: Future<*>? = null

    private var postmanLoadFuture: Future<*>? = null

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
                            rootTreeModel.reload(moduleNode)
                        }
                        apiLoadFuture = null
                    }
                }
            }
        }

        this.projectCollapseButton!!.addActionListener {
            SwingUtils.expandOrCollapseNode(this.projectApiTree!!, false)
        }
    }

    private fun loadApiInModule(moduleNode: DefaultMutableTreeNode, rootTreeModel: DefaultTreeModel) {
        val moduleData = moduleNode.userObject as ModuleProjectNodeData

        val sourceRoots = moduleData.module.rootManager.getSourceRoots(false)
        if (sourceRoots.isNullOrEmpty()) {
            moduleData.status = NodeStatus.loaded
            moduleNode.removeFromParent()
            return
        }

        val countLatch: CountLatch = AQSCountLatch()
        moduleData.status = NodeStatus.loading
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
                            { !disposed && it.name.endsWith("java") && (it is PsiClassOwner) }) { psiFile ->
                        if (disposed) return@traversal
                        for (psiClass in (psiFile as PsiClassOwner).classes) {

                            if (disposed) return@traversal
                            classExporter!!.export(psiClass, parseHandle!!) { request ->
                                if (disposed) return@export
                                if (request.resource == null) return@export
                                anyFound = true
                                val resourceClass = resourceHelper!!.findResourceClass(request.resource!!)

                                val clsTreeNode = classNodeMap.computeIfAbsent(resourceClass!!) {
                                    val classProjectNodeData = ClassProjectNodeData(resourceClass, resourceHelper.findAttrOfClass(resourceClass))
                                    val node = DefaultMutableTreeNode(classProjectNodeData)
                                    moduleNode.add(node)
                                    (moduleNode.userObject as ModuleProjectNodeData).addSubProjectNodeData(classProjectNodeData)
                                    return@computeIfAbsent node
                                }

                                val apiProjectNodeData = ApiProjectNodeData(request)

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
            countLatch.waitFor(60000)
            if (anyFound) {
                moduleData.status = NodeStatus.loaded
            } else {
                moduleNode.removeFromParent()
            }
            rootTreeModel.reload(moduleNode)
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

    //region postman module-----------------------------------------------------
    private fun initPostmanInfo() {

        postmanApiTree!!.model = null

        loadPostmanInfo(true)

        //drop drag from api to postman
        DropTarget(this.postmanApiTree, DnDConstants.ACTION_COPY_OR_MOVE, object : DropTargetAdapter() {

            override fun drop(dtde: DropTargetDropEvent?) {
                if (dtde == null) return


                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)

                    val tp = postmanApiTree!!.getPathForLocation(dtde.location.x, dtde.location.y)
                    val targetComponent = tp.lastPathComponent
                    val postmanNodeData = (targetComponent as DefaultMutableTreeNode).userObject

                    val transferable = dtde.transferable
                    val wrapDataFlavor = getWrapDataFlavor()

                    val transferData = transferable.getTransferData(wrapDataFlavor)

                    val projectNodeData = (transferData as WrapData).wrapHash?.let { safeHashHelper.getBean(it) }
                            ?: return

                    handleDropEvent(projectNodeData, postmanNodeData)

                } catch (e: java.lang.Exception) {
                    logger!!.info("drop failed:" + ExceptionUtils.getStackTrace(e))
                } finally {
                    dtde.dropComplete(true)
                }
            }
        })

        this.postmanCollapseButton!!.addActionListener {
            SwingUtils.expandOrCollapseNode(this.postmanApiTree!!, false)
        }

        this.postmanSyncButton!!.addActionListener {
            loadPostmanInfo(false)
        }
    }

    private fun loadPostmanInfo(useCache: Boolean) {

        if (!postmanCachedHelper!!.hasPrivateToken()) {
            actionContext!!.runInSwingUI {
                Messages.showErrorDialog(actionContext!!.instance(Project::class),
                        "load postman info failed,no token be found", "Error")
            }
            return
        }

        actionContext!!.runInSwingUI {
            //            postmanApiTree!!.dragEnabled = true
            val treeNode = DefaultMutableTreeNode()
            val rootTreeModel = DefaultTreeModel(treeNode, true)

            actionContext!!.runAsync {

                val collections = postmanCachedHelper.getAllCollection(useCache)
                if (collections.isNullOrEmpty()) {
                    actionContext!!.runInSwingUI {
                        Messages.showErrorDialog(actionContext!!.instance(Project::class),
                                "load postman info failed", "Error")
                    }
                    return@runAsync
                }
                val collectionNodes: ArrayList<DefaultMutableTreeNode> = ArrayList()

                actionContext!!.runInSwingUI {
                    for (collection in collections) {
                        val collectionNode = DefaultMutableTreeNode(CollectionPostmanNodeData(collection))
                        logger!!.info("load collection:$collectionNode")
                        treeNode.add(collectionNode)
                        collectionNodes.add(collectionNode)
                        rootTreeModel.reload(collectionNode)
                    }
                    postmanApiTree!!.model = rootTreeModel

                    postmanLoadFuture = actionContext!!.runAsync {
                        Thread.sleep(1000)
                        for (collectionNode in collectionNodes) {
                            if (disposed) break
                            Thread.sleep(1000)
                            actionContext!!.runInSwingUI {
                                loadPostCollectionInfo(collectionNode, rootTreeModel, useCache)
                                rootTreeModel.reload(collectionNode)
                            }
                        }
                        postmanLoadFuture = null
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPostCollectionInfo(collectionNode: DefaultMutableTreeNode, rootTreeModel: DefaultTreeModel, useCache: Boolean) {
        val moduleData = collectionNode.userObject as CollectionPostmanNodeData
        val collectionId = moduleData.collection["id"]
        if (collectionId == null) {
            collectionNode.removeFromParent()
            rootTreeModel.reload(collectionNode)
            return
        }

        val collectionInfo = postmanCachedHelper!!.getCollectionInfo(collectionId.toString(), useCache)
        if (collectionInfo == null) {
            moduleData.status = NodeStatus.loaded
            return
        }
        try {
            val items = collectionInfo["item"] as ArrayList<HashMap<String, Any?>>
            for (item in items) {
                loadPostmanNode(collectionNode, item)
            }
        } finally {
            moduleData.status = NodeStatus.loaded
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPostmanNode(parentNode: DefaultMutableTreeNode, item: HashMap<String, Any?>) {
        if (item.isNullOrEmpty()) return

        val parentNodeData = parentNode.userObject as PostmanNodeData
        Thread.yield()
        if (item.containsKey("request")) {//is request
            val apiTreeNode = DefaultMutableTreeNode(PostmanApiNodeData(parentNodeData, item))
            apiTreeNode.allowsChildren = false
            parentNode.add(apiTreeNode)

        } else {//is sub collection
            val subCollectionNode = DefaultMutableTreeNode(PostmanSubCollectionNodeData(parentNodeData, item))
            parentNode.add(subCollectionNode)

            val items = item["item"] as ArrayList<HashMap<String, Any?>>?
            if (items.isNullOrEmpty()) return
            for (subItem in items) {
                loadPostmanNode(subCollectionNode, subItem)
            }
        }
    }
    //endregion postman module-----------------------------------------------------

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

    class ModuleProjectNodeData : ProjectNodeData<ClassProjectNodeData> {
        var module: Module

        var status = NodeStatus.unload

        constructor(module: Module) {
            this.module = module
        }

        override fun toString(): String {
            return status.desc + module.name
        }
    }

    class ClassProjectNodeData : ProjectNodeData<ApiProjectNodeData> {

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

    enum class NodeStatus(var desc: String) {
        unload("(unload)"),
        loading("(loading)"),
        loaded("")
    }

    class ApiProjectNodeData {

        var request: Request

        constructor(request: Request) {
            this.request = request
        }

        override fun toString(): String {
            return request.name ?: "anonymous"
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

    //region postman Node Data--------------------------------------------------

    abstract class PostmanNodeData {
        abstract fun currData(): HashMap<String, Any?>

        fun getRootNodeData(): PostmanNodeData? {
            val parentCollectionInfo = getParentNodeData()
            return when (parentCollectionInfo) {
                null -> this
                else -> parentCollectionInfo.getRootNodeData()
            }
        }

        abstract fun getParentNodeData(): PostmanNodeData?
    }

    class CollectionPostmanNodeData : PostmanNodeData {
        override fun currData(): HashMap<String, Any?> {
            return collection
        }

        override fun getParentNodeData(): PostmanNodeData? {
            return null
        }


        var collection: HashMap<String, Any?>

        var status = NodeStatus.unload

        constructor(collection: HashMap<String, Any?>) {
            this.collection = collection
        }

        override fun toString(): String {
            return status.desc + collection.getOrDefault("name", "unknown")
        }
    }

    class PostmanSubCollectionNodeData : PostmanNodeData {
        override fun currData(): HashMap<String, Any?> {
            return info
        }

        override fun getParentNodeData(): PostmanNodeData? {
            return parentNode
        }

        private var parentNode: PostmanNodeData

        var info: HashMap<String, Any?>

        constructor(parentNode: PostmanNodeData, info: HashMap<String, Any?>) {
            this.info = info
            this.parentNode = parentNode
        }

        override fun toString(): String {
            return info.getOrDefault("name", "unknown").toString()
        }
    }

    class PostmanApiNodeData : PostmanNodeData {
        override fun currData(): HashMap<String, Any?> {
            return info
        }

        override fun getParentNodeData(): PostmanNodeData? {
            return parentNode
        }

        private var parentNode: PostmanNodeData

        var info: HashMap<String, Any?>

        constructor(parentNode: PostmanNodeData, info: HashMap<String, Any?>) {
            this.info = info
            this.parentNode = parentNode
        }

        override fun toString(): String {
            return info.getOrDefault("name", "unknown").toString()
        }

    }

    //endregion postman Node Data--------------------------------------------------


    //region handle drop--------------------------------------------------------

    fun handleDropEvent(fromModuleData: Any, toPostmanNodeData: Any) {

        val targetCollectionNodeData: PostmanNodeData = when (toPostmanNodeData) {
            is PostmanApiNodeData -> toPostmanNodeData.getParentNodeData()!!
            else -> toPostmanNodeData as PostmanNodeData
        }

        logger!!.info("drop [$fromModuleData] to $targetCollectionNodeData")

        val formatToPostmanInfo = formatPostmanInfo(fromModuleData)
        if (formatToPostmanInfo == null) {
            logger.info("no api can be moved")
            return
        }

        val rootPostmanNodeData = (toPostmanNodeData as PostmanNodeData).getRootNodeData()!!

        val targetCollectionPostmanNodeData =
                when (toPostmanNodeData) {
                    is PostmanApiNodeData -> toPostmanNodeData.getParentNodeData()!!
                    else -> toPostmanNodeData
                }

        val currData = targetCollectionNodeData.currData()
        val items = currData.computeIfAbsent("item") { ArrayList<HashMap<String, Any?>>() } as ArrayList<HashMap<String, Any?>>
        items.add(formatToPostmanInfo)

        val collection = (rootPostmanNodeData as CollectionPostmanNodeData).collection
        val collectionId = collection["id"].toString()

        postmanCachedHelper!!.updateCollection(collectionId, collection)
    }

    private fun formatPostmanInfo(projectNodeData: Any): HashMap<String, Any?>? {

        when (projectNodeData) {
            is ApiProjectNodeData -> return postmanFormatter!!.request2Item(projectNodeData.request)
            is ClassProjectNodeData -> {
                val subProjectNodeData: ArrayList<ApiProjectNodeData>? = projectNodeData.getSubProjectNodeData()
                        ?: return null

                val subItems: ArrayList<HashMap<String, Any?>> = ArrayList()
                subProjectNodeData!!.stream()
                        .map { it.request }
                        .map { postmanFormatter!!.request2Item(it) }
                        .forEach { subItems.add(it) }
                return postmanFormatter!!.wrapInfo(projectNodeData.cls, subItems)

            }
            is ModuleProjectNodeData -> {
                val subProjectNodeData: ArrayList<ClassProjectNodeData>? = projectNodeData.getSubProjectNodeData()
                        ?: return null

                val subItems: ArrayList<HashMap<String, Any?>> = ArrayList()
                subProjectNodeData!!.stream()
                        .map { formatPostmanInfo(it) }
                        .filter { it != null }
                        .forEach { subItems.add(it!!) }
                return postmanFormatter!!.wrapInfo(projectNodeData.module.name, subItems)
            }
            else -> return null
        }

    }

    //endregion handle drop--------------------------------------------------------

    class ApiTreeTransferHandler(private val apiDashboardDialog: ApiDashboardDialog) : TransferHandler() {

        override fun canImport(comp: JComponent?, transferFlavors: Array<out DataFlavor>?): Boolean {
            return super.canImport(comp, transferFlavors)
        }

        override fun importData(comp: JComponent?, t: Transferable?): Boolean {
            return super.importData(comp, t)
        }

        override fun createTransferable(component: JComponent?): Transferable {
            apiDashboardDialog.logger!!.info("createTransferable:$component")
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
            val postmanLoadFuture = this.postmanLoadFuture
            if (postmanLoadFuture != null && !postmanLoadFuture.isDone) {
                postmanLoadFuture.cancel(true)
            }
        } catch (e: Throwable) {
            logger!!.error("error to cancel postman load:" +
                    ExceptionUtils.getStackTrace(e))
        }
        actionContext!!.unHold()
        dispose()
    }

}
