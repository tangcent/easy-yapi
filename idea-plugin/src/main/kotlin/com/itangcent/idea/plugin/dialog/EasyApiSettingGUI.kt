package com.itangcent.idea.plugin.dialog

import com.google.inject.Inject
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CheckBoxList
import com.intellij.ui.MutableCollectionComboBoxModel
import com.itangcent.common.utils.*
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.icons.iconOnly
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanUrls.INTEGRATIONS_DASHBOARD
import com.itangcent.idea.plugin.api.export.postman.PostmanWorkspace
import com.itangcent.idea.plugin.settings.MarkdownFormatType
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.CommonSettingsHelper
import com.itangcent.idea.plugin.settings.helper.MemoryPostmanSettingsHelper
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.idea.plugin.settings.helper.RecommendConfigLoader
import com.itangcent.idea.plugin.support.IdeaSupport
import com.itangcent.idea.utils.Charsets
import com.itangcent.idea.utils.SwingUtils
import com.itangcent.idea.utils.isDoubleClick
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.rx.AutoComputer
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


class EasyApiSettingGUI {

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

    private var postmanTokenTextArea: JTextArea? = null

    private lateinit var postmanWorkspaceComboBox: JComboBox<PostmanWorkspaceData>

    private var wrapCollectionCheckBox: JCheckBox? = null

    private var autoMergeScriptCheckBox: JCheckBox? = null

    private var postmanJson5FormatTypeComboBox: JComboBox<String>? = null

    private var postmanWorkSpaceRefreshButton: JButton? = null

    @Inject
    private lateinit var postmanCachedApiHelper: PostmanCachedApiHelper

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    private var allWorkspaces: List<PostmanWorkspaceData>? = null

    private var selectedPostmanWorkspace: PostmanWorkspaceData?
        get() {
            val postmanWorkspace = settings?.postmanWorkspace ?: return DEFAULT_WORKSPACE
            return allWorkspaces?.firstOrNull { it.id == postmanWorkspace } ?: PostmanWorkspaceData(
                postmanWorkspace,
                "unknown"
            )
        }
        set(value) {
            settings?.postmanWorkspace = value?.id
        }

    //endregion postman-----------------------------------------------------

    //region general-----------------------------------------------------

    private var pullNewestDataBeforeCheckBox: JCheckBox? = null

    private var generalPanel: JPanel? = null

    private var logLevelComboBox: JComboBox<Logger.Level>? = null

    private var methodDocEnableCheckBox: JCheckBox? = null

    private var genericEnableCheckBox: JCheckBox? = null

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

    //region recommend-----------------------------------------------------
    private var recommendConfigList: CheckBoxList<String>? = null

    private var previewTextArea: JTextArea? = null
    //endregion recommend-----------------------------------------------------

    private var builtInConfigTextArea: JTextArea? = null

    private val throttleHelper = ThrottleHelper()

    fun getRootPanel(): JPanel? {
        return rootPanel
    }

    private var settings: Settings? = null

    private var autoComputer: AutoComputer = AutoComputer()

    /**
     * please call it from dispatch thread
     */
    fun onCreate() {

        initExportAndImport()

        initGeneral()

        initPostman()

        initRecommendConfig()
    }

    private fun initPostman() {

        EasyIcons.Refresh.iconOnly(this.postmanWorkSpaceRefreshButton)
        SwingUtils.immersed(this.postmanWorkSpaceRefreshButton!!)

        postmanJson5FormatTypeComboBox!!.model =
            DefaultComboBoxModel(PostmanJson5FormatType.values().mapToTypedArray { it.name })

        autoComputer.bind<String?>(this, "settings.postmanJson5FormatType")
            .with(this.postmanJson5FormatTypeComboBox!!)
            .filter { throttleHelper.acquire("settings.postmanJson5FormatType", 300) }
            .eval { (it ?: PostmanJson5FormatType.EXAMPLE_ONLY.name) }

        autoComputer.bindVisible(postmanWorkSpaceRefreshButton!!)
            .with(this.postmanTokenTextArea!!)
            .eval { it.notNullOrBlank() }

        postmanWorkSpaceRefreshButton!!.addActionListener {
            refreshPostmanWorkSpaces(false)
        }

        postmanTokenLabel!!.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                if(e.isDoubleClick()) {
                    actionContext.instance(IdeaSupport::class).openUrl(INTEGRATIONS_DASHBOARD)
                }
            }
        })
    }

    private fun initGeneral() {
        //region general-----------------------------------------------------

        recommendedCheckBox!!.toolTipText = RecommendConfigLoader.plaint()

        autoComputer.bind(pullNewestDataBeforeCheckBox!!)
            .mutual(this, "settings.pullNewestDataBefore")

        autoComputer.bind(postmanTokenTextArea!!)
            .mutual(this, "settings.postmanToken")

        autoComputer.bind(wrapCollectionCheckBox!!)
            .mutual(this, "settings.wrapCollection")

        autoComputer.bind(autoMergeScriptCheckBox!!)
            .mutual(this, "settings.autoMergeScript")

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
            .mutual(this, "settings.methodDocEnable")

        autoComputer.bind(genericEnableCheckBox!!)
            .mutual(this, "settings.genericEnable")

        autoComputer.bind(inferEnableCheckBox!!)
            .mutual(this, "settings.inferEnable")

        autoComputer.bind(readGetterCheckBox!!)
            .mutual(this, "settings.readGetter")

        autoComputer.bind(readSetterCheckBox!!)
            .mutual(this, "settings.readSetter")

        autoComputer.bind(formExpandedCheckBox!!)
            .mutual(this, "settings.formExpanded")

        autoComputer.bind(queryExpandedCheckBox!!)
            .mutual(this, "settings.queryExpanded")

        autoComputer.bind(recommendedCheckBox!!)
            .mutual(this, "settings.useRecommendConfig")

        autoComputer.bind(outputDemoCheckBox!!)
            .mutual(this, "settings.outputDemo")

        autoComputer.bind(this.maxDeepTextField!!)
            .with<Int?>(this, "settings.inferMaxDeep")
            .eval { (it ?: Settings.DEFAULT_INFER_MAX_DEEP).toString() }

        autoComputer.bind<Int>(this, "settings.inferMaxDeep")
            .with(this.maxDeepTextField!!)
            .eval {
                try {
                    it?.toInt() ?: Settings.DEFAULT_INFER_MAX_DEEP
                } catch (e: Exception) {
                    Settings.DEFAULT_INFER_MAX_DEEP
                }
            }

        autoComputer.bind(yapiServerTextField!!)
            .mutual(this, "settings.yapiServer")

        autoComputer.bind(yapiTokenTextArea!!)
            .mutual(this, "settings.yapiTokens")

        autoComputer.bind(enableUrlTemplatingCheckBox!!)
            .mutual(this, "settings.enableUrlTemplating")

        autoComputer.bind(switchNoticeCheckBox!!)
            .mutual(this, "settings.switchNotice")

        autoComputer.bind(loginModeCheckBox!!)
            .mutual(this, "settings.loginMode")

        autoComputer.bind(yapiReqBodyJson5CheckBox!!)
            .mutual(this, "settings.yapiReqBodyJson5")

        autoComputer.bind(yapiResBodyJson5CheckBox!!)
            .mutual(this, "settings.yapiResBodyJson5")

        autoComputer.bind(yapiTokenLabel!!)
            .with<Boolean?>(this, "settings.loginMode")
            .eval {
                if (it == true) {
                    "projectIds"
                } else {
                    "tokens"
                }
            }

        autoComputer.bind(this.httpTimeOutTextField!!)
            .with<Int?>(this, "settings.httpTimeOut")
            .eval { (it ?: ConfigurableHttpClientProvider.defaultHttpTimeOut).toString() }

        autoComputer.bind<Int>(this, "settings.httpTimeOut")
            .with(this.httpTimeOutTextField!!)
            .eval {
                try {
                    it?.toInt() ?: ConfigurableHttpClientProvider.defaultHttpTimeOut
                } catch (e: Exception) {
                    ConfigurableHttpClientProvider.defaultHttpTimeOut
                }
            }

        logLevelComboBox!!.model = DefaultComboBoxModel(CommonSettingsHelper.CoarseLogLevel.editableValues())

        autoComputer.bind<Int?>(this, "settings.logLevel")
            .with(this.logLevelComboBox!!)
            .filter { throttleHelper.acquire("settings.logLevel", 300) }
            .eval { (it ?: CommonSettingsHelper.CoarseLogLevel.LOW).getLevel() }

        outputCharsetComboBox!!.model = DefaultComboBoxModel(Charsets.SUPPORTED_CHARSETS)

        markdownFormatTypeComboBox!!.model =
            DefaultComboBoxModel(MarkdownFormatType.values().mapToTypedArray { it.name })

        autoComputer.bind<String?>(this, "settings.outputCharset")
            .with(this.outputCharsetComboBox!!)
            .filter { throttleHelper.acquire("settings.outputCharset", 300) }
            .eval { (it ?: Charsets.UTF_8).displayName() }

        autoComputer.bind<String?>(this, "settings.markdownFormatType")
            .with(this.markdownFormatTypeComboBox!!)
            .filter { throttleHelper.acquire("settings.markdownFormatType", 300) }
            .eval { (it ?: MarkdownFormatType.SIMPLE.name) }

        autoComputer.bind(this.previewTextArea!!)
            .with<String>(this, "settings.recommendConfigs")
            .eval { configs ->
                RecommendConfigLoader.buildRecommendConfig(
                    configs,
                    "\n#${"-".repeat(20)}\n"
                )
            }

        autoComputer.bind(this.builtInConfigTextArea!!)
            .with<String?>(this, "settings.builtInConfig")
            .eval { it.takeIf { it.notNullOrBlank() } ?: DEFAULT_BUILT_IN_CONFIG }

        autoComputer.bind<String?>(this, "settings.builtInConfig")
            .with(builtInConfigTextArea!!)
            .eval { it.takeIf { it != DEFAULT_BUILT_IN_CONFIG } ?: "" }

        autoComputer.bind<Array<String>>(this, "settings.trustHosts")
            .with(trustHostsTextArea!!)
            .eval { trustHosts -> trustHosts?.lines()?.toTypedArray() ?: emptyArray() }

        autoComputer.bind(trustHostsTextArea!!)
            .with<Array<String>>(this, "settings.trustHosts")
            .eval { trustHosts -> trustHosts.joinToString(separator = "\n") }

        //endregion general-----------------------------------------------------
    }

    private fun initExportAndImport() {
        EasyIcons.Export.iconOnly(this.exportButton)
        EasyIcons.Import.iconOnly(this.importButton)
        SwingUtils.immersed(this.exportButton!!)
        SwingUtils.immersed(this.importButton!!)

        this.exportButton!!.addActionListener {
            export()
        }
        this.importButton!!.addActionListener {
            import()
        }
    }

    private fun initRecommendConfig() {
        recommendConfigList!!.setItems(RecommendConfigLoader.codes().toList())
        {
            it.padEnd(30) + "    " +
                    RecommendConfigLoader[it]?.truncate(100)
                        ?.replace("\n", "    ")
        }

        this.recommendConfigList!!.setCheckBoxListListener { index, value ->
            val code = RecommendConfigLoader[index]
            val settings = this.settings!!
            if (value) {
                settings.recommendConfigs = RecommendConfigLoader.addSelectedConfig(settings.recommendConfigs, code)
            } else {
                settings.recommendConfigs = RecommendConfigLoader.removeSelectedConfig(settings.recommendConfigs, code)
            }
            autoComputer.value(this, "settings.recommendConfigs", settings.recommendConfigs)
        }
    }

    fun setSettings(settings: Settings) {
        throttleHelper.refresh("throttleHelper")

        autoComputer.value(this::settings, settings.copy())

        this.postmanWorkspaceComboBoxModel?.selectedItem = this.selectedPostmanWorkspace
        this.logLevelComboBox!!.selectedItem = CommonSettingsHelper.CoarseLogLevel.toLevel(settings.logLevel)
        this.outputCharsetComboBox!!.selectedItem = Charsets.forName(settings.outputCharset)
        this.postmanJson5FormatTypeComboBox!!.selectedItem = settings.postmanJson5FormatType
        this.markdownFormatTypeComboBox!!.selectedItem = settings.markdownFormatType

        RecommendConfigLoader.selectedCodes(settings.recommendConfigs).forEach {
            this.recommendConfigList!!.setItemSelected(it, true)
        }
        refresh()
    }

    private fun refresh() {
        actionContext.runAsync {
            refreshCache()
            refreshPostmanWorkSpaces()
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
        (postmanSettingsHelper as? MemoryPostmanSettingsHelper)?.setPrivateToken(this.settings?.postmanToken)
        val allWorkspaces = postmanCachedApiHelper.getAllWorkspaces(userCache)
        val allWorkspacesData: ArrayList<PostmanWorkspaceData> = arrayListOf(DEFAULT_WORKSPACE)
        if (allWorkspaces == null) {
            if (settings?.postmanWorkspace != null) {
                allWorkspacesData.add(PostmanWorkspaceData(settings?.postmanWorkspace ?: "", "unknown"))
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

    fun getSettings(): Settings {
        if (settings == null) {
            settings = Settings()
        }
        return settings!!
    }

    private fun globalBasePath(): String {

        var home = SystemUtils.userHome
        if (home.endsWith(File.separator)) {
            home = home.substring(0, home.length - 1)
        }
        return home
    }

    private fun export() {
        val descriptor = FileSaverDescriptor(
            "Export Setting",
            "Choose directory to export setting to",
            "json"
        )
        descriptor.withHideIgnored(false)
        val chooser = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, this.getRootPanel()!!)
        var toSelect: VirtualFile? = null
        val lastLocation = PropertiesComponent.getInstance().getValue(setting_path)
        if (lastLocation != null) {
            toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
        }
        val fileWrapper = chooser.save(toSelect, "setting.json")
        if (fileWrapper != null) {
            com.itangcent.intellij.util.FileUtils.forceSave(
                fileWrapper.file.path,
                GsonUtils.toJson(settings).toByteArray(kotlin.text.Charsets.UTF_8)
            )
        }
    }

    private fun import() {
        val descriptor = FileChooserDescriptorFactory
            .createSingleFileOrFolderDescriptor()
            .withTitle("Import Setting")
            .withDescription("Choose setting file")
            .withHideIgnored(false)
        val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, null, this.getRootPanel()!!)
        var toSelect: VirtualFile? = null
        val lastLocation = PropertiesComponent.getInstance().getValue(setting_path)
        if (lastLocation != null) {
            toSelect = LocalFileSystem.getInstance().refreshAndFindFileByPath(lastLocation)
        }
        val files = chooser.choose(null, toSelect)
        if (files.notNullOrEmpty()) {
            val virtualFile = files[0]
            val read = FileUtils.read(File(virtualFile.path), kotlin.text.Charsets.UTF_8)
            if (read.notNullOrEmpty()) {
                setSettings(GsonUtils.fromJson(read!!, Settings::class))
            }
        }
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
            this.name = postmanWorkspace.name ?: ""
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

    companion object {
        const val basePath = ".easy_api"

        const val setting_path = "easy.api.setting.path"

        private const val built_in_config_name = ".default.built.in.easy.api.config"

        val DEFAULT_BUILT_IN_CONFIG = ResourceUtils.readResource(built_in_config_name)

        private var DEFAULT_WORKSPACE = PostmanWorkspaceData(null, "")
    }
}

private val LOG = org.apache.log4j.Logger.getLogger(EasyApiSettingGUI::class.java)