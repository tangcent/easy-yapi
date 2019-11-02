package com.itangcent.idea.plugin.dialog

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.CheckBoxList
import com.itangcent.common.utils.SystemUtils
import com.itangcent.common.utils.truncate
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.utils.ConfigurableLogger
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.extend.rx.mutual
import com.itangcent.intellij.logger.Logger
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.*
import javax.swing.*


class EasyApiSettingGUI {

    private var rootPanel: JPanel? = null

    //region general-----------------------------------------------------
    private var pullNewestDataBeforeCheckBox: JCheckBox? = null

    private var postmanTokenTextArea: JTextArea? = null

    private var generalPanel: JPanel? = null

    private var logLevelComboBox: JComboBox<Logger.Level>? = null

    private var methodDocEnableCheckBox: JCheckBox? = null

    private var globalCacheSizeLabel: JLabel? = null

    private var projectCacheSizeLabel: JLabel? = null

    private var globalCacheSize: String = "0M"

    private var clearGlobalCacheButton: JButton? = null

    private var projectCacheSize: String = "0M"

    private var clearProjectCacheButton: JButton? = null

    private var projectCachePanel: JPanel? = null

    private var hasProject = false

    private var outputDemoCheckBox: JCheckBox? = null

    private var inferEnableCheckBox: JCheckBox? = null

    private var maxDeepTextField: JTextField? = null

    private var readGetterCheckBox: JCheckBox? = null

    private var yapiServerTextField: JTextField? = null

    private var yapiTokenTextArea: JTextArea? = null

    private var recommendedCheckBox: JCheckBox? = null

    private var httpTimeOutTextField: JTextField? = null
    //endregion general-----------------------------------------------------

    private var recommendConfigList: CheckBoxList<String>? = null

    private var previewTextArea: JTextArea? = null

    private val throttleHelper = ThrottleHelper()

    fun getRootPanel(): JPanel? {
        return rootPanel
    }

    private var settings: Settings? = null

    private var autoComputer: AutoComputer = AutoComputer()

    fun onCreate() {

        //region general-----------------------------------------------------
        recommendedCheckBox!!.toolTipText = RecommendConfigReader.RECOMMEND_CONFIG_PLAINT

        autoComputer.bind(pullNewestDataBeforeCheckBox!!)
                .mutual(this, "settings.pullNewestDataBefore")

        autoComputer.bind(postmanTokenTextArea!!)
                .mutual(this, "settings.postmanToken")

        autoComputer.bind(this.globalCacheSizeLabel!!)
                .with(this::globalCacheSize)
                .eval { it }

        this.clearGlobalCacheButton!!.addActionListener {
            clearGlobalCache()
        }

        autoComputer.bind(this.projectCacheSizeLabel!!)
                .with(this::projectCacheSize)
                .eval { it }

        autoComputer.bindVisible(this.projectCachePanel!!)
                .with(this::hasProject)
                .eval { it }

        this.clearProjectCacheButton!!.addActionListener {
            clearProjectCache()
        }

        autoComputer.bind(methodDocEnableCheckBox!!)
                .mutual(this, "settings.methodDocEnable")

        autoComputer.bind(inferEnableCheckBox!!)
                .mutual(this, "settings.inferEnable")

        autoComputer.bind(readGetterCheckBox!!)
                .mutual(this, "settings.readGetter")

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

        logLevelComboBox!!.model = DefaultComboBoxModel(ConfigurableLogger.CoarseLogLevel.editableValues())

        autoComputer.bind<Int?>(this, "settings.logLevel")
                .with(this.logLevelComboBox!!)
                .filter { throttleHelper.acquire("settings.logLevel", 300) }
                .eval { (it ?: ConfigurableLogger.CoarseLogLevel.LOW).getLevel() }

        autoComputer.bind(this.previewTextArea!!)
                .with<String>(this, "settings.recommendConfigs")
                .eval { configs -> RecommendConfigReader.buildRecommendConfig(configs.split(",")) }

        //endregion  general-----------------------------------------------------

        bindRecommendConfig()

        refresh()

        this.recommendConfigList!!.setCheckBoxListListener { index, value ->
            val code = RecommendConfigReader.RECOMMEND_CONFIG_CODES[index]
            val configs = settings!!.recommendConfigs.split(",")
            if (value) {
                if (!configs.contains(code)) {
                    val newConfigs = LinkedList(configs)
                    newConfigs.add(code)
                    settings!!.recommendConfigs = newConfigs.joinToString(",")
                }
            } else {
                settings!!.recommendConfigs = configs.filter { it != code }.joinToString(",")
            }
            autoComputer.value(this, "settings.recommendConfigs", settings!!.recommendConfigs)
//            this.previewTextArea!!.text =  RecommendConfigReader.buildRecommendConfig(settings!!.recommendConfigs)
        }
    }

