package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBoxTableRenderer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.table.JBTable
import com.itangcent.cache.withoutCache
import com.itangcent.common.utils.*
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanUrls.INTEGRATIONS_DASHBOARD
import com.itangcent.idea.plugin.api.export.postman.PostmanWorkspace
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.MarkdownFormatType
import com.itangcent.idea.plugin.settings.PostmanExportMode
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.*
import com.itangcent.idea.plugin.settings.xml.postmanCollectionsAsPairs
import com.itangcent.idea.plugin.settings.xml.setPostmanCollectionsPairs
import com.itangcent.idea.plugin.support.IdeaSupport
import com.itangcent.idea.swing.onSelect
import com.itangcent.idea.swing.onTextChange
import com.itangcent.idea.swing.selected
import com.itangcent.idea.utils.Charsets
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.idea.utils.isDoubleClick
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.utils.ResourceUtils
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel


class EasyApiSettingGUI : AbstractEasyApiSettingGUI() {

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject(optional = true)
    private var myProject: Project? = null

    private var rootPanel: JPanel? = null

    //region postman-----------------------------------------------------

    private var postmanTokenLabel: JLabel? = null

    private var postmanTokenTextField: JTextField? = null

    private lateinit var postmanWorkspaceComboBox: JComboBox<PostmanWorkspaceData>

    private var wrapCollectionCheckBox: JCheckBox? = null

    private var autoMergeScriptCheckBox: JCheckBox? = null

    private var postmanExportModeComboBox: JComboBox<String>? = null

    private var postmanJson5FormatTypeComboBox: JComboBox<String>? = null

    private var postmanWorkSpaceRefreshButton: JButton? = null

    private var postmanCollectionsTable: JBTable? = null

    private var postmanCollectionsRefreshButton: JButton? = null

    private var postmanExportCollectionPanel: JPanel? = null

    @Inject
    private lateinit var postmanCachedApiHelper: PostmanCachedApiHelper

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    private var allWorkspaces: List<PostmanWorkspaceData>? = null

    private var selectedPostmanWorkspace: PostmanWorkspaceData?
        get() {
            val postmanWorkspace = settingsInstance?.postmanWorkspace ?: return DEFAULT_WORKSPACE
            return allWorkspaces?.firstOrNull { it.id == postmanWorkspace } ?: PostmanWorkspaceData(
                postmanWorkspace,
                "unknown"
            )
        }
        set(value) {
            settingsInstance?.postmanWorkspace = value?.id
        }

    //endregion postman-----------------------------------------------------

    //region general-----------------------------------------------------

    private var pullNewestDataBeforeCheckBox: JCheckBox? = null

    private var generalPanel: JPanel? = null

    private var logLevelComboBox: JComboBox<Logger.Level>? = null

    private var methodDocEnableCheckBox: JCheckBox? = null

    private var genericEnableCheckBox: JCheckBox? = null

    private var feignEnableCheckBox: JCheckBox? = null

    private var quarkusEnableCheckBox: JCheckBox? = null

    private var globalCacheSizeLabel: JLabel? = null

    private var projectCacheSizeLabel: JLabel? = null

    private var clearGlobalCacheButton: JButton? = null

    private var clearProjectCacheButton: JButton? = null

    private var outputDemoCheckBox: JCheckBox? = null

    private var outputCharsetComboBox: JComboBox<Charsets>? = null

    private var markdownFormatTypeComboBox: JComboBox<String>? = null

    private var inferEnableCheckBox: JCheckBox? = null

    private var maxDeepTextField: JTextField? = null

    private var readGetterCheckBox: JCheckBox? = null

    private var readSetterCheckBox: JCheckBox? = null

    private var yapiTokenLabel: JLabel? = null

    private var yapiServerTextField: JTextField? = null

    private var yapiTokenTextArea: JTextArea? = null

    private var enableUrlTemplatingCheckBox: JCheckBox? = null

    private var switchNoticeCheckBox: JCheckBox? = null

    private var loginModeCheckBox: JCheckBox? = null

    private var yapiReqBodyJson5CheckBox: JCheckBox? = null

    private var yapiResBodyJson5CheckBox: JCheckBox? = null

    private var formExpandedCheckBox: JCheckBox? = null

    private var queryExpandedCheckBox: JCheckBox? = null

    private var recommendedCheckBox: JCheckBox? = null

