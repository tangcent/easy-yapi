package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.openapi.ui.Messages
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.logger.traceWarn
import com.itangcent.common.model.Doc
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.plugin.api.export.yapi.YapiApiDashBoardExporter
import com.itangcent.idea.plugin.api.export.yapi.YapiApiHelper
import com.itangcent.idea.plugin.api.export.yapi.YapiFormatter
import com.itangcent.idea.plugin.settings.helper.YapiSettingsHelper
import com.itangcent.idea.plugin.support.IdeaSupport
import com.itangcent.idea.swing.EasyApiTreeCellRenderer
import com.itangcent.idea.swing.IconCustomized
import com.itangcent.idea.swing.ToolTipAble
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.idea.utils.isDoubleClick
import com.itangcent.idea.utils.reload
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.*
import com.itangcent.intellij.extend.rx.from
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath


class YapiDashboardDialog : AbstractApiDashboardDialog() {
    override var contentPane: JPanel? = null
    override var projectApiTree: JTree? = null
    private var yapiApiTree: JTree? = null
    override var projectApiPanel: JPanel? = null
    private var yapiPanel: JPanel? = null
    override var projectApiModeButton: JButton? = null
    override var projectCollapseButton: JButton? = null

    private var yapiNewProjectButton: JButton? = null
    private var yapiSyncButton: JButton? = null
    private var yapiCollapseButton: JButton? = null

    private var yapiPopMenu: JPopupMenu? = null

    private var projectMode: ProjectMode = ProjectMode.Legible

    @Inject
    private lateinit var yapiApiHelper: YapiApiHelper

    @Inject
    private val yapiApiDashBoardExporter: YapiApiDashBoardExporter? = null

    @Inject
    protected lateinit var yapiSettingsHelper: YapiSettingsHelper

    init {
        setContentPane(contentPane)
        isModal = true
    }

