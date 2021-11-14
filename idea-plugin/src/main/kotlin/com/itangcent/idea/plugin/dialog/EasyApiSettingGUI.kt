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
import com.itangcent.idea.utils.Charsets
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.idea.utils.isDoubleClick
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.extend.rx.mutual
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

    //region import&export-----------------------------------------------------
    private var importButton: JButton? = null

    private var exportButton: JButton? = null
    //endregion import&export-----------------------------------------------------

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

    private var globalCacheSize: String = "0M"

    private var clearGlobalCacheButton: JButton? = null

    private var projectCacheSize: String = "0M"

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

        autoComputer.bind<String?>(this, "settingsInstance.postmanJson5FormatType")
            .with(this.postmanJson5FormatTypeComboBox!!)
            .filter { throttleHelper.acquire("settingsInstance.postmanJson5FormatType", 300) }
            .eval { (it ?: PostmanJson5FormatType.EXAMPLE_ONLY.name) }

        postmanExportModeComboBox!!.model =
            DefaultComboBoxModel(PostmanExportMode.values().mapToTypedArray { it.name })

        autoComputer.bind<String?>(this, "settingsInstance.postmanExportMode")
            .with(this.postmanExportModeComboBox!!)
            .filter { throttleHelper.acquire("settingsInstance.postmanExportMode", 300) }
            .eval { (it ?: PostmanExportMode.COPY.name) }

        autoComputer.bindVisible(postmanWorkSpaceRefreshButton!!)
            .with(this.postmanTokenTextField!!)
            .eval { it.notNullOrBlank() }

        autoComputer.bindVisible(postmanExportCollectionPanel!!)
            .with<String?>(this, "settingsInstance.postmanExportMode")
            .eval {
                it == PostmanExportMode.UPDATE.name
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

        autoComputer.bind(pullNewestDataBeforeCheckBox!!)
            .mutual(this, "settingsInstance.pullNewestDataBefore")

        autoComputer.bind(postmanTokenTextField!!)
            .mutual(this, "settingsInstance.postmanToken")

        autoComputer.bind(wrapCollectionCheckBox!!)
            .mutual(this, "settingsInstance.wrapCollection")

        autoComputer.bind(autoMergeScriptCheckBox!!)
            .mutual(this, "settingsInstance.autoMergeScript")

        autoComputer.bind(this.globalCacheSizeLabel!!)
            .with(this::globalCacheSize)
            .eval { it }

        this.clearGlobalCacheButton!!.addActionListener {
            clearGlobalCache()
        }

        autoComputer.bind(this.projectCacheSizeLabel!!)
            .with(this::projectCacheSize)
            .eval { it }

        this.clearProjectCacheButton!!.addActionListener {
            clearProjectCache()
        }

        autoComputer.bind(methodDocEnableCheckBox!!)
            .mutual(this, "settingsInstance.methodDocEnable")

        autoComputer.bind(genericEnableCheckBox!!)
            .mutual(this, "settingsInstance.genericEnable")

        autoComputer.bind(feignEnableCheckBox!!)
            .mutual(this, "settingsInstance.feignEnable")

        autoComputer.bind(quarkusEnableCheckBox!!)
            .mutual(this, "settingsInstance.quarkusEnable")

        autoComputer.bind(inferEnableCheckBox!!)
            .mutual(this, "settingsInstance.inferEnable")

        autoComputer.bind(readGetterCheckBox!!)
            .mutual(this, "settingsInstance.readGetter")

        autoComputer.bind(readSetterCheckBox!!)
            .mutual(this, "settingsInstance.readSetter")

        autoComputer.bind(formExpandedCheckBox!!)
            .mutual(this, "settingsInstance.formExpanded")

        autoComputer.bind(queryExpandedCheckBox!!)
            .mutual(this, "settingsInstance.queryExpanded")

        autoComputer.bind(recommendedCheckBox!!)
            .mutual(this, "settingsInstance.useRecommendConfig")

        autoComputer.bind(outputDemoCheckBox!!)
            .mutual(this, "settingsInstance.outputDemo")

        autoComputer.bind(this.maxDeepTextField!!)
            .with<Int?>(this, "settingsInstance.inferMaxDeep")
            .eval { (it ?: Settings.DEFAULT_INFER_MAX_DEEP).toString() }

        autoComputer.bind<Int>(this, "settingsInstance.inferMaxDeep")
            .with(this.maxDeepTextField!!)
            .eval {
                try {
                    it?.toInt() ?: Settings.DEFAULT_INFER_MAX_DEEP
                } catch (e: Exception) {
                    Settings.DEFAULT_INFER_MAX_DEEP
                }
            }

        autoComputer.bind(yapiServerTextField!!)
            .mutual(this, "settingsInstance.yapiServer")

        autoComputer.bind(yapiTokenTextArea!!)
            .mutual(this, "settingsInstance.yapiTokens")

        autoComputer.bind(enableUrlTemplatingCheckBox!!)
            .mutual(this, "settingsInstance.enableUrlTemplating")

        autoComputer.bind(switchNoticeCheckBox!!)
            .mutual(this, "settingsInstance.switchNotice")

        autoComputer.bind(loginModeCheckBox!!)
            .mutual(this, "settingsInstance.loginMode")

        autoComputer.bind(yapiReqBodyJson5CheckBox!!)
            .mutual(this, "settingsInstance.yapiReqBodyJson5")

        autoComputer.bind(yapiResBodyJson5CheckBox!!)
            .mutual(this, "settingsInstance.yapiResBodyJson5")

        autoComputer.bind(yapiTokenLabel!!)
            .with<Boolean?>(this, "settingsInstance.loginMode")
            .eval {
                if (it == true) {
                    "projectIds:"
                } else {
                    "tokens:"
                }
            }

        autoComputer.bind(this.httpTimeOutTextField!!)
            .with<Int?>(this, "settingsInstance.httpTimeOut")
            .eval { (it ?: ConfigurableHttpClientProvider.defaultHttpTimeOut).toString() }

        autoComputer.bind<Int>(this, "settingsInstance.httpTimeOut")
            .with(this.httpTimeOutTextField!!)
            .eval {
                try {
                    it?.toInt() ?: ConfigurableHttpClientProvider.defaultHttpTimeOut
                } catch (e: Exception) {
                    ConfigurableHttpClientProvider.defaultHttpTimeOut
                }
            }

        logLevelComboBox!!.model = DefaultComboBoxModel(CommonSettingsHelper.CoarseLogLevel.editableValues())

        autoComputer.bind<Int?>(this, "settingsInstance.logLevel")
            .with(this.logLevelComboBox!!)
            .filter { throttleHelper.acquire("settingsInstance.logLevel", 300) }
            .eval { (it ?: CommonSettingsHelper.CoarseLogLevel.LOW).getLevel() }

        outputCharsetComboBox!!.model = DefaultComboBoxModel(Charsets.SUPPORTED_CHARSETS)

        markdownFormatTypeComboBox!!.model =
            DefaultComboBoxModel(MarkdownFormatType.values().mapToTypedArray { it.name })

        autoComputer.bind<String?>(this, "settingsInstance.outputCharset")
            .with(this.outputCharsetComboBox!!)
            .filter { throttleHelper.acquire("settingsInstance.outputCharset", 300) }
            .eval { (it ?: Charsets.UTF_8).displayName() }

        autoComputer.bind<String?>(this, "settingsInstance.markdownFormatType")
            .with(this.markdownFormatTypeComboBox!!)
            .filter { throttleHelper.acquire("settingsInstance.markdownFormatType", 300) }
            .eval { (it ?: MarkdownFormatType.SIMPLE.name) }

        autoComputer.bind<Array<String>>(this, "settingsInstance.trustHosts")
            .with(trustHostsTextArea!!)
            .eval { trustHosts -> trustHosts?.lines()?.toTypedArray() ?: emptyArray() }

        autoComputer.bind(trustHostsTextArea!!)
            .with<Array<String>>(this, "settingsInstance.trustHosts")
            .eval { trustHosts -> trustHosts.joinToString(separator = "\n") }

        //endregion general-----------------------------------------------------
    }

    override fun setSettings(settings: Settings) {
        val snapshot = settings.copy()
        super.setSettings(settings)

        throttleHelper.refresh("throttleHelper")

        this.postmanWorkspaceComboBoxModel?.selectedItem = this.selectedPostmanWorkspace
        this.logLevelComboBox!!.selectedItem = CommonSettingsHelper.CoarseLogLevel.toLevel(settings.logLevel)
        this.outputCharsetComboBox!!.selectedItem = Charsets.forName(settings.outputCharset)
        this.postmanJson5FormatTypeComboBox!!.selectedItem = settings.postmanJson5FormatType
        this.postmanExportModeComboBox!!.selectedItem = settings.postmanExportMode
        this.markdownFormatTypeComboBox!!.selectedItem = settings.markdownFormatType

        refresh(snapshot)
    }

    private fun refresh(settings: Settings) {
        actionContext.runAsync {
            //fix
            this.settingsInstance?.postmanExportMode = settings.postmanExportMode
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
                MutableCollectionComboBoxModel(allWorkspacesData ?: emptyList(), selectedPostmanWorkspace)
            postmanWorkspaceComboBox.model = postmanWorkspaceComboBoxModel
            autoComputer.bind(postmanWorkspaceComboBox)
                .mutual(this::selectedPostmanWorkspace)
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
        val readableCacheSize = StringUtil.formatFileSize(cacheSize)
        autoComputer.value(this::projectCacheSize, readableCacheSize)
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
        val readableCacheSize = StringUtil.formatFileSize(cacheSize)
        autoComputer.value(this::globalCacheSize, readableCacheSize)
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

    override fun getSettings(): Settings {
        return super.getSettings().also {
            readPostmanCollections(it)
        }
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

    override fun readSettings(settings: Settings, from: Settings) {
        settings.postmanToken = from.postmanToken
        settings.wrapCollection = from.wrapCollection
        settings.autoMergeScript = from.autoMergeScript
        settings.postmanJson5FormatType = from.postmanJson5FormatType
        settings.pullNewestDataBefore = from.pullNewestDataBefore
        settings.methodDocEnable = from.methodDocEnable
        settings.genericEnable = from.genericEnable
        settings.feignEnable = from.feignEnable
        settings.quarkusEnable = from.quarkusEnable
        settings.queryExpanded = from.queryExpanded
        settings.formExpanded = from.formExpanded
        settings.readGetter = from.readGetter
        settings.readSetter = from.readSetter
        settings.inferEnable = from.inferEnable
        settings.inferMaxDeep = from.inferMaxDeep
        settings.yapiServer = from.yapiServer
        settings.yapiTokens = from.yapiTokens
        settings.enableUrlTemplating = from.enableUrlTemplating
        settings.switchNotice = from.switchNotice
        settings.loginMode = from.loginMode
        settings.yapiReqBodyJson5 = from.yapiReqBodyJson5
        settings.yapiResBodyJson5 = from.yapiResBodyJson5
        settings.httpTimeOut = from.httpTimeOut
        settings.useRecommendConfig = from.useRecommendConfig
        settings.logLevel = from.logLevel
        settings.outputDemo = from.outputDemo
        settings.outputCharset = from.outputCharset
        settings.markdownFormatType = from.markdownFormatType
        settings.trustHosts = from.trustHosts
        settings.postmanWorkspace = from.postmanWorkspace
        settings.postmanExportMode = from.postmanExportMode
        settings.postmanCollections = from.postmanCollections
        settings.yapiTokens = from.yapiTokens
    }

    override fun checkUI(): Boolean {
        return this.yapiServerTextField?.text == this.settingsInstance?.yapiServer
                && this.postmanTokenTextField?.text == this.settingsInstance?.postmanToken
                && this.trustHostsTextArea?.text == this.settingsInstance?.trustHosts?.joinToString(separator = "\n")
                && (this.postmanExportCollectionPanel!!.isVisible == (this.settingsInstance?.postmanExportMode == PostmanExportMode.UPDATE.name))
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