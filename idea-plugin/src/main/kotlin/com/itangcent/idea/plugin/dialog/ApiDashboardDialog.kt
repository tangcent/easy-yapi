package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.openapi.ui.Messages
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Doc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.*
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanFormatter
import com.itangcent.idea.plugin.api.export.postman.PostmanUrls.INTEGRATIONS_DASHBOARD
import com.itangcent.idea.plugin.api.export.postman.getEditableItem
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.idea.swing.EasyApiTreeCellRenderer
import com.itangcent.idea.swing.IconCustomized
import com.itangcent.idea.swing.ToolTipAble
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.idea.utils.clear
import com.itangcent.idea.utils.reload
import com.itangcent.idea.utils.remove
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.rx.eval
import com.itangcent.intellij.extend.rx.from
import com.itangcent.task.AsyncTask
import com.itangcent.task.Task
import com.itangcent.task.TaskManager
import org.apache.commons.lang3.exception.ExceptionUtils
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.*
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeNode
import javax.swing.tree.TreePath


class ApiDashboardDialog : AbstractApiDashboardDialog() {

    override var contentPane: JPanel? = null
    override var projectApiTree: JTree? = null
    var postmanApiTree: JTree? = null
    override var projectApiPanel: JPanel? = null
    var postmanPanel: JPanel? = null
    private var postmanWorkspaceComboBox: JComboBox<WorkspaceWrapper>? = null
    override var projectApiModeButton: JButton? = null
    override var projectCollapseButton: JButton? = null

    private var postmanNewCollectionButton: JButton? = null
    private var postmanSyncButton: JButton? = null
    private var postmanCollapseButton: JButton? = null

    var postmanPopMenu: JPopupMenu? = null

    @Inject
    private lateinit var postmanCachedApiHelper: PostmanCachedApiHelper

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    @Inject
    private val postmanFormatter: PostmanFormatter? = null

    private var currentWorkspace: WorkspaceWrapper? = null

