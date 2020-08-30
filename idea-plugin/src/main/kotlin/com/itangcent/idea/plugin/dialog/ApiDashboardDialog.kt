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
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.safeComputeIfAbsent
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.plugin.api.export.ClassExporter
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanFormatter
import com.itangcent.idea.plugin.api.export.requestOnly
import com.itangcent.idea.psi.PsiResource
import com.itangcent.idea.psi.ResourceHelper
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.idea.swing.EasyApiTreeCellRenderer
import com.itangcent.idea.swing.IconCustomized
import com.itangcent.idea.swing.SafeHashHelper
import com.itangcent.idea.swing.ToolTipAble
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.extend.rx.from
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.FileType
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
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class ApiDashboardDialog : JDialog() {
    private var contentPane: JPanel? = null
    private var projectApiTree: JTree? = null
    private var postmanApiTree: JTree? = null
    private var projectApiPanel: JPanel? = null
    private var postmanPanel: JPanel? = null
    private var projectApModeButton: JButton? = null
    private var projectCollapseButton: JButton? = null

    private var postmanNewCollectionButton: JButton? = null
    private var postmanSyncButton: JButton? = null
    private var postmanCollapseButton: JButton? = null

    var postmanPopMenu: JPopupMenu? = null

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
    private val postmanCachedApiHelper: PostmanCachedApiHelper? = null

    @Inject
    private val postmanFormatter: PostmanFormatter? = null

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

        EasyIcons.CollapseAll.iconOnly(this.projectCollapseButton)
        EasyIcons.CollapseAll.iconOnly(this.postmanCollapseButton)
        EasyIcons.Add.iconOnly(this.postmanNewCollectionButton)
        EasyIcons.Refresh.iconOnly(this.postmanSyncButton)

        try {
            val projectCellRenderer = EasyApiTreeCellRenderer()

            this.projectApiTree!!.cellRenderer = projectCellRenderer

            projectCellRenderer.leafIcon = EasyIcons.Method
            projectCellRenderer.openIcon = EasyIcons.WebFolder
            projectCellRenderer.closedIcon = EasyIcons.WebFolder

            val postmanCellRenderer = EasyApiTreeCellRenderer()

            this.postmanApiTree!!.cellRenderer = postmanCellRenderer

            postmanCellRenderer.leafIcon = EasyIcons.Link
            postmanCellRenderer.openIcon = EasyIcons.WebFolder
            postmanCellRenderer.closedIcon = EasyIcons.WebFolder

        } catch (e: Exception) {
        }

        postmanPopMenu = JPopupMenu()

        val addItem = JMenuItem("Add Collection")

        addItem.addActionListener {
            newSubPostmanAction()
        }

        val renameItem = JMenuItem("Rename")

        renameItem.addActionListener {
            renamePostmanAction()
        }
        val syncItem = JMenuItem("Sync")

        syncItem.addActionListener {
            try {
                syncPostmanAction()
            } catch (e: Exception) {
                logger!!.traceError("sync failed", e)

            }
        }

        val deleteItem = JMenuItem("Delete")

        deleteItem.addActionListener {
            try {
                deletePostmanAction()
            } catch (e: Exception) {
                logger!!.traceError("delete failed", e)

            }
        }

        postmanPopMenu!!.add(addItem)
        postmanPopMenu!!.add(renameItem)
        postmanPopMenu!!.add(syncItem)
        postmanPopMenu!!.add(deleteItem)

        this.postmanApiTree!!.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                if (e == null) return
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = postmanApiTree!!.getPathForLocation(e.x, e.y) ?: return

                    val targetComponent = path.lastPathComponent
                    val postmanNodeData = (targetComponent as DefaultMutableTreeNode).userObject

                    addItem.isEnabled = postmanNodeData !is PostmanApiNodeData
                    postmanPopMenu!!.show(postmanApiTree!!, e.x, e.y)
                    postmanApiTree!!.selectionPath = path
                }
            }
        })
    }

    @PostConstruct
    fun init() {
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
        for (contentRoot in sourceRoots) {
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
                                !disposed
                                        && FileType.acceptable(it.name)
                                        && (it is PsiClassOwner)
                            }) { psiFile ->
                        if (disposed) return@traversal
                        for (psiClass in (psiFile as PsiClassOwner).classes) {

                            if (disposed) return@traversal
                            classExporter!!.export(psiClass, requestOnly { request ->
                                if (disposed) return@requestOnly
                                if (request.resource == null) return@requestOnly
                                anyFound = true
                                val resourceClass = request.resourceClass()

                                val clsTreeNode = classNodeMap.safeComputeIfAbsent(resourceClass!!) {
                                    val classProjectNodeData = ClassProjectNodeData(this, resourceClass, resourceHelper!!.findAttrOfClass(resourceClass))
                                    val node = DefaultMutableTreeNode(classProjectNodeData)
                                    moduleNode.add(node)
                                    (moduleNode.userObject as ModuleProjectNodeData).addSubProjectNodeData(classProjectNodeData)
                                    node
                                }!!

                                val apiProjectNodeData = ApiProjectNodeData(this, request)

                                val apiTreeNode = DefaultMutableTreeNode(apiProjectNodeData)
                                apiTreeNode.allowsChildren = false
                                clsTreeNode.add(apiTreeNode)
                                (clsTreeNode.userObject as ClassProjectNodeData).addSubProjectNodeData(apiProjectNodeData)
                            })
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

    private var postmanAvailable: Boolean = true

    private fun initPostmanInfo() {

        postmanApiTree!!.model = null

        autoComputer.bindEnable(this.postmanCollapseButton!!)
                .from(this::postmanAvailable)
        autoComputer.bindEnable(this.postmanSyncButton!!)
                .from(this::postmanAvailable)
        autoComputer.bindEnable(this.postmanNewCollectionButton!!)
                .from(this::postmanAvailable)

        if (!postmanCachedApiHelper!!.hasPrivateToken()) {
            autoComputer.value(this::postmanAvailable, false)

            tryInputPostmanPrivateToken()
        } else {
            loadPostmanInfo(true)
        }

        //drop drag from api to postman
        DropTarget(this.postmanApiTree, DnDConstants.ACTION_COPY_OR_MOVE, object : DropTargetAdapter() {

            override fun drop(dtde: DropTargetDropEvent?) {
                if (dtde == null) return


                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)

                    val tp: TreePath = postmanApiTree!!.getPathForLocation(dtde.location.x, dtde.location.y) ?: return
                    val targetComponent = tp.lastPathComponent
                    val postmanNodeData = (targetComponent as DefaultMutableTreeNode).userObject

                    val transferable = dtde.transferable
                    val wrapDataFlavor = getWrapDataFlavor()

                    val transferData = transferable.getTransferData(wrapDataFlavor)

                    val projectNodeData = (transferData as WrapData).wrapHash?.let { safeHashHelper.getBean(it) }
                            ?: return

                    handleDropEvent(projectNodeData, postmanNodeData)

                } catch (e: java.lang.Exception) {
                    logger!!.traceError("drop failed", e)

                } finally {
                    dtde.dropComplete(true)
                }
            }
        })

        this.postmanCollapseButton!!.addActionListener {
            try {
                SwingUtils.expandOrCollapseNode(this.postmanApiTree!!, false)
            } catch (e: Exception) {
                logger!!.error("try collapse postman apis failed!")
            }
        }

        this.postmanSyncButton!!.addActionListener {
            ((this.postmanApiTree!!.model as DefaultTreeModel).root as DefaultMutableTreeNode).removeAllChildren()
            loadPostmanInfo(false)
        }

        this.postmanNewCollectionButton!!.addActionListener {
            newPostmanCollection()
        }

    }

    private fun tryInputPostmanPrivateToken() {
        actionContext!!.runAsync {
            Thread.sleep(200)
            actionContext!!.runInSwingUI {
                val postmanPrivateToken = Messages.showInputDialog(this, "Input Postman Private Token",
                        "Postman Private Token", Messages.getInformationIcon())
                if (postmanPrivateToken.isNullOrBlank()) return@runInSwingUI

                postmanCachedApiHelper!!.setPrivateToken(postmanPrivateToken)
                autoComputer.value(this::postmanAvailable, true)
                loadPostmanInfo(true)
            }
        }
    }

    private fun loadPostmanInfo(useCache: Boolean) {

        if (!postmanCachedApiHelper!!.hasPrivateToken()) {
            actionContext!!.runInSwingUI {
                Messages.showErrorDialog(this,
                        "load postman info failed,no token be found", "Error")
            }
            logger!!.info("If you do not have a privateToken of postman, you can easily generate one by heading over to the" +
                    " Postman Integrations Dashboard [https://go.postman.co/integrations/services/pm_pro_api].")
            return
        }

        actionContext!!.runInSwingUI {
            //            postmanApiTree!!.dragEnabled = true
            val treeNode = DefaultMutableTreeNode()
            val rootTreeModel = DefaultTreeModel(treeNode, true)

            actionContext!!.runAsync {

                val collections = postmanCachedApiHelper.getAllCollection(useCache)
                if (collections.isNullOrEmpty()) {
                    if (collections == null) {
                        actionContext!!.runInSwingUI {
                            Messages.showErrorDialog(actionContext!!.instance(Project::class),
                                    "load postman info failed", "Error")
                        }
                    } else {
                        actionContext!!.runInSwingUI {
                            Messages.showErrorDialog(actionContext!!.instance(Project::class),
                                    "No collection be found", "Error")
                        }
                    }

                    return@runAsync
                }
                val collectionNodes: ArrayList<DefaultMutableTreeNode> = ArrayList()

                actionContext!!.runInSwingUI {
                    for (collection in collections) {
                        val collectionNode = PostmanCollectionNodeData(collection).asTreeNode()
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
                            loadPostCollectionInfo(collectionNode, useCache)

                        }
                        postmanLoadFuture = null
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPostCollectionInfo(collectionNode: DefaultMutableTreeNode, useCache: Boolean) {
        val moduleData = collectionNode.userObject as PostmanCollectionNodeData
        val collectionId = moduleData.collection["id"]
        val postmanApiTreeModel = postmanApiTree!!.model as DefaultTreeModel
        if (collectionId == null) {
            actionContext!!.runInSwingUI {
                collectionNode.removeFromParent()
                postmanApiTreeModel.reload(collectionNode)
            }
            return
        }

        actionContext!!.runAsync {
            moduleData.status = NodeStatus.Loading
            val collectionInfo = postmanCachedApiHelper!!.getCollectionInfo(collectionId.toString(), useCache)
            if (collectionInfo == null) {
                moduleData.status = NodeStatus.Loaded
                return@runAsync
            }
            try {
                moduleData.detail = collectionInfo
                val items = findEditableItem(collectionInfo)

                actionContext!!.runInSwingUI {
                    for (item in items) {
                        loadPostmanNode(collectionNode, item)
                    }
                }
                actionContext!!.runInSwingUI {
                    postmanApiTreeModel.reload(collectionNode)
                }
            } finally {
                moduleData.status = NodeStatus.Loaded
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPostmanNode(parentNode: DefaultMutableTreeNode, item: HashMap<String, Any?>) {
        if (item.isNullOrEmpty()) return

        actionContext!!.runInSwingUI {
            val parentNodeData = parentNode.userObject as PostmanNodeData
            Thread.yield()
            if (item.containsKey("request")) {//is request
                val apiTreeNode = PostmanApiNodeData(parentNodeData, item).asTreeNode()
                apiTreeNode.allowsChildren = false
                parentNode.add(apiTreeNode)

            } else {//is sub collection
                val subCollectionNode = PostmanSubCollectionNodeData(parentNodeData, item).asTreeNode()
                parentNode.add(subCollectionNode)

                val items = findEditableItem(item)
                if (items.isNullOrEmpty()) return@runInSwingUI
                for (subItem in items) {
                    loadPostmanNode(subCollectionNode, subItem)
                }
            }
        }
    }

    private fun newPostmanCollection() {
        actionContext!!.runInSwingUI {
            val newCollectionName = Messages.showInputDialog(this,
                    "Input New Collection Name",
                    "New Collection",
                    Messages.getInformationIcon())
            if (newCollectionName.isNullOrBlank()) return@runInSwingUI

            actionContext!!.runAsync {
                val info: HashMap<String, Any?> = HashMap()
                info["name"] = newCollectionName
                info["description"] = "create by easyApi at ${DateUtils.formatYMD_HMS(Date())}"
                info["schema"] = PostmanFormatter.POSTMAN_SCHEMA_V2_1_0

                val collection: HashMap<String, Any?> = HashMap()

                collection["info"] = info
                collection["item"] = ArrayList<Any?>()

                val createdCollection = postmanCachedApiHelper!!.createCollection(collection)
                if (createdCollection == null) {
                    logger!!.error("create collection failed")
                } else {
                    val collectionPostmanNodeData = PostmanCollectionNodeData(createdCollection)
                    collectionPostmanNodeData.status = NodeStatus.Loaded
                    val collectionTreeNode = collectionPostmanNodeData.asTreeNode()
                    actionContext!!.runInSwingUI {
                        val treeModel = postmanApiTree!!.model as DefaultTreeModel
                        val rootTreeNode = treeModel.root as DefaultMutableTreeNode
                        rootTreeNode.add(collectionTreeNode)
                        treeModel.reload(rootTreeNode)
                    }
                }
            }
        }
    }

    //endregion postman module-----------------------------------------------------

    //region postman pop action---------------------------------------------------------
    private fun newSubPostmanAction() {
        val lastSelectedPathComponent = postmanApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

        if (lastSelectedPathComponent != null) {
            val postmanNodeData = lastSelectedPathComponent.userObject ?: return
            actionContext!!.runInSwingUI {
                val newCollectionName = Messages.showInputDialog(this, "Input Sub Collection Name",
                        "Collection Name", Messages.getInformationIcon())
                if (newCollectionName.isNullOrBlank()) return@runInSwingUI

                actionContext!!.runAsync {

                    try {
                        val newCollection: HashMap<String, Any?> = HashMap()
                        newCollection["name"] = newCollectionName
                        newCollection["description"] = "create by easyApi at ${DateUtils.formatYMD_HMS(Date())}"
                        newCollection["item"] = ArrayList<Any?>()
                        findEditableItem((postmanNodeData as PostmanNodeData).currData()).add(newCollection)

                        val rootPostmanNodeData = postmanNodeData.getRootNodeData()!! as PostmanCollectionNodeData
                        rootPostmanNodeData.status = NodeStatus.Uploading

                        val collection = rootPostmanNodeData.collection
                        val collectionId = collection["id"].toString()

                        logger!!.info("upload info...")
                        if (postmanCachedApiHelper!!.updateCollection(collectionId, rootPostmanNodeData.currData())) {
                            logger.info("create success")
                            actionContext!!.runInSwingUI {
                                rootPostmanNodeData.status = NodeStatus.Loaded
                                loadPostmanNode(postmanNodeData.asTreeNode(), newCollection)
                                (postmanApiTree!!.model as DefaultTreeModel).reload(postmanNodeData.asTreeNode())
                            }
                        } else {
                            logger.error("create failed")
                            rootPostmanNodeData.status = NodeStatus.Loaded
                        }
                    } catch (e: Exception) {
                        logger!!.traceError("create failed", e)

                    }
                }
            }

        }
    }

    private fun renamePostmanAction() {
        val lastSelectedPathComponent = postmanApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

        if (lastSelectedPathComponent != null) {
            val postmanNodeData = lastSelectedPathComponent.userObject ?: return
            val coreData = (postmanNodeData as PostmanNodeData).coreData()
            val oldName = coreData["name"].toString()
            actionContext!!.runInSwingUI {
                val newName = Messages.showInputDialog(this, "New Name",
                        "New Name", Messages.getInformationIcon(), oldName, null)
                if (newName.isNullOrBlank() || newName == oldName) return@runInSwingUI

                try {
                    actionContext!!.runAsync {
                        coreData["name"] = newName

                        val rootPostmanNodeData = postmanNodeData.getRootNodeData()!! as PostmanCollectionNodeData
                        rootPostmanNodeData.status = NodeStatus.Uploading

                        val collection = rootPostmanNodeData.collection
                        val collectionId = collection["id"].toString()

                        logger!!.info("upload info...")
                        if (postmanCachedApiHelper!!.updateCollection(collectionId, rootPostmanNodeData.currData())) {
                            logger.info("rename success")
                            actionContext!!.runInSwingUI {
                                rootPostmanNodeData.status = NodeStatus.Loaded
                                (postmanApiTree!!.model as DefaultTreeModel).reload(postmanNodeData.asTreeNode())
                            }
                        } else {
                            logger.error("rename failed")
                            rootPostmanNodeData.status = NodeStatus.Loaded
                        }
                    }
                } catch (e: Exception) {
                    logger!!.traceError("rename failed", e)

                }
            }
        }
    }

    private fun syncPostmanAction() {

        val lastSelectedPathComponent = postmanApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

        if (lastSelectedPathComponent != null) {
            val postmanNodeData = lastSelectedPathComponent.userObject
            logger!!.info("reload:[$postmanNodeData]")
            val collectionPostmanNodeData = (postmanNodeData as PostmanNodeData).getRootNodeData() ?: return
            //clear
            collectionPostmanNodeData.asTreeNode().removeAllChildren()
            //reload
            loadPostCollectionInfo(collectionPostmanNodeData.asTreeNode(), false)
        }
    }

    private fun deletePostmanAction() {

        val lastSelectedPathComponent = postmanApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

        if (lastSelectedPathComponent != null) {
            val postmanNodeData = lastSelectedPathComponent.userObject
            if (postmanNodeData is PostmanCollectionNodeData) {//delete collection
                logger!!.info("delete from remote...")
                actionContext!!.runAsync {
                    val collection = postmanNodeData.collection
                    val collectionId = collection["id"].toString()
                    if (postmanCachedApiHelper!!.deleteCollectionInfo(collectionId) != null) {
                        logger.info("delete success")
                        postmanNodeData.asTreeNode().removeFromParent()
                        (postmanApiTree!!.model as DefaultTreeModel).reload(postmanApiTree!!.model.root as TreeNode)
                    } else {
                        logger.error("delete failed")
                    }
                }
            } else {//delete sub collection or api
                if (findEditableItem((postmanNodeData as PostmanNodeData).getParentNodeData()!!.currData()).remove(postmanNodeData.currData())) {

                    val rootPostmanNodeData = postmanNodeData.getRootNodeData()!! as PostmanCollectionNodeData
                    rootPostmanNodeData.status = NodeStatus.Uploading

                    val collection = rootPostmanNodeData.collection
                    val collectionId = collection["id"].toString()

                    logger!!.info("delete from remote...")
                    actionContext!!.runAsync {
                        try {
                            if (postmanCachedApiHelper!!.updateCollection(collectionId, rootPostmanNodeData.currData())) {
                                logger.info("delete success")
                                actionContext!!.runInSwingUI {
                                    postmanNodeData.asTreeNode().removeFromParent()
                                    rootPostmanNodeData.status = NodeStatus.Loaded
                                    (postmanApiTree!!.model as DefaultTreeModel).reload(rootPostmanNodeData.asTreeNode())
                                }
                            } else {
                                logger.error("delete failed")
                                rootPostmanNodeData.status = NodeStatus.Loaded
                            }
                        } catch (e: Exception) {
                            rootPostmanNodeData.status = NodeStatus.Loaded
                            logger.error("delete failed")
                        }

                    }
                    return
                }
                logger!!.error("delete failed")
            }
        }
    }
    //endregion postman pop action---------------------------------------------------------

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

        private val apiDashboardDialog: ApiDashboardDialog

        constructor(apiDashboardDialog: ApiDashboardDialog, cls: PsiClass) {
            this.cls = cls
            this.apiDashboardDialog = apiDashboardDialog
        }

        constructor(apiDashboardDialog: ApiDashboardDialog, cls: PsiClass, attr: String?) {
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

    class ApiProjectNodeData : IconCustomized, ToolTipAble {
        override fun toolTip(): String? {
            return "${PsiClassUtils.fullNameOfMethod(request.resourceClass()!!, request.resourceMethod()!!)}\n${request.method}:${request.path}"
        }

        private val apiDashboardDialog: ApiDashboardDialog

        override fun icon(): Icon? {
            return EasyIcons.Method
        }

        var request: Request

        constructor(apiDashboardDialog: ApiDashboardDialog, request: Request) {
            this.request = request
            this.apiDashboardDialog = apiDashboardDialog
        }

        override fun toString(): String {
            if (this.apiDashboardDialog.projectMode == ProjectMode.Original) {
                return ((request.resource as PsiResource?)?.resource() as PsiMethod?)?.name ?: request.name
                ?: "anonymous"
            }
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
        open fun currData(): HashMap<String, Any?> {
            return coreData()
        }

        abstract fun coreData(): HashMap<String, Any?>

        fun getRootNodeData(): PostmanNodeData? {
            val parentCollectionInfo = getParentNodeData()
            return when (parentCollectionInfo) {
                null -> this
                else -> parentCollectionInfo.getRootNodeData()
            }
        }

        abstract fun getParentNodeData(): PostmanNodeData?

        var treeNode: DefaultMutableTreeNode? = null

        fun asTreeNode(): DefaultMutableTreeNode {
            if (treeNode != null) return treeNode!!
            treeNode = DefaultMutableTreeNode(this)
            return treeNode!!

        }
    }

    class PostmanCollectionNodeData : PostmanNodeData, IconCustomized, ToolTipAble {
        override fun icon(): Icon? {
            return when (status) {
                NodeStatus.Loading -> EasyIcons.Refresh
                NodeStatus.Uploading -> EasyIcons.UpFolder
                else -> null
            } ?: EasyIcons.ModuleGroup
        }

        @Suppress("UNCHECKED_CAST")
        override fun currData(): HashMap<String, Any?> {
            if (detail != null) {
                (detail!!["info"] as MutableMap<String, Any?>)["name"] = this.collection["name"]
                return detail!!
            }

            val collection: HashMap<String, Any?> = HashMap()

            val info = HashMap<String, Any?>()
            info["name"] = this.collection["name"]
            info["_postman_id"] = this.collection["id"]
            info["schema"] = PostmanFormatter.POSTMAN_SCHEMA_V2_1_0

            collection["info"] = info
            collection["item"] = ArrayList<HashMap<String, Any?>>()

            detail = collection
            return collection
        }

        override fun coreData(): HashMap<String, Any?> {
            return collection
        }

        override fun getParentNodeData(): PostmanNodeData? {
            return null
        }

        var collection: HashMap<String, Any?>

        var detail: HashMap<String, Any?>? = null

        var status = NodeStatus.Unload

        constructor(collection: HashMap<String, Any?>) {
            this.collection = collection
        }

        override fun toString(): String {
            return status.desc + collection.getOrDefault("name", "unknown")
        }

        @Suppress("UNCHECKED_CAST")
        override fun toolTip(): String? {
            if (detail == null) return null
            val info = detail!!["info"] ?: return null
            return (info as Map<*, *>)["description"]?.toString()
        }
    }

    class PostmanSubCollectionNodeData : PostmanNodeData, IconCustomized, ToolTipAble {
        override fun icon(): Icon? {
            return EasyIcons.Module
        }

        override fun coreData(): HashMap<String, Any?> {
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

        @Suppress("UNCHECKED_CAST")
        override fun toolTip(): String? {
            return info["description"]?.toString()
        }
    }

    class PostmanApiNodeData : PostmanNodeData, IconCustomized, ToolTipAble {

        override fun icon(): Icon? {
            return EasyIcons.Link
        }

        override fun coreData(): HashMap<String, Any?> {
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

        @Suppress("UNCHECKED_CAST")
        override fun toolTip(): String? {
            val request = (info["request"] as HashMap<String, Any?>?) ?: return null
            val sb = StringBuilder()
            val method = request["method"]
            if (method != null) {
                sb.append(method).append(":")
            }
            val url = request["url"]
            if (url != null) {
                if (url is Map<*, *>) {
                    sb.append(url["raw"])
                } else {
                    sb.append(url)
                }
            }
            val description = request["description"]
            if (description != null) {
                sb.append("\n").append(description)
            }
            return sb.toString()
        }
    }

    //endregion postman Node Data--------------------------------------------------

    //region handle drop--------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    fun handleDropEvent(fromProjectData: Any, toPostmanNodeData: Any) {

        val targetCollectionNodeData: PostmanNodeData = when (toPostmanNodeData) {
            is PostmanApiNodeData -> toPostmanNodeData.getParentNodeData()!!
            else -> toPostmanNodeData as PostmanNodeData
        }

        logger!!.debug("export [$fromProjectData] to $targetCollectionNodeData")

        actionContext!!.runAsync {
            var rootPostmanNodeData: PostmanCollectionNodeData? = null
            try {

                logger.info("parse api...")
                val formatToPostmanInfo = formatPostmanInfo(fromProjectData)
                if (formatToPostmanInfo == null) {
                    logger.info("no api can be moved")
                    return@runAsync
                }

                rootPostmanNodeData = (toPostmanNodeData as PostmanNodeData).getRootNodeData()!! as PostmanCollectionNodeData
                rootPostmanNodeData.status = NodeStatus.Uploading
                actionContext!!.runInSwingUI {
                    (postmanApiTree!!.model as DefaultTreeModel).reload(rootPostmanNodeData.asTreeNode())
                }

                val currData = targetCollectionNodeData.currData()
                val items = findEditableItem(currData)
                items.add(formatToPostmanInfo)

                val collection = rootPostmanNodeData.collection
                val collectionId = collection["id"].toString()

                logger.info("upload api...")
                if (postmanCachedApiHelper!!.updateCollection(collectionId, rootPostmanNodeData.currData())) {
                    logger.info("export success")
                    rootPostmanNodeData.status = NodeStatus.Loaded

                    actionContext!!.runInSwingUI {
                        loadPostmanNode(targetCollectionNodeData.asTreeNode(), formatToPostmanInfo)
                        (postmanApiTree!!.model as DefaultTreeModel).reload(targetCollectionNodeData.asTreeNode())
                    }
                } else {
                    logger.error("export failed")
                    rootPostmanNodeData.status = NodeStatus.Loaded
                }
            } catch (e: Exception) {
                logger.traceError("export failed", e)

                rootPostmanNodeData!!.status = NodeStatus.Loaded
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun findEditableItem(data: HashMap<String, Any?>): ArrayList<HashMap<String, Any?>> {
        var items = data["item"]
        if (items != null) {
            if (items is ArrayList<*>) {
                val firstOrNull = items.firstOrNull()
                if (firstOrNull != null && firstOrNull !is HashMap<*, *>) {
                    val arrayListItems = ArrayList<HashMap<String, Any?>>()
                    items.forEach { arrayListItems.add(castToHashMap(it)) }
                    data["item"] = arrayListItems
                    return arrayListItems
                }
                return items as ArrayList<HashMap<String, Any?>>
            }

            if (items is List<*>) {
                val arrayListItems = ArrayList<HashMap<String, Any?>>()
                items.forEach { arrayListItems.add(castToHashMap(it)) }
                data["item"] = arrayListItems
                return arrayListItems
            }

        }

        items = ArrayList<HashMap<String, Any?>>()
        data["item"] = items
        return items
    }

    @Suppress("UNCHECKED_CAST")
    private fun castToHashMap(obj: Any?): HashMap<String, Any?> {
        if (obj is HashMap<*, *>) {
            return obj as HashMap<String, Any?>
        }

        if (obj is Map<*, *>) {
            val map: HashMap<String, Any?> = HashMap()
            obj.forEach { k, v -> map[k.toString()] = v }
            return map
        }
        return HashMap()
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
        actionContext!!.stop(false)
        dispose()
    }

}