    fun setSettings(settings: Settings) {
        throttleHelper.refresh("throttleHelper")

        autoComputer.value(this::settings, settings)

        this.logLevelComboBox!!.selectedItem =
                settings.logLevel.let { ConfigurableLogger.CoarseLogLevel.toLevel(it) }

        val configs = settings.recommendConfigs.split(",")
        RecommendConfigReader.RECOMMEND_CONFIG_CODES.forEach {
            this.recommendConfigList!!.setItemSelected(it, configs.contains(it))
        }
//        this.recommendConfigList!!.selectedIndices = settings.recommendConfigs.map {
//            RecommendConfigReader.RECOMMEND_CONFIG_CODES.indexOf(it)
//        }.toIntArray()
    }

    private fun bindRecommendConfig() {
        recommendConfigList!!.setItems(RecommendConfigReader.RECOMMEND_CONFIG_CODES.toList())
        {
            RecommendConfigReader.RECOMMEND_CONFIG_MAP[it]?.truncate(100)
                    ?.replace("\n", "     ")
                    ?: ""
        }
    }

    fun refresh() {

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

    private fun computeProjectCacheSize() {
        val currentProject = getCurrentProject()
        autoComputer.value(this::hasProject, currentProject != null)
        if (currentProject == null) {
            return
        }
        val projectBasePath = currentProject.basePath
        val cachePath = "$projectBasePath${File.separator}.idea${File.separator}.cache${File.separator}$basePath"
        val cacheSize = computeFolderSize(cachePath)
        val readableCacheSize = StringUtil.formatFileSize(cacheSize)
        autoComputer.value(this::projectCacheSize, readableCacheSize)
    }

    private fun clearProjectCache() {
        val currentProject = getCurrentProject()
        autoComputer.value(this::hasProject, currentProject != null)
        if (currentProject == null) {
            return
        }
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
            when {
                file.isFile -> return FileUtils.sizeOf(file)
                file.isDirectory -> return FileUtils.sizeOfDirectory(file)
            }
        }
        return 0
    }

    private fun getCurrentProject(): Project? {
        val wm = WindowManager.getInstance()
        val projects = ProjectManager.getInstance().openProjects
        if (projects.size == 1) {
            return projects[0]
        }

        if (generalPanel?.parent != null) {
            for (project in projects) {
                if (SwingUtilities.isDescendingFrom(generalPanel,
                                wm.suggestParentWindow(project))) {
                    return project
                }
            }
        }

        for (project in projects) {
            val window = wm.suggestParentWindow(project)
            if (window != null && window.isActive) {
                return project
            }
        }

        try {
            val dataContext = DataManager.getInstance()?.getDataContext(generalPanel)
            val project = dataContext?.getData(CommonDataKeys.PROJECT)
            if (project != null) {
                return project
            }
        } catch (e: Exception) {
            ///ignore
        }

        return null
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

    companion object {
        const val basePath = ".easy_api"
    }
}