    protected var taskManager = TaskManager()

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
                logger.traceError("sync failed", e)

            }
        }

        val deleteItem = JMenuItem("Delete")

        deleteItem.addActionListener {
            try {
                deletePostmanAction()
            } catch (e: Exception) {
                logger.traceError("delete failed", e)

            }
        }

        val curlItem = JMenuItem("Copy Curl")
        curlItem.addActionListener {
            selectedPostmanNode()?.let { copyCurl(it) }
        }

        postmanPopMenu!!.add(addItem)
        postmanPopMenu!!.add(renameItem)
        postmanPopMenu!!.add(syncItem)
        postmanPopMenu!!.add(deleteItem)
        postmanPopMenu!!.add(curlItem)

        this.postmanApiTree!!.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                if (e == null) return
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = postmanApiTree!!.getPathForLocation(e.x, e.y) ?: return

                    val targetComponent = path.lastPathComponent
                    val postmanNodeData = (targetComponent as DefaultMutableTreeNode).userObject as PostmanNodeData

                    addItem.isEnabled = postmanNodeData !is PostmanApiNodeData
                    syncItem.isEnabled = syncEnable(postmanNodeData)
                    curlItem.isEnabled = postmanNodeData.curlEnable()
                    postmanPopMenu!!.show(postmanApiTree!!, e.x, e.y)
                    postmanApiTree!!.selectionPath = path
                }
            }
        })

    }

    @PostConstruct
    fun init() {
        actionContext.hold()

        LOG.info("init project apis")
        initProjectApiModule()

        LOG.info("init postman collections")
        initPostmanInfo()
    }

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

        if (!postmanSettingsHelper.hasPrivateToken()) {
            autoComputer.value(this::postmanAvailable, false)
            tryInputPostmanPrivateToken()
        } else {
            loadPostmanWorkspace()
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

                    val projectNodeData =
                        (transferData as WrapData).wrapHash?.let { safeHashHelper.getBean(it) } as? ProjectNodeData
                            ?: return

                    handleDropEvent(projectNodeData, postmanNodeData)

                } catch (e: java.lang.Exception) {
                    logger.traceError("drop failed", e)

                } finally {
                    dtde.dropComplete(true)
                }
            }
        })

        this.postmanCollapseButton!!.addActionListener {
            try {
                SwingUtils.expandOrCollapseNode(this.postmanApiTree!!, false)
            } catch (e: Exception) {
                logger.error("try collapse postman apis failed!")
            }
        }

        this.postmanSyncButton!!.addActionListener {
            this.postmanApiTree!!.model.clear()
            //loadPostmanWorkspace() 只刷新当前选中的workspace下的collection
            loadPostmanCollection(false)
        }

        this.postmanNewCollectionButton!!.addActionListener {
            newPostmanCollection()
        }

    }

    private fun tryInputPostmanPrivateToken() {
        actionContext.runAsync {
            Thread.sleep(200)
            val postmanPrivateToken = postmanSettingsHelper.getPrivateToken(false)
            if (postmanPrivateToken.notNullOrBlank()) {
                autoComputer.value(this::postmanAvailable, true)
                loadPostmanWorkspace()
            }
        }
    }

    private fun loadPostmanWorkspace() {

        if (!postmanSettingsHelper.hasPrivateToken()) {
            actionContext.runInSwingUI {
                Messages.showErrorDialog(
                    this,
                    "load postman workspace failed,no token be found", "Error"
                )
            }
            logger.info(
                "If you do not have a privateToken of postman, you can easily generate one by heading over to the" +
                        " Postman Integrations Dashboard [$INTEGRATIONS_DASHBOARD]."
            )
            return
        }

        actionContext.runAsync {
            val workspaces = postmanCachedApiHelper.getAllWorkspaces() // 不使用缓存
            if (workspaces.isNullOrEmpty()) {
                if (workspaces == null) {
                    actionContext.runInSwingUI {
                        Messages.showErrorDialog(
                            this,
                            "load postman workspace failed", "Error"
                        )
                    }
                } else {
                    logger.warn("no postman workspace be found. this will never happen.")
                }
                return@runAsync
            }
            actionContext.runInSwingUI {
                postmanWorkspaceComboBox!!.removeAllItems()
                val workSpacesArrData = workspaces.mapToTypedArray { WorkspaceWrapper(it.id, it.nameWithType()) }
                postmanWorkspaceComboBox!!.model =
                    DefaultComboBoxModel(workSpacesArrData)
                postmanWorkspaceComboBox!!.selectedIndex = 0
                this.currentWorkspace = workSpacesArrData[0]

                autoComputer.bind(this::currentWorkspace)
                    .with(this.postmanWorkspaceComboBox!!)
                    .eval()

                autoComputer.listen(this.postmanWorkspaceComboBox!!)
                    .action { loadPostmanCollection(true) }

                loadPostmanCollection(true)
            }
        }
    }

    private class PostmanCollectionLoadTask(
        val apiDashboardDialog: ApiDashboardDialog,
        val useCache: Boolean,
        actionContext: ActionContext,
        taskManager: TaskManager
    ) : AsyncTask(
        actionContext, taskManager
    ) {
        var collectionInfoLoadingFuture: Future<*>? = null

        override fun doTask(): Int {
            try {
                val workspace = apiDashboardDialog.currentWorkspace ?: return Task.DONE

                val treeNode = DefaultMutableTreeNode()
                val rootTreeModel = DefaultTreeModel(treeNode, true)
                synchronized(apiDashboardDialog)
                {
                    if (disposed()) {
                        return Task.DONE
                    }
                    apiDashboardDialog.postmanApiTree!!.model = rootTreeModel
                }
                val collections =
                    apiDashboardDialog.postmanCachedApiHelper.getCollectionByWorkspace(workspace.id!!, useCache)
                if (collections.isNullOrEmpty()) {
                    if (collections == null) {
                        apiDashboardDialog.actionContext.runInSwingUI {
                            Messages.showErrorDialog(
                                apiDashboardDialog,
                                "load postman info failed", "Error"
                            )
                        }
                    } else {
                        apiDashboardDialog.logger.debug("No collection be found")
                    }
                    return Task.DONE
                }
                val collectionNodes: ArrayList<PostmanCollectionNodeData> = ArrayList()

                apiDashboardDialog.actionContext.runInSwingUI {
                    for (collection in collections) {
                        if (disposed()) {
                            complete()
                            return@runInSwingUI
                        }
                        val collectionNode = PostmanCollectionNodeData(collection)
                        treeNode.add(collectionNode.asTreeNode())
                        collectionNodes.add(collectionNode)
                        rootTreeModel.reload(collectionNode.asTreeNode())
                    }
                    rootTreeModel.reload()
                    collectionInfoLoadingFuture = apiDashboardDialog.actionContext.runAsync {
                        try {
                            val countDown = CountDownLatch(collectionNodes.size)
                            val semaphore = Semaphore(3)
                            for (collectionNode in collectionNodes) {
                                if (disposed()) {
                                    break
                                }
                                semaphore.acquire()
                                try {
                                    if (disposed()) {
                                        break
                                    }
                                    apiDashboardDialog.loadPostCollectionInfo(collectionNode, useCache, this) {
                                        semaphore.release()
                                        countDown.countDown()
                                    }
                                } catch (e: Exception) {
                                    semaphore.release()
                                    countDown.countDown()
                                }
                            }
                            countDown.await()
                        } catch (e: InterruptedException) {
                            if (!disposed()) {
                                throw e
                            }
                        } finally {
                            complete()
                            collectionInfoLoadingFuture = null
                        }
                    }
                }
                return Task.RUNNING
            } catch (e: Exception) {
                apiDashboardDialog.logger.traceError("failed load postman collection", e)
                return Task.DONE
            }
        }

        override fun terminate(): Boolean {
            val ret = super.terminate()
            collectionInfoLoadingFuture?.let {
                KitUtils.safe { it.cancel(true) }
            }
            return ret
        }
    }

    private fun loadPostmanCollection(useCache: Boolean) {
        PostmanCollectionLoadTask(this, useCache, actionContext, taskManager).start()
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPostCollectionInfo(
        collectionNode: PostmanCollectionNodeData,
        useCache: Boolean,
        task: Task? = null,
        onCompleted: (() -> Unit)? = null
    ) {
        val collectionId: Any?
        val postmanApiTreeModel: DefaultTreeModel
        try {
            collectionId = collectionNode.collection["id"]
            postmanApiTreeModel = postmanApiTree!!.model as DefaultTreeModel
            if (collectionId == null) {
                collectionNode.status = NodeStatus.Loaded
                actionContext.runInSwingUI {
                    if (task?.disposed() == true) {
                        return@runInSwingUI
                    }
                    postmanApiTreeModel.remove(collectionNode.asTreeNode())
                }
                onCompleted?.invoke()
                return
            }
        } catch (e: Exception) {
            onCompleted?.invoke()
            return
        }

        actionContext.runAsync {
            try {
                if (task?.disposed() == true) {
                    collectionNode.status = NodeStatus.Loaded
                    onCompleted?.invoke()
                    return@runAsync
                }
                collectionNode.status = NodeStatus.Loading
                val collectionInfo = postmanCachedApiHelper.getCollectionInfo(collectionId.toString(), useCache)
                if (collectionInfo == null || task?.disposed() == true) {
                    if (collectionInfo == null) {
                        collectionNode.status = NodeStatus.Deleted
                        postmanApiTreeModel.remove(collectionNode.asTreeNode())
                    } else {
                        collectionNode.status = NodeStatus.Loaded
                    }
                    onCompleted?.invoke()
                    return@runAsync
                }
                collectionNode.detail = collectionInfo
                val items = collectionInfo.getEditableItem()

                actionContext.runInSwingUI {
                    try {
                        for (item in items) {
                            if (task?.disposed() == true) {
                                return@runInSwingUI
                            }
                            loadPostmanNode(collectionNode, item)
                        }
                        if (task?.disposed() == true) {
                            return@runInSwingUI
                        }
                        postmanApiTreeModel.reload(collectionNode.asTreeNode())
                    } finally {
                        collectionNode.status = NodeStatus.Loaded
                        onCompleted?.invoke()
                    }
                }
            } catch (e: Throwable) {
                collectionNode.status = NodeStatus.Loaded
                onCompleted?.invoke()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadPostmanNode(parentNode: PostmanNodeData, item: HashMap<String, Any?>) {
        if (item.isNullOrEmpty()) return

        actionContext.runInSwingUI {
            if (item.containsKey("request")) {//is request
                val apiNodeData = PostmanApiNodeData(item)
                parentNode.addSubNodeData(apiNodeData)
            } else {//is sub collection
                val subCollectionNode = PostmanSubCollectionNodeData(item)
                parentNode.addSubNodeData(subCollectionNode)

                val items = item.getEditableItem()
                if (items.isNullOrEmpty()) return@runInSwingUI
                for (subItem in items) {
                    loadPostmanNode(subCollectionNode, subItem)
                }
            }
        }
    }

    private fun newPostmanCollection() {
        val currentWorkspace = this.currentWorkspace
        if (currentWorkspace == null) {
            actionContext.runInSwingUI {
                Messages.showErrorDialog(
                    this,
                    "no workspace be selected", "Error"
                )
            }
            return
        }
        actionContext.runInSwingUI {
            val newCollectionName = Messages.showInputDialog(
                this,
                "Input New Collection Name",
                "New Collection",
                Messages.getInformationIcon()
            )
            if (newCollectionName.isNullOrBlank()) return@runInSwingUI

            actionContext.runAsync {
                val info: HashMap<String, Any?> = HashMap()
                info["name"] = newCollectionName
                info["description"] = "create by easyApi at ${DateUtils.formatYMD_HMS(Date())}"
                info["schema"] = PostmanFormatter.POSTMAN_SCHEMA_V2_1_0

                val collection: HashMap<String, Any?> = HashMap()

                collection["info"] = info
                collection["item"] = ArrayList<Any?>()

                val createdCollection = postmanCachedApiHelper.createCollection(
                    collection,
                    currentWorkspace.id
                )
                if (createdCollection == null) {
                    logger.error("create collection failed")
                } else {
                    val collectionPostmanNodeData = PostmanCollectionNodeData(createdCollection)
                    collectionPostmanNodeData.status = NodeStatus.Loaded
                    val collectionTreeNode = collectionPostmanNodeData.asTreeNode()
                    actionContext.runInSwingUI {
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
            actionContext.runInSwingUI {
                val newCollectionName = Messages.showInputDialog(
                    this, "Input Sub Collection Name",
                    "Collection Name", Messages.getInformationIcon()
                )
                if (newCollectionName.isNullOrBlank()) return@runInSwingUI

                actionContext.runAsync {

                    try {
                        val newCollection: HashMap<String, Any?> = HashMap()
                        newCollection["name"] = newCollectionName
                        newCollection["description"] = "create by easyApi at ${DateUtils.formatYMD_HMS(Date())}"
                        newCollection["item"] = ArrayList<Any?>()
                        (postmanNodeData as PostmanNodeData).currData().getEditableItem().add(newCollection)

                        val rootPostmanNodeData = postmanNodeData.getRootNodeData() as PostmanCollectionNodeData
                        rootPostmanNodeData.status = NodeStatus.Uploading

                        val collection = rootPostmanNodeData.collection
                        val collectionId = collection["id"].toString()

                        logger.info("upload info...")
                        if (postmanCachedApiHelper.updateCollection(collectionId, rootPostmanNodeData.currData())) {
                            logger.info("create success")
                            actionContext.runInSwingUI {
                                rootPostmanNodeData.status = NodeStatus.Loaded
                                loadPostmanNode(postmanNodeData, newCollection)
                                postmanApiTree!!.model.reload(postmanNodeData.asTreeNode())
                            }
                        } else {
                            logger.error("create failed")
                            rootPostmanNodeData.status = NodeStatus.Loaded
                        }
                    } catch (e: Exception) {
                        logger.traceError("create failed", e)

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
            actionContext.runInSwingUI {
                val newName = Messages.showInputDialog(
                    this, "New Name",
                    "New Name", Messages.getInformationIcon(), oldName, null
                )
                if (newName.isNullOrBlank() || newName == oldName) return@runInSwingUI

                try {
                    actionContext.runAsync {
                        coreData["name"] = newName

                        val rootPostmanNodeData = postmanNodeData.getRootNodeData()!! as PostmanCollectionNodeData
                        rootPostmanNodeData.status = NodeStatus.Uploading

                        val collection = rootPostmanNodeData.collection
                        val collectionId = collection["id"].toString()

                        logger.info("upload info...")
                        if (postmanCachedApiHelper!!.updateCollection(collectionId, rootPostmanNodeData.currData())) {
                            logger.info("rename success")
                            actionContext.runInSwingUI {
                                rootPostmanNodeData.status = NodeStatus.Loaded
                                postmanApiTree!!.model.reload(postmanNodeData.asTreeNode())
                            }
                        } else {
                            logger.error("rename failed")
                            rootPostmanNodeData.status = NodeStatus.Loaded
                        }
                    }
                } catch (e: Exception) {
                    logger.traceError("rename failed", e)

                }
            }
        }
    }

    private fun syncPostmanAction() {

        val lastSelectedPathComponent = postmanApiTree!!.lastSelectedPathComponent as? DefaultMutableTreeNode

        if (lastSelectedPathComponent != null) {
            val postmanNodeData = lastSelectedPathComponent.userObject
            logger.info("reload:[$postmanNodeData]")
            val collectionPostmanNodeData = (postmanNodeData as PostmanNodeData).getRootNodeData()
                    as? PostmanCollectionNodeData ?: return
            //clear
            postmanApiTree!!.model.clear(collectionPostmanNodeData.asTreeNode())
            //reload
            loadPostCollectionInfo(collectionPostmanNodeData, false)
        }
    }

    private fun syncEnable(postmanNodeData: Any?): Boolean {
        if (postmanNodeData == null) {
            return false
        }
        return postmanNodeData.let { it as? PostmanNodeData }
            ?.getRootNodeData()
            ?.let { it as? PostmanCollectionNodeData }
            ?.status == NodeStatus.Loaded
    }

    private fun deletePostmanAction() {

        val lastSelectedPathComponent = postmanApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

        if (lastSelectedPathComponent != null) {
            val postmanNodeData = lastSelectedPathComponent.userObject
            if (postmanNodeData is PostmanCollectionNodeData) {//delete collection
                logger.info("delete from remote...")
                actionContext.runAsync {
                    val collection = postmanNodeData.collection
                    val collectionId = collection["id"].toString()
                    if (postmanCachedApiHelper!!.deleteCollectionInfo(collectionId) != null) {
                        logger.info("delete success")
                        postmanNodeData.asTreeNode().removeFromParent()
                        postmanApiTree!!.model.reload(postmanApiTree!!.model.root as TreeNode)
                    } else {
                        logger.error("delete failed")
                    }
                }
            } else {//delete sub collection or api
                if ((postmanNodeData as PostmanNodeData).getParentNodeData()!!.currData().getEditableItem().remove(
                        postmanNodeData.currData()
                    )
                ) {
                    val rootPostmanNodeData = postmanNodeData.getRootNodeData() as PostmanCollectionNodeData
                    rootPostmanNodeData.status = NodeStatus.Uploading

                    val collection = rootPostmanNodeData.collection
                    val collectionId = collection["id"].toString()

                    logger.info("delete from remote...")
                    actionContext.runAsync {
                        try {
                            if (postmanCachedApiHelper.updateCollection(
                                    collectionId,
                                    rootPostmanNodeData.currData()
                                )
                            ) {
                                logger.info("delete success")
                                actionContext.runInSwingUI {
                                    postmanNodeData.asTreeNode().removeFromParent()
                                    rootPostmanNodeData.status = NodeStatus.Loaded
                                    postmanApiTree!!.model.reload(rootPostmanNodeData.asTreeNode())
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
                logger.error("delete failed")
            }
        }
    }

    private fun selectedPostmanNode(): PostmanNodeData? {
        return (postmanApiTree!!.lastSelectedPathComponent as? DefaultMutableTreeNode)
            ?.userObject as? PostmanNodeData
    }

    //endregion postman pop action---------------------------------------------------------

    //region postman Node Data--------------------------------------------------

    abstract class PostmanNodeData : DocContainer, TreeNodeData<PostmanNodeData>() {
        open fun currData(): HashMap<String, Any?> {
            return coreData()
        }

        abstract fun coreData(): HashMap<String, Any?>

        override fun docs(handle: (Doc) -> Unit) {
            this.getSubNodeData()?.forEach { it.docs(handle) }
        }

        open fun curlEnable(): Boolean {
            return true
        }
    }

    class PostmanCollectionNodeData(var collection: HashMap<String, Any?>) : PostmanNodeData(), IconCustomized,
        ToolTipAble {
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

        var detail: HashMap<String, Any?>? = null

        var status = NodeStatus.Unload

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

    class PostmanSubCollectionNodeData(
        var info: HashMap<String, Any?>
    ) : PostmanNodeData(), IconCustomized, ToolTipAble {
        override fun icon(): Icon? {
            return EasyIcons.Module
        }

        override fun coreData(): HashMap<String, Any?> {
            return info
        }

        override fun toString(): String {
            return info.getOrDefault("name", "unknown").toString()
        }

        @Suppress("UNCHECKED_CAST")
        override fun toolTip(): String? {
            return info["description"]?.toString()
        }
    }

    class PostmanApiNodeData(
        var info: HashMap<String, Any?>
    ) : PostmanNodeData(), IconCustomized, ToolTipAble {

        private val postmanFormatter: PostmanFormatter by lazy {
            ActionContext.getContext()!!.instance(PostmanFormatter::class)
        }

        override fun icon(): Icon? {
            return EasyIcons.Link
        }

        override fun coreData(): HashMap<String, Any?> {
            return info
        }

        override fun asTreeNode(): DefaultMutableTreeNode {
            return super.asTreeNode().also { it.allowsChildren = false }
        }

        override fun docs(handle: (Doc) -> Unit) {
            postmanFormatter.item2Request(this.info)?.let { handle(it) }
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
    fun handleDropEvent(fromProjectData: ProjectNodeData, toPostmanNodeData: Any) {

        val targetCollectionNodeData: PostmanNodeData = when (toPostmanNodeData) {
            is PostmanApiNodeData -> toPostmanNodeData.getParentNodeData()!!
            else -> toPostmanNodeData as PostmanNodeData
        }

        logger.debug("export [$fromProjectData] to $targetCollectionNodeData")

        actionContext.runAsync {
            var rootPostmanNodeData: PostmanCollectionNodeData? = null
            try {

                logger.info("parse api...")
                val formatToPostmanInfo = formatPostmanInfo(fromProjectData)
                if (formatToPostmanInfo == null) {
                    logger.info("no api can be moved")
                    return@runAsync
                }

                rootPostmanNodeData =
                    (toPostmanNodeData as PostmanNodeData).getRootNodeData() as PostmanCollectionNodeData
                rootPostmanNodeData.status = NodeStatus.Uploading
                actionContext.runInSwingUI {
                    postmanApiTree!!.model.reload(rootPostmanNodeData.asTreeNode())
                }

                val currData = targetCollectionNodeData.currData()
                val items = currData.getEditableItem()
                items.add(formatToPostmanInfo)

                val collection = rootPostmanNodeData.collection
                val collectionId = collection["id"].toString()

                logger.info("upload api...")
                if (postmanCachedApiHelper.updateCollection(collectionId, rootPostmanNodeData.currData())) {
                    logger.info("export success")
                    rootPostmanNodeData.status = NodeStatus.Loaded

                    actionContext.runInSwingUI {
                        loadPostmanNode(targetCollectionNodeData, formatToPostmanInfo)
                        postmanApiTree!!.model.reload(targetCollectionNodeData.asTreeNode())
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

    private fun formatPostmanInfo(projectNodeData: ProjectNodeData): HashMap<String, Any?>? {
        LOG.info("format postman info:$projectNodeData")
        if (projectNodeData is ApiProjectNodeData) {
            return (projectNodeData.doc as? Request)?.let { postmanFormatter!!.request2Item(it) }
        }

        val subProjectNodeData: ArrayList<ProjectNodeData> = projectNodeData.getSubNodeData()
            ?: return null
        val subItems: ArrayList<HashMap<String, Any?>> = ArrayList()
        subProjectNodeData.stream()
            .mapNotNull { formatPostmanInfo(it) }
            .forEach { subItems.add(it) }

        if (projectNodeData is ClassProjectNodeData) {
            return postmanFormatter!!.wrapInfo(projectNodeData.cls, subItems)
        } else if (projectNodeData is ModuleProjectNodeData) {
            return postmanFormatter!!.wrapInfo(projectNodeData.module.name, subItems)
        }
        return null
    }

    //endregion handle drop--------------------------------------------------------

    //region workspace--------------------------------------------------------

    private class WorkspaceWrapper(var id: String?, var name: String?) {

        override fun toString(): String {
            return name ?: ""
        }

    }

    //endregion workspace--------------------------------------------------------

    override fun filterDoc(doc: Doc): Doc? {
        return doc.takeIf { it is Request }
    }

    private fun onCancel() {
        disposed = true
        try {
            val apiLoadFuture = this.apiLoadFuture
            if (apiLoadFuture != null && !apiLoadFuture.isDone) {
                apiLoadFuture.cancel(true)
            }
        } catch (e: Throwable) {
            logger.error(
                "error to cancel api load:" +
                        ExceptionUtils.getStackTrace(e)
            )
        }
        try {
            taskManager.terminateAll()
        } catch (e: Throwable) {
            logger.traceError("error to cancel postman load", e)

        }
        actionContext.unHold()
        actionContext.stop(false)
        dispose()
    }

}

private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(ApiDashboardDialog::class.java)