    private var httpTimeOutTextField: JTextField? = null

    private var trustHostsTextArea: JTextArea? = null

    //endregion general-----------------------------------------------------

    private val throttleHelper = ThrottleHelper()

    override fun getRootPanel(): JPanel? {
        return rootPanel
    }

    /**
     * please call it from dispatch thread
     */
    override fun onCreate() {

        initGeneral()

        initPostman()
    }

    private fun initPostman() {

        EasyIcons.Refresh.iconOnly(this.postmanWorkSpaceRefreshButton)
        EasyIcons.Refresh.iconOnly(this.postmanCollectionsRefreshButton)
        SwingUtils.immersed(this.postmanWorkSpaceRefreshButton!!)

        postmanJson5FormatTypeComboBox!!.model =
            DefaultComboBoxModel(PostmanJson5FormatType.values().mapToTypedArray { it.name })

        postmanExportModeComboBox!!.model =
            DefaultComboBoxModel(PostmanExportMode.values().mapToTypedArray { it.name })

        this.postmanTokenTextField!!.onTextChange {
            postmanWorkSpaceRefreshButton!!.isVisible = it.notNullOrBlank()
        }

        this.postmanExportModeComboBox!!.onSelect {
            postmanExportCollectionPanel!!.isVisible = it == PostmanExportMode.UPDATE.name
        }

        postmanWorkSpaceRefreshButton!!.addActionListener {
            refreshPostmanWorkSpaces(false)
        }

        postmanCollectionsRefreshButton!!.addActionListener {
            refreshPostmanCollections(false)
        }

        postmanTokenLabel!!.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if (e.isDoubleClick()) {
                    actionContext.instance(IdeaSupport::class).openUrl(INTEGRATIONS_DASHBOARD)
                }
            }
        })
    }

    private fun initGeneral() {
        //region general-----------------------------------------------------

        recommendedCheckBox!!.toolTipText = RecommendConfigLoader.plaint()

        this.clearGlobalCacheButton!!.addActionListener {
            clearGlobalCache()
        }

        this.clearProjectCacheButton!!.addActionListener {
            clearProjectCache()
        }

        this.loginModeCheckBox!!.onSelect {
            this.yapiTokenLabel!!.text = if (it) "projectIds:" else "tokens:"
        }

        logLevelComboBox!!.model = DefaultComboBoxModel(CommonSettingsHelper.CoarseLogLevel.editableValues())

        outputCharsetComboBox!!.model = DefaultComboBoxModel(Charsets.SUPPORTED_CHARSETS)

        markdownFormatTypeComboBox!!.model =
            DefaultComboBoxModel(MarkdownFormatType.values().mapToTypedArray { it.name })

        //endregion general-----------------------------------------------------
    }

    override fun setSettings(settings: Settings) {
        super.setSettings(settings)

        this.pullNewestDataBeforeCheckBox!!.isSelected = settings.pullNewestDataBefore
        this.postmanTokenTextField!!.text = settings.postmanToken ?: ""
        this.wrapCollectionCheckBox!!.isSelected = settings.wrapCollection
        this.autoMergeScriptCheckBox!!.isSelected = settings.autoMergeScript

        this.postmanWorkspaceComboBoxModel?.selectedItem = this.selectedPostmanWorkspace
        this.logLevelComboBox!!.selectedItem = CommonSettingsHelper.CoarseLogLevel.toLevel(settings.logLevel)
        this.outputCharsetComboBox!!.selectedItem = Charsets.forName(settings.outputCharset)
        this.postmanJson5FormatTypeComboBox!!.selectedItem = settings.postmanJson5FormatType
        this.postmanExportModeComboBox!!.selectedItem = settings.postmanExportMode
        this.markdownFormatTypeComboBox!!.selectedItem = settings.markdownFormatType

        this.methodDocEnableCheckBox!!.isSelected = settings.methodDocEnable
        this.genericEnableCheckBox!!.isSelected = settings.genericEnable
        this.feignEnableCheckBox!!.isSelected = settings.feignEnable
        this.quarkusEnableCheckBox!!.isSelected = settings.quarkusEnable
        this.inferEnableCheckBox!!.isSelected = settings.inferEnable
        this.readGetterCheckBox!!.isSelected = settings.readGetter
        this.readSetterCheckBox!!.isSelected = settings.readSetter
        this.formExpandedCheckBox!!.isSelected = settings.formExpanded
        this.queryExpandedCheckBox!!.isSelected = settings.queryExpanded
        this.recommendedCheckBox!!.isSelected = settings.useRecommendConfig
        this.outputDemoCheckBox!!.isSelected = settings.outputDemo


        this.yapiServerTextField!!.text = settings.yapiServer ?: ""
        this.yapiTokenTextArea!!.text = settings.yapiTokens ?: ""
        this.enableUrlTemplatingCheckBox!!.isSelected = settings.enableUrlTemplating
        this.switchNoticeCheckBox!!.isSelected = settings.switchNotice
        this.loginModeCheckBox!!.isSelected = settings.loginMode
        this.yapiReqBodyJson5CheckBox!!.isSelected = settings.yapiReqBodyJson5
        this.yapiResBodyJson5CheckBox!!.isSelected = settings.yapiResBodyJson5

        this.trustHostsTextArea!!.text = settings.trustHosts.joinToString(separator = "\n")
        this.maxDeepTextField!!.text = settings.inferMaxDeep.toString()

        this.httpTimeOutTextField!!.text = settings.httpTimeOut.toString()

        refresh()
    }

    private fun refresh() {
        actionContext.runAsync {
            refreshCache()
            refreshPostmanWorkSpaces()
            refreshPostmanCollections()
        }
    }

    private fun refreshCache() {

        try {
            computeGlobalCacheSize()
        } catch (e: Exception) {
            //ignore
        }

        try {
            computeProjectCacheSize()
        } catch (e: Exception) {
            //ignore
        }
    }

    private var postmanWorkspaceComboBoxModel: MutableCollectionComboBoxModel<PostmanWorkspaceData>? = null

    private fun refreshPostmanWorkSpaces(userCache: Boolean = true) {
        (postmanSettingsHelper as? MemoryPostmanSettingsHelper)?.setPrivateToken(this.settingsInstance?.postmanToken)
        val allWorkspaces = postmanCachedApiHelper.getAllWorkspaces(userCache)
        val allWorkspacesData: ArrayList<PostmanWorkspaceData> = arrayListOf(DEFAULT_WORKSPACE)
        if (allWorkspaces == null) {
            if (settingsInstance?.postmanWorkspace != null) {
                allWorkspacesData.add(PostmanWorkspaceData(settingsInstance?.postmanWorkspace ?: "", "unknown"))
            }
        } else {
            allWorkspaces.forEach { allWorkspacesData.add(PostmanWorkspaceData(it)) }
        }
        this.allWorkspaces = allWorkspacesData
        if (postmanWorkspaceComboBoxModel == null) {
            postmanWorkspaceComboBoxModel =
                MutableCollectionComboBoxModel(allWorkspacesData, selectedPostmanWorkspace)
            postmanWorkspaceComboBox.model = postmanWorkspaceComboBoxModel
            postmanWorkspaceComboBox.onSelect {
                this.selectedPostmanWorkspace = it
            }
        } else {
            val selected = this.selectedPostmanWorkspace
            postmanWorkspaceComboBoxModel!!.replaceAll(allWorkspacesData.toMutableList())
            postmanWorkspaceComboBoxModel!!.selectedItem = selected
        }
    }

    @Volatile
    private var postmanCollectionTableModel: DefaultTableModel? = null
    private var tableMouseListener: MouseListener? = null
    private var postmanCollectionInit = false


    @Synchronized
    private fun refreshPostmanCollections(cache: Boolean) {
        if (cache) {
            refreshPostmanCollections()
        } else {
            postmanCachedApiHelper.withoutCache {
                refreshPostmanCollections()
            }
        }
    }

    @Synchronized
    private fun refreshPostmanCollections() {
        this.postmanExportCollectionPanel!!.isVisible =
            this.settingsInstance?.postmanExportMode == PostmanExportMode.UPDATE.name
        if (postmanCollectionTableModel != null) {
            postmanCollectionsTable!!.removeAll()
            postmanCollectionTableModel!!.columnCount = 0
            postmanCollectionTableModel!!.rowCount = 0
        }
        val allCollections = postmanCachedApiHelper.getAllCollectionPreferred() ?: emptyList()
        val collectionDataArray = allCollections.mapToTypedArray { PostmanCollectionData(it) }
        val collectionMap = HashMap<String, PostmanCollectionData>()
        collectionDataArray.forEach { collectionMap[it.id] = it }
        val columns = arrayOf("module", "collection")
        val data: ArrayList<Array<Any>> = ArrayList()
        settingsInstance?.postmanCollectionsAsPairs()?.forEach { collection ->
            data.add(
                arrayOf(
                    collection.first,
                    collectionMap[collection.second] ?: PostmanCollectionData(collection.second, "unknown")
                )
            )
        }
        val tableModel = DefaultTableModel(data.toTypedArray(), columns)
        postmanCollectionsTable!!.model = tableModel
        this.postmanCollectionTableModel = tableModel

        val columnModel = postmanCollectionsTable!!.columnModel

        val moduleColumn = columnModel.getColumn(0)
        moduleColumn.preferredWidth = 200

        val collectionColumn = columnModel.getColumn(1)
        collectionColumn.preferredWidth = 340
        collectionColumn.cellEditor = ComboBoxTableRenderer(collectionDataArray)

        if (tableMouseListener == null) {
            val tablePopMenu = JPopupMenu()

            val insertRowItem = JMenuItem("Insert Row")

            insertRowItem.addActionListener {
                postmanCollectionTableModel!!.addRow(arrayOf("", null))
            }

            tablePopMenu.add(insertRowItem)

            val removeRowItem = JMenuItem("Remove Row")

            removeRowItem.addActionListener {
                postmanCollectionsTable!!.selectedRow
                    .takeIf { it >= 0 }
                    ?.let { postmanCollectionTableModel!!.removeRow(it) }
            }

            tablePopMenu.add(removeRowItem)

            tableMouseListener = object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent?) {
                    if (e == null) return
                    if (SwingUtilities.isRightMouseButton(e)) {
                        tablePopMenu.show(e.component!!, e.x, e.y)
                    }
                }
            }
            postmanCollectionsTable!!.addMouseListener(tableMouseListener)
        }
        postmanCollectionInit = true
    }

    private fun computeProjectCacheSize() {
        val currentProject = myProject ?: return
        val projectBasePath = currentProject.basePath
        val cachePath = "$projectBasePath${File.separator}.idea${File.separator}.cache${File.separator}$basePath"
        val cacheSize = computeFolderSize(cachePath)
        actionContext.runInSwingUI {
            this.projectCacheSizeLabel!!.text = StringUtil.formatFileSize(cacheSize)
        }
    }

    private fun clearProjectCache() {
        val currentProject = myProject ?: return
        val projectBasePath = currentProject.basePath
        val cachePath = "$projectBasePath${File.separator}.idea${File.separator}.cache${File.separator}$basePath"
        deleteFolder(cachePath)
        computeProjectCacheSize()
    }

    private fun computeGlobalCacheSize() {
        val cachePath = "${globalBasePath()}${File.separator}$basePath"
        val cacheSize = computeFolderSize(cachePath)
        actionContext.runInSwingUI {
            this.globalCacheSizeLabel!!.text = StringUtil.formatFileSize(cacheSize)
        }
    }

    private fun clearGlobalCache() {
        val cachePath = "${globalBasePath()}${File.separator}$basePath"
        deleteFolder(cachePath)
        computeGlobalCacheSize()
    }

    private fun computeFolderSize(path: String): Long {
        val file = File(path)
        if (file.exists()) {
            return FileSizeUtils.sizeOf(file)
        }
        return 0
    }

    private fun deleteFolder(path: String) {
        return FileUtils.deleteDirectory(File(path))
    }

    private fun readPostmanCollections(settings: Settings) {
        if (!postmanCollectionInit) {
            return
        }
        val collectionModel = postmanCollectionTableModel ?: return
        val pairs: ArrayList<Pair<String, String>> = ArrayList()
        for (row in 0 until collectionModel.rowCount) {
            val module = collectionModel.getValueAt(row, 0) as? String ?: continue
            val collectionId = collectionModel.getValueAt(row, 1) as? PostmanCollectionData ?: continue
            pairs.add(module to collectionId.id)
        }
        settings.setPostmanCollectionsPairs(pairs)
    }

    private fun globalBasePath(): String {

        var home = SystemUtils.userHome
        if (home.endsWith(File.separator)) {
            home = home.substring(0, home.length - 1)
        }
        return home
    }

    private class PostmanWorkspaceData {

        var id: String?
        var name: String

        constructor(id: String?, name: String) {
            this.id = id
            this.name = name
        }

        constructor(postmanWorkspace: PostmanWorkspace) {
            this.id = postmanWorkspace.id ?: ""
            this.name = postmanWorkspace.nameWithType() ?: ""
        }

        override fun toString(): String {
            return name
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as PostmanWorkspaceData

            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }

    }

    private class PostmanCollectionData {

        var id: String
        var name: String

        constructor(id: String, name: String) {
            this.id = id
            this.name = name
        }

        constructor(collectionData: HashMap<String, Any?>) {
            this.id = collectionData.getAs("id") ?: "unknown"
            this.name = collectionData.getAs("name") ?: "unknown"
        }

        override fun toString(): String {
            return name
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            if (id != (other as? PostmanWorkspaceData)?.id) return false
            return true
        }

        override fun hashCode(): Int {
            return id.hashCode()
        }
    }

    override fun readSettings(settings: Settings) {
        settings.postmanToken = postmanTokenTextField!!.text
        settings.wrapCollection = wrapCollectionCheckBox!!.isSelected
        settings.autoMergeScript = autoMergeScriptCheckBox!!.isSelected
        postmanJson5FormatTypeComboBox!!.selected()?.let {
            settings.postmanJson5FormatType = it
        }
        settings.pullNewestDataBefore = pullNewestDataBeforeCheckBox!!.isSelected
        settings.methodDocEnable = methodDocEnableCheckBox!!.isSelected
        settings.genericEnable = genericEnableCheckBox!!.isSelected
        settings.feignEnable = feignEnableCheckBox!!.isSelected
        settings.quarkusEnable = quarkusEnableCheckBox!!.isSelected
        settings.queryExpanded = queryExpandedCheckBox!!.isSelected
        settings.formExpanded = formExpandedCheckBox!!.isSelected
        settings.readGetter = readGetterCheckBox!!.isSelected
        settings.readSetter = readSetterCheckBox!!.isSelected
        settings.inferEnable = inferEnableCheckBox!!.isSelected
        settings.inferMaxDeep = maxDeepTextField!!.text.toIntOrNull() ?: Settings.DEFAULT_INFER_MAX_DEEP
        settings.yapiServer = yapiServerTextField!!.text
        settings.yapiTokens = yapiTokenTextArea!!.text
        settings.enableUrlTemplating = enableUrlTemplatingCheckBox!!.isSelected
        settings.switchNotice = switchNoticeCheckBox!!.isSelected
        settings.loginMode = loginModeCheckBox!!.isSelected
        settings.yapiReqBodyJson5 = yapiReqBodyJson5CheckBox!!.isSelected
        settings.yapiResBodyJson5 = yapiResBodyJson5CheckBox!!.isSelected
        settings.httpTimeOut =
            httpTimeOutTextField!!.text.toIntOrNull() ?: ConfigurableHttpClientProvider.defaultHttpTimeOut
        settings.useRecommendConfig = recommendedCheckBox!!.isSelected
        settings.logLevel =
            (logLevelComboBox!!.selected() ?: CommonSettingsHelper.CoarseLogLevel.LOW).getLevel()
        settings.outputDemo = outputDemoCheckBox!!.isSelected
        settings.outputCharset = (outputCharsetComboBox!!.selectedItem as? Charsets ?: Charsets.UTF_8).displayName()
        settings.markdownFormatType =
            markdownFormatTypeComboBox!!.selected() ?: MarkdownFormatType.SIMPLE.name
        settings.trustHosts = trustHostsTextArea!!.text?.lines()?.toTypedArray() ?: emptyArray()
        settings.postmanWorkspace = settingsInstance?.postmanWorkspace
        settings.postmanExportMode = postmanExportModeComboBox!!.selected() ?: PostmanExportMode.COPY.name
        settings.yapiTokens = this.yapiTokenTextArea!!.text

        readPostmanCollections(settings)
    }

    companion object {
        const val basePath = ".easy_api"

        const val setting_path = "easy.api.setting.path"

        private const val built_in_config_name = ".default.built.in.easy.api.config"

        val DEFAULT_BUILT_IN_CONFIG = ResourceUtils.readResource(built_in_config_name)

        private var DEFAULT_WORKSPACE = PostmanWorkspaceData(null, "")
    }
}

private val LOG = org.apache.log4j.Logger.getLogger(YapiDashboardDialog::class.java)