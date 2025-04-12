package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.openapi.ui.ComboBoxTableRenderer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.table.JBTable
import com.itangcent.cache.withoutCache
import com.itangcent.common.logger.Log
import com.itangcent.common.utils.*
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.plugin.api.cache.ProjectCacheRepository
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanUrls.INTEGRATIONS_DASHBOARD
import com.itangcent.idea.plugin.api.export.postman.PostmanWorkspace
import com.itangcent.idea.plugin.configurable.AbstractEasyApiSettingGUI
import com.itangcent.idea.plugin.settings.*
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
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.utils.EnumKit
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel


class EasyApiSettingGUI : AbstractEasyApiSettingGUI() {

    @Inject
    private lateinit var actionContext: ActionContext

    private var rootPanel: JPanel? = null

    //region postman-----------------------------------------------------

    private var postmanTokenLabel: JLabel? = null

    private var postmanTokenTextField: JTextField? = null

    private lateinit var postmanWorkspaceComboBox: JComboBox<PostmanWorkspaceData>

    private var wrapCollectionCheckBox: JCheckBox? = null

    private var autoMergeScriptCheckBox: JCheckBox? = null

    private var buildExampleCheckBox: JCheckBox? = null

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

    private var logLevelComboBox: JComboBox<CommonSettingsHelper.VerbosityLevel>? = null

    private var loggerConsoleTypeComboBox: JComboBox<CommonSettingsHelper.LoggerConsoleType>? = null

    private var methodDocEnableCheckBox: JCheckBox? = null

    private var genericEnableCheckBox: JCheckBox? = null

    private var feignEnableCheckBox: JCheckBox? = null

    private var jaxrsEnableCheckBox: JCheckBox? = null

    private var actuatorEnableCheckBox: JCheckBox? = null

    private var globalCacheSizeLabel: JLabel? = null

    private var projectCacheSizeLabel: JLabel? = null

    private var clearGlobalCacheButton: JButton? = null

    private var clearProjectCacheButton: JButton? = null

    private var outputDemoCheckBox: JCheckBox? = null

    private var outputCharsetComboBox: JComboBox<Charsets>? = null

    private var markdownFormatTypeComboBox: JComboBox<String>? = null

    private var inferEnableCheckBox: JCheckBox? = null

    private var selectedOnlyCheckBox: JCheckBox? = null

    private var maxDeepTextField: JTextField? = null

    private var readGetterCheckBox: JCheckBox? = null

    private var readSetterCheckBox: JCheckBox? = null

    private var yapiTokenLabel: JLabel? = null

    private var yapiServerTextField: JTextField? = null

    private var yapiTokenTextArea: JTextArea? = null

    private var enableUrlTemplatingCheckBox: JCheckBox? = null

    private var switchNoticeCheckBox: JCheckBox? = null

    private var loginModeCheckBox: JCheckBox? = null

    private var yapiExportModeComboBox: JComboBox<String>? = null

    private var yapiReqBodyJson5CheckBox: JCheckBox? = null

    private var yapiResBodyJson5CheckBox: JCheckBox? = null

    private var formExpandedCheckBox: JCheckBox? = null

    private var queryExpandedCheckBox: JCheckBox? = null

    private var recommendedCheckBox: JCheckBox? = null

    private var unsafeSslCheckBox: JCheckBox? = null

    private var httpClientComboBox: JComboBox<String>? = null

    private var httpTimeOutTextField: JTextField? = null

    private var trustHostsTextArea: JTextArea? = null

    //endregion general-----------------------------------------------------

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
            DefaultComboBoxModel(PostmanJson5FormatType.entries.mapToTypedArray { it.name })

        postmanExportModeComboBox!!.model =
            DefaultComboBoxModel(PostmanExportMode.entries.mapToTypedArray { it.name })

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
            forceRefreshPostmanCollections()
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

        yapiExportModeComboBox!!.model =
            DefaultComboBoxModel(YapiExportMode.entries.mapToTypedArray { it.name })

        logLevelComboBox!!.model = DefaultComboBoxModel(CommonSettingsHelper.VerbosityLevel.entries.toTypedArray())

        loggerConsoleTypeComboBox!!.model = DefaultComboBoxModel(CommonSettingsHelper.LoggerConsoleType.entries.toTypedArray())

        outputCharsetComboBox!!.model = DefaultComboBoxModel(Charsets.SUPPORTED_CHARSETS)

        markdownFormatTypeComboBox!!.model =
            DefaultComboBoxModel(MarkdownFormatType.entries.mapToTypedArray { it.name })

        httpClientComboBox!!.model =
            DefaultComboBoxModel(HttpClientType.entries.mapToTypedArray { it.value })

        //endregion general-----------------------------------------------------
    }

    override fun setSettings(settings: Settings) {
        super.setSettings(settings)

        this.pullNewestDataBeforeCheckBox!!.isSelected = settings.pullNewestDataBefore
        this.postmanTokenTextField!!.text = settings.postmanToken ?: ""
        this.wrapCollectionCheckBox!!.isSelected = settings.wrapCollection
        this.autoMergeScriptCheckBox!!.isSelected = settings.autoMergeScript
        this.buildExampleCheckBox!!.isSelected = settings.postmanBuildExample

        this.postmanWorkspaceComboBoxModel?.selectedItem = this.selectedPostmanWorkspace
        this.logLevelComboBox!!.selectedItem = CommonSettingsHelper.VerbosityLevel.toLevel(settings.logLevel)
        this.loggerConsoleTypeComboBox!!.selectedItem =
            EnumKit.safeValueOf<CommonSettingsHelper.LoggerConsoleType>(settings.loggerConsoleType)

        this.outputCharsetComboBox!!.selectedItem = Charsets.forName(settings.outputCharset)
        this.postmanJson5FormatTypeComboBox!!.selectedItem = settings.postmanJson5FormatType
        this.postmanExportModeComboBox!!.selectedItem = settings.postmanExportMode
        this.markdownFormatTypeComboBox!!.selectedItem = settings.markdownFormatType

        this.methodDocEnableCheckBox!!.isSelected = settings.methodDocEnable
        this.genericEnableCheckBox!!.isSelected = settings.genericEnable
        this.feignEnableCheckBox!!.isSelected = settings.feignEnable
        this.jaxrsEnableCheckBox!!.isSelected = settings.jaxrsEnable
        this.actuatorEnableCheckBox!!.isSelected = settings.actuatorEnable
        this.inferEnableCheckBox!!.isSelected = settings.inferEnable
        this.selectedOnlyCheckBox!!.isSelected = settings.selectedOnly
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
        this.yapiExportModeComboBox!!.selectedItem = settings.yapiExportMode
        this.yapiReqBodyJson5CheckBox!!.isSelected = settings.yapiReqBodyJson5
        this.yapiResBodyJson5CheckBox!!.isSelected = settings.yapiResBodyJson5

        this.trustHostsTextArea!!.text = settings.trustHosts.joinToString(separator = "\n")
        this.maxDeepTextField!!.text = settings.inferMaxDeep.toString()

        this.unsafeSslCheckBox!!.isSelected = settings.unsafeSsl
        this.httpClientComboBox!!.selectedItem = settings.httpClient
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
        } catch (_: Exception) {
            //ignore
        }

        try {
            computeProjectCacheSize()
        } catch (_: Exception) {
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
    private fun forceRefreshPostmanCollections() {
        postmanCachedApiHelper.withoutCache {
            refreshPostmanCollections()
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
        val cachePath = actionContext.instance(ProjectCacheRepository::class).cachePath()
        val cacheSize = computeFolderSize(cachePath)
        actionContext.runInSwingUI {
            this.projectCacheSizeLabel!!.text = StringUtil.formatFileSize(cacheSize)
        }
    }

    private fun clearProjectCache() {
        val cachePath = actionContext.instance(ProjectCacheRepository::class).cachePath()
        deleteFolder(cachePath)
        computeProjectCacheSize()
    }

    private fun computeGlobalCacheSize() {
        val cachePath = actionContext.instance(DefaultLocalFileRepository::class).cachePath()
        val cacheSize = computeFolderSize(cachePath)
        actionContext.runInSwingUI {
            this.globalCacheSizeLabel!!.text = StringUtil.formatFileSize(cacheSize)
        }
    }

    private fun clearGlobalCache() {
        val cachePath = actionContext.instance(DefaultLocalFileRepository::class).cachePath()
        deleteFolder(cachePath)
        computeGlobalCacheSize()
    }

    private fun LocalFileRepository.cachePath(): String {
        return this.getOrCreateFile(".setting.size").parentFile.path
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

        constructor(collectionData: Map<String, Any?>) {
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
        settings.postmanBuildExample = buildExampleCheckBox!!.isSelected
        postmanJson5FormatTypeComboBox!!.selected()?.let {
            settings.postmanJson5FormatType = it
        }
        settings.pullNewestDataBefore = pullNewestDataBeforeCheckBox!!.isSelected
        settings.methodDocEnable = methodDocEnableCheckBox!!.isSelected
        settings.genericEnable = genericEnableCheckBox!!.isSelected
        settings.feignEnable = feignEnableCheckBox!!.isSelected
        settings.jaxrsEnable = jaxrsEnableCheckBox!!.isSelected
        settings.actuatorEnable = actuatorEnableCheckBox!!.isSelected
        settings.queryExpanded = queryExpandedCheckBox!!.isSelected
        settings.formExpanded = formExpandedCheckBox!!.isSelected
        settings.readGetter = readGetterCheckBox!!.isSelected
        settings.readSetter = readSetterCheckBox!!.isSelected
        settings.inferEnable = inferEnableCheckBox!!.isSelected
        settings.selectedOnly = selectedOnlyCheckBox!!.isSelected
        settings.inferMaxDeep = maxDeepTextField!!.text.toIntOrNull() ?: Settings.DEFAULT_INFER_MAX_DEEP
        settings.yapiServer = yapiServerTextField!!.text
        settings.yapiTokens = yapiTokenTextArea!!.text
        settings.enableUrlTemplating = enableUrlTemplatingCheckBox!!.isSelected
        settings.switchNotice = switchNoticeCheckBox!!.isSelected
        settings.loginMode = loginModeCheckBox!!.isSelected
        settings.yapiExportMode = yapiExportModeComboBox!!.selectedItem as? String ?: YapiExportMode.ALWAYS_UPDATE.name
        settings.yapiReqBodyJson5 = yapiReqBodyJson5CheckBox!!.isSelected
        settings.yapiResBodyJson5 = yapiResBodyJson5CheckBox!!.isSelected
        settings.unsafeSsl = unsafeSslCheckBox!!.isSelected
        settings.httpClient = httpClientComboBox!!.selectedItem as? String ?: HttpClientType.APACHE.value
        settings.httpTimeOut = httpTimeOutTextField!!.text.toIntOrNull() ?: 10
        settings.useRecommendConfig = recommendedCheckBox!!.isSelected
        settings.logLevel = (logLevelComboBox!!.selected() ?: CommonSettingsHelper.VerbosityLevel.NORMAL).level
        settings.loggerConsoleType = (loggerConsoleTypeComboBox!!.selected() ?: CommonSettingsHelper.LoggerConsoleType.SINGLE_CONSOLE).name
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

    companion object : Log() {
        const val setting_path = "easy.api.setting.path"

        private var DEFAULT_WORKSPACE = PostmanWorkspaceData(null, "")
    }
}