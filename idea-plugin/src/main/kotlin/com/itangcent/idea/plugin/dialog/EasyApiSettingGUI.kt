package com.itangcent.idea.plugin.dialog

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.itangcent.common.utils.SystemUtils
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.extend.rx.AutoComputer
import com.itangcent.intellij.extend.rx.consistent
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import org.apache.commons.io.FileUtils
import java.io.File
import javax.swing.*


class EasyApiSettingGUI {
    private var pullNewestDataBeforeCheckBox: JCheckBox? = null
    private var postmanTokenTextArea: JTextArea? = null

    private var rootPanel: JPanel? = null

    private var globalCacheSizeLabel: JLabel? = null

    private var projectCacheSizeLabel: JLabel? = null

    private var globalCacheSize: String = "0M"

    private var clearGlobalCacheButton: JButton? = null

    private var projectCacheSize: String = "0M"

    private var clearProjectCacheButton: JButton? = null

    private var projectCachePanel: JPanel? = null

    private var hasProject = false

    private var inferEnableCheckBox: JCheckBox? = null

    private var maxDeepTextField: JTextField? = null

    private var readGetterCheckBox: JCheckBox? = null

    private var httpTimeOutTextField: JTextField? = null

    fun getRootPanel(): JPanel? {
        return rootPanel
    }

    private var settings: Settings? = null

    private var autoComputer: AutoComputer = AutoComputer()

    fun onCreate() {
        autoComputer.bind(pullNewestDataBeforeCheckBox!!)
                .consistent(this, "settings.pullNewestDataBefore")

        autoComputer.bind(postmanTokenTextArea!!)
                .consistent(this, "settings.postmanToken")

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

        autoComputer.bind(inferEnableCheckBox!!)
                .consistent(this, "settings.inferEnable")

        autoComputer.bind(readGetterCheckBox!!)
                .consistent(this, "settings.readGetter")

        autoComputer.bind(this.maxDeepTextField!!)
                .with<Int?>(this, "settings.inferMaxDeep")
                .eval { (it ?: 0).toString() }

        autoComputer.bind<Int>(this, "settings.inferMaxDeep")
                .with(this.maxDeepTextField!!)
                .eval {
                    try {
                        it?.toInt() ?: 0
                    } catch (e: Exception) {
                        0
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
        refresh()
    }

    fun setSettings(settings: Settings) {
        autoComputer.value(this::settings, settings)
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
        val cachePath = "$projectBasePath/.idea/.cache/$basePath"
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
        val cachePath = "$projectBasePath/.idea/.cache/$basePath"
        deleteFolder(cachePath)
        computeProjectCacheSize()
    }

    private fun computeGlobalCacheSize() {
        val cachePath = "${globalBasePath()}/$basePath"
        val cacheSize = computeFolderSize(cachePath)
        val readableCacheSize = StringUtil.formatFileSize(cacheSize)
        autoComputer.value(this::globalCacheSize, readableCacheSize)
    }

    private fun clearGlobalCache() {
        val cachePath = "${globalBasePath()}/$basePath"
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

        if (rootPanel?.parent != null) {
            for (project in projects) {
                if (SwingUtilities.isDescendingFrom(rootPanel,
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
            val dataContext = DataManager.getInstance()?.dataContextFromFocus
                    ?.getResultSync(1000)
            val project = dataContext?.getData(DataKeys.PROJECT)
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
        if (home.endsWith("/")) {
            home = home.substring(0, home.length - 1)
        }
        return home
    }

    companion object {
        const val basePath = ".easy_api"
    }
}