    override fun init() {
        super.init()

        EasyIcons.CollapseAll.iconOnly(this.projectCollapseButton)
        EasyIcons.CollapseAll.iconOnly(this.yapiCollapseButton)
        EasyIcons.Add.iconOnly(this.yapiNewProjectButton)
        EasyIcons.Refresh.iconOnly(this.yapiSyncButton)

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

        } catch (_: Exception) {
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
                logger.traceError("sync failed", e)
            }
        }

        val curlItem = JMenuItem("Copy Curl")
        curlItem.addActionListener {
            selectedYapiNode()?.let { copyCurl(it) }
        }

        yapiPopMenu!!.add(addItem)
        yapiPopMenu!!.add(unloadItem)
        yapiPopMenu!!.add(syncItem)
        yapiPopMenu!!.add(curlItem)

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

            override fun mouseClicked(e: MouseEvent?) {
                if (e.isDoubleClick()) {
                    goToYapi()
                    e?.consume()
                }
            }
        })

        yapiApiTree!!.model = null

        autoComputer.bindEnable(this.yapiCollapseButton!!)
            .from(this::yapiAvailable)
        autoComputer.bindEnable(this.yapiSyncButton!!)
            .from(this::yapiAvailable)
        autoComputer.bindEnable(this.yapiNewProjectButton!!)
            .from(this::yapiAvailable)


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

                    val projectNodeData =
                        (transferData as WrapData).wrapHash?.let { safeHashHelper.getBean(it) } as? ProjectNodeData
                            ?: return

                    handleDropEvent(projectNodeData, yapiNodeData)

                } catch (e: java.lang.Exception) {
                    logger.traceWarn("drop failed", e)
                } finally {
                    dtde.dropComplete(true)
                }
            }
        })

        this.yapiCollapseButton!!.addActionListener {
            try {
                SwingUtils.expandOrCollapseNode(this.yapiApiTree!!, false)
            } catch (e: Exception) {
                logger.error("try collapse yapi apis failed!")
            }
        }

        this.yapiSyncButton!!.addActionListener {
            ((this.yapiApiTree!!.model as DefaultTreeModel).root as DefaultMutableTreeNode).removeAllChildren()
            loadYapiInfo()
        }

        this.yapiNewProjectButton!!.addActionListener {
            importNewYapiProject()
        }

        initYapiInfo()
    }

    //region yapi module-----------------------------------------------------

    private var yapiAvailable: Boolean = true

    private fun initYapiInfo() {
        actionContext.runWithContext {
            if (yapiSettingsHelper.hasServer()) {
                loadYapiInfo()
            } else {
                autoComputer.value(this::yapiAvailable, false)
                actionContext.runAsync {
                    if (yapiSettingsHelper.getServer(false).notNullOrBlank()) {
                        autoComputer.value(this::yapiAvailable, true)
                        loadYapiInfo()
                    }
                }
            }
        }
    }

    private fun loadYapiInfo() {
        actionContext.runInNormalThread {
            if (!yapiSettingsHelper.hasServer()) {
                actionContext.runInSwingUI {
                    Messages.showErrorDialog(
                        this,
                        "Load yapi info failed, no server be found", "Error"
                    )
                }
                return@runInNormalThread
            }

            actionContext.runInSwingUI {
                //            yapiApiTree!!.dragEnabled = true
                val treeNode = DefaultMutableTreeNode()
                val rootTreeModel = DefaultTreeModel(treeNode, true)

                actionContext.runAsync {
                    var projectNodes: ArrayList<YapiProjectNodeData>? = null
                    try {
                        val yapiTokens = yapiSettingsHelper.readTokens()

                        if (yapiTokens.isEmpty()) {
                            actionContext.runInSwingUI {
                                Messages.showErrorDialog(
                                    this,
                                    "No token be found", "Error"
                                )
                            }
                            return@runAsync
                        }

                        projectNodes = ArrayList()

                        yapiTokens.values.asSequence().distinct().forEach { token ->

                            logger.info("load token:$token")
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

                            val projectNode = YapiProjectNodeData(token, projectInfo)
                            treeNode.add(projectNode.asTreeNode())
                            projectNodes.add(projectNode)
                            actionContext.runInSwingUI {
                                rootTreeModel.reload(projectNode.asTreeNode())
                            }
                        }
                    } catch (e: Exception) {
                        logger.traceError("error to load yapi info", e)
                    }

                    actionContext.runInSwingUI {
                        yapiApiTree!!.model = rootTreeModel

                        actionContext.runAsync {
                            try {
                                if (projectNodes != null) {
                                    val boundary = actionContext.createBoundary()
                                    try {
                                        for (projectNode in projectNodes) {
                                            Thread.sleep(200)
                                            loadYapiProject(projectNode)
                                            boundary.waitComplete(false)
                                        }
                                    } finally {
                                        boundary.remove()
                                    }
                                }
                            } catch (_: InterruptedException) {
                            }
                        }
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadYapiProject(projectNode: YapiProjectNodeData) {
        val projectId = projectNode.getProjectId()
        val yapiApiTreeModel = yapiApiTree!!.model as DefaultTreeModel
        if (projectId == null) {
            actionContext.runInSwingUI {
                projectNode.removeFromParent()
                yapiApiTreeModel.reload(projectNode.asTreeNode())
            }
            return
        }

        actionContext.runAsync {
            projectNode.status = NodeStatus.Loading
            actionContext.withBoundary {
                val carts = yapiApiHelper.findCarts(projectId.toString(), projectNode.getProjectToken())
                if (carts.isNullOrEmpty()) {
                    return@withBoundary
                }
                for (cart in carts) {
                    val yapiCartNode = YapiCartNodeData(cart as HashMap<String, Any?>)
                    projectNode.addSubNodeData(yapiCartNode)
                }
                projectNode.getSubNodeData()?.forEach {
                    (it as? YapiCartNodeData)?.let { cart -> loadYapiCart(cart) }
                }
            }
            projectNode.status = NodeStatus.Loaded
            actionContext.runInSwingUI {
                yapiApiTreeModel.reload(projectNode.asTreeNode())
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadYapiCart(yapiCartNodeData: YapiCartNodeData) {

        actionContext.runInSwingUI {
            val cartInfo = yapiCartNodeData.info

            val apis = yapiApiHelper.findApis(
                yapiCartNodeData.getProjectToken(),
                cartInfo["_id"].toString()
            )
            if (apis.isNullOrEmpty()) return@runInSwingUI
            for (api in apis) {
                loadYapiApi(yapiCartNodeData, api as HashMap<String, Any?>)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadYapiApi(parentNode: YapiCartNodeData, item: HashMap<String, Any?>) {
        if (item.isNullOrEmpty()) return
        actionContext.runInSwingUI {
            val apiNodeData = YapiApiNodeData(parentNode, item)
            parentNode.addSubNodeData(apiNodeData)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun importNewYapiProject() {
        actionContext.runAsync {
            val projectToken = this.yapiSettingsHelper.inputNewToken()

            if (projectToken.isNullOrBlank()) return@runAsync

            val projectId = yapiApiHelper.getProjectIdByToken(projectToken)

            if (projectId.isNullOrEmpty()) {
                return@runAsync
            }

            val projectInfo = yapiApiHelper.getProjectInfo(projectToken, projectId)
                .sub("data")
                ?.asMap()

            if (projectInfo.isNullOrEmpty()) {
                logger.error("invalid token:$projectToken")
                return@runAsync
            }

            actionContext.runInSwingUI {
                val yapiProjectName = projectInfo["name"].toString()
                val moduleName = Messages.showInputDialog(
                    this,
                    "Input Module Name Of Project",
                    "Module Name",
                    Messages.getInformationIcon(),
                    yapiProjectName,
                    null
                )

                @Suppress("LABEL_NAME_CLASH")
                if (moduleName.isNullOrBlank()) return@runInSwingUI

                actionContext.runAsync {

                    yapiSettingsHelper.setToken(moduleName, projectToken)
                    actionContext.runInSwingUI {
                        val projectTreeNode = YapiProjectNodeData(projectToken, projectInfo)
                        var model = yapiApiTree!!.model
                        if (model == null) {
                            val treeNode = DefaultMutableTreeNode()
                            model = DefaultTreeModel(treeNode, true)
                            yapiApiTree!!.model = model
                        }

                        val yapiTreeModel = model as DefaultTreeModel

                        (yapiTreeModel.root as DefaultMutableTreeNode).add(projectTreeNode.asTreeNode())
                        yapiTreeModel.reload()

                        loadYapiProject(projectTreeNode)
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

            actionContext.runInSwingUI {
                val cartName = Messages.showInputDialog(
                    this,
                    "Input Cart Name",
                    "Cart Name",
                    Messages.getInformationIcon()
                )
                if (cartName.isNullOrBlank()) return@runInSwingUI

                yapiApiHelper.addCart(yapiNodeData.getProjectToken()!!, cartName, "")

                syncYapiNode(yapiNodeData.getRootNodeData())
            }
        }
    }

    private fun unloadYapiProject() {
        val lastSelectedPathComponent = yapiApiTree!!.lastSelectedPathComponent as (DefaultMutableTreeNode?)

        if (lastSelectedPathComponent != null) {
            val yapiNodeData = lastSelectedPathComponent.userObject as YapiNodeData

            yapiSettingsHelper.removeToken(yapiNodeData.getProjectToken()!!)

            val treeModel = yapiApiTree!!.model as DefaultTreeModel
            (treeModel.root as DefaultMutableTreeNode)
                .remove(yapiNodeData.getRootNodeData().asTreeNode())
            treeModel.reload()
        }

    }

    private fun syncYapiProject() {
        val yapiNodeData = selectedYapiNode() ?: return
        logger.info("reload:[$yapiNodeData]")
        syncYapiNode(yapiNodeData)
    }

    private fun syncYapiNode(yapiNodeData: YapiNodeData) {
        actionContext.runInSwingUI {
            when (yapiNodeData) {
                is YapiApiNodeData -> {
                    syncYapiNode(yapiNodeData.getParentNodeData())
                }

                is YapiProjectNodeData -> {
                    //clear
                    yapiNodeData.removeAllSub()
                    //reload
                    loadYapiProject(yapiNodeData)
                }

                is YapiCartNodeData -> {
                    //clear
                    yapiNodeData.removeAllSub()
                    loadYapiCart(yapiNodeData)
                    yapiApiTree!!.model.reload(yapiNodeData.getRootNodeData().asTreeNode())
                }
            }
        }
    }

    private fun goToYapi() {
        val yapiNodeData = selectedYapiNode() ?: return
        LOG.trace("go to:[$yapiNodeData]")
        yapiNodeData.getUrl(this)?.let {
            actionContext.instance(IdeaSupport::class).openUrl(it)
        }
    }

    private fun selectedYapiNode(): YapiNodeData? {
        return (yapiApiTree!!.lastSelectedPathComponent as? DefaultMutableTreeNode)
            ?.userObject as? YapiNodeData
    }

    //endregion yapi pop action---------------------------------------------------------

    //region yapi Node Data--------------------------------------------------

    abstract class YapiNodeData : DocContainer, TreeNodeData<YapiNodeData>() {
        abstract fun data(): Map<String, Any?>

        abstract fun getProjectId(): String?

        abstract fun getProjectToken(): String?

        abstract fun getUrl(yapiDashboardDialog: YapiDashboardDialog): String?

        override fun docs(handle: (Doc) -> Unit) {
            this.getSubNodeData()?.forEach { it.docs(handle) }
        }
    }

    class YapiProjectNodeData(
        private var projectToken: String,
        var projectInfo: Map<String, Any?>,
    ) : YapiNodeData(), IconCustomized {
        override fun icon(): Icon? {
            return when (status) {
                NodeStatus.Loading -> EasyIcons.Refresh
                NodeStatus.Uploading -> EasyIcons.UpFolder
                else -> null
            } ?: EasyIcons.ModuleGroup
        }

        override fun data(): Map<String, Any?> {
            return projectInfo
        }

        override fun getParentNodeData(): YapiNodeData? {
            return null
        }

        var status = NodeStatus.Unload

        override fun getProjectId(): String? {
            return projectInfo["_id"]?.toString()
        }

        override fun getProjectToken(): String {
            return projectToken
        }

        override fun getUrl(yapiDashboardDialog: YapiDashboardDialog): String? {
            if (!yapiDashboardDialog.yapiSettingsHelper.hasServer()) {
                return null
            }
            val projectId = getProjectId() ?: return null
            return "${yapiDashboardDialog.yapiSettingsHelper.getServer(true)}/project/${projectId}/interface/api"
        }

        override fun toString(): String {
            return status.desc + projectInfo.getOrDefault("name", "unknown")
        }
    }

    class YapiCartNodeData(
        var info: HashMap<String, Any?>,
    ) : YapiNodeData(), IconCustomized, ToolTipAble {

        override fun icon(): Icon? {
            return EasyIcons.Module
        }

        override fun data(): HashMap<String, Any?> {
            return info
        }

        override fun getParentNodeData(): YapiProjectNodeData {
            return super.getParentNodeData() as YapiProjectNodeData
        }

        override fun getProjectId(): String? {
            return getParentNodeData().getProjectId()
        }

        override fun getProjectToken(): String {
            return getParentNodeData().getProjectToken()
        }

        override fun getUrl(yapiDashboardDialog: YapiDashboardDialog): String? {
            if (!yapiDashboardDialog.yapiSettingsHelper.hasServer()) {
                return null
            }
            val projectId = getProjectId() ?: return null
            val cartId = info["_id"]?.toString() ?: return null
            return "${yapiDashboardDialog.yapiSettingsHelper.getServer(true)}/project/${projectId}/interface/api/cat_${cartId}"
        }

        override fun toString(): String {
            return info.getOrDefault("name", "unknown").toString()
        }

        override fun toolTip(): String? {
            return info["desc"]?.toString()
        }
    }

    class YapiApiNodeData(private var parentNode: YapiCartNodeData, var info: HashMap<String, Any?>) : YapiNodeData(),
        IconCustomized, ToolTipAble {

        private val yapiFormatter: YapiFormatter by lazy {
            ActionContext.getContext()!!.instance(YapiFormatter::class)
        }

        override fun icon(): Icon? {
            return EasyIcons.Link
        }

        override fun data(): HashMap<String, Any?> {
            return info
        }

        override fun getParentNodeData(): YapiCartNodeData {
            return parentNode
        }

        override fun getProjectId(): String? {
            return parentNode.getProjectId()
        }

        override fun getProjectToken(): String {
            return parentNode.getProjectToken()
        }

        override fun docs(handle: (Doc) -> Unit) {
            handle(yapiFormatter.item2Request(this.info))
        }

        override fun asTreeNode(): DefaultMutableTreeNode {
            return super.asTreeNode().also { it.allowsChildren = false }
        }

        override fun toString(): String {
            return info.getOrDefault("title", "unknown").toString()
        }

        override fun toolTip(): String {
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

        override fun getUrl(yapiDashboardDialog: YapiDashboardDialog): String? {
            if (!yapiDashboardDialog.yapiSettingsHelper.hasServer()) {
                return null
            }
            val projectId = getProjectId() ?: return null
            val apiId = info["_id"]?.toString() ?: return null
            return "${yapiDashboardDialog.yapiSettingsHelper.getServer(true)}/project/${projectId}/interface/api/${apiId}"
        }

    }

    //endregion yapi Node Data--------------------------------------------------

    //region handle drop--------------------------------------------------------

    @Suppress("UNCHECKED_CAST", "LABEL_NAME_CLASH")
    fun handleDropEvent(fromProjectData: ProjectNodeData, toYapiNodeData: Any) {

        //    \to  | api    |cart               |project
        // from\   |        |                   |
        // api     |see ->  |✔️                 |new cart
        // class   |see ->  |new cart/the cart  |new cart
        // module  |see ->  |new cart/the cart  |new cart

        //targetNodeData should be YapiCartNodeData or YapiProjectNodeData
        val targetNodeData: YapiNodeData = when (toYapiNodeData) {
            is YapiApiNodeData -> toYapiNodeData.getParentNodeData()
            else -> toYapiNodeData as YapiNodeData
        }

        logger.info("export [$fromProjectData] to $targetNodeData")

        actionContext.runAsync {
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
                        actionContext.runInSwingUI {
                            val yesNoCancel = Messages.showYesNoCancelDialog(
                                project!!, "Add as new cart?",
                                "Export", "New", "Not", "Cancel", Messages.getInformationIcon()
                            )
                            actionContext.runAsync {
                                when (yesNoCancel) {
                                    Messages.CANCEL -> return@runAsync
                                    Messages.OK -> {
                                        export(fromProjectData, targetNodeData, null)
                                        Thread.sleep(200)
                                        syncYapiNode(targetNodeData.getRootNodeData())
                                    }

                                    Messages.NO -> {
                                        export(fromProjectData, targetNodeData, cartId)
                                        Thread.sleep(200)
                                        syncYapiNode(targetNodeData.getRootNodeData())
                                    }
                                }
                            }
                        }
                    }
                } else {
                    export(fromProjectData, targetNodeData, null)
                    Thread.sleep(200)
                    syncYapiNode(targetNodeData.getRootNodeData())
                }
            } catch (e: Exception) {
                logger.traceError("export failed", e)
            }
        }
    }

    private fun export(
        fromProjectData: ProjectNodeData, targetNodeData: YapiNodeData,
        cartId: String?,
    ) {

        val privateToken = targetNodeData.getProjectToken()
        if (privateToken == null) {
            logger.error("target token missing!Please try sync")
            return
        }

        val docHandle: (Doc) -> Unit = if (cartId.isNullOrBlank()) {
            { doc -> yapiApiDashBoardExporter!!.exportDoc(doc, privateToken) }
        } else {
            { doc -> yapiApiDashBoardExporter!!.exportDoc(doc, privateToken, cartId) }
        }

        fromProjectData.docs(docHandle)
        logger.info("exported success")
    }

    //endregion handle drop--------------------------------------------------------
    companion object : Log()
}