package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.ui.Messages
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.update
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.logger.Logger
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

@Singleton
class PostmanSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    @Inject
    private lateinit var messagesHelper: MessagesHelper

    @Inject
    private lateinit var postmanApiHelper: PostmanApiHelper

    @Inject
    private lateinit var postmanWorkspaceChecker: PostmanWorkspaceChecker

    @Inject
    private lateinit var logger: Logger

    //region privateToken----------------------------------------------------

    fun hasPrivateToken(): Boolean {
        return getPrivateToken().notNullOrEmpty()
    }

    fun getPrivateToken(dumb: Boolean = true): String? {
        settingBinder.read().postmanToken?.let { return it.trim() }
        if (!dumb) {
            val postmanPrivateToken = messagesHelper.showInputDialog(
                    "Input Postman Private Token",
                    "Postman Private Token", Messages.getInformationIcon())
            if (postmanPrivateToken.isNullOrBlank()) return null
            settingBinder.update {
                it.postmanToken = postmanPrivateToken
            }
            return postmanPrivateToken
        }
        return null
    }

    //endregion privateToken----------------------------------------------------

    //region workspace----------------------------------------------------

    private var tryInputWorkspaceOfModule: HashSet<String> = HashSet()
    private var cacheLock: ReadWriteLock = ReentrantReadWriteLock()

    /**
     * Workspace in setting.
     * Map<module, workspace>
     */
    private var workspaceMap: HashMap<String, String?>? = null

    fun getWorkspace(module: String, dumb: Boolean = true): String? {
        cacheLock.readLock().withLock {
            if (workspaceMap != null) {
                return workspaceMap!![module]
            }
        }
        cacheLock.writeLock().withLock {
            if (workspaceMap == null) {
                initWorkspace()
            }
            workspaceMap!![module]?.let { return it }
            if (!dumb && tryInputWorkspaceOfModule.add(module)) {
                val modulePrivateWorkspace = selectWorkspace(module)
                if (modulePrivateWorkspace.notNullOrBlank()) {
                    setWorkspace(module, modulePrivateWorkspace!!)
                    return modulePrivateWorkspace
                }
            }
        }

        return null
    }

    private fun selectWorkspace(module: String): String? {
        val inputTitle = "Workspace"
        val workspaces = postmanApiHelper.getAllWorkspaces() ?: return null
        val workspaceMap = workspaces.map { it.name to it.id }.toMap()
        return messagesHelper.showEditableChooseDialog(
            "Select $inputTitle Of Module:$module",
            "Postman $inputTitle",
            Messages.getInformationIcon(),
            workspaceMap.keys.sorted().toTypedArray()
        )?.let { workspaceMap[it] }
    }

    private fun initWorkspace() {
        workspaceMap = hashMapOf()
        val settings = settingBinder.read()
        if (settings.postmanWorkspaces != null) {
            val properties = Properties()
            properties.load(settings.postmanWorkspaces!!.byteInputStream())
            properties.forEach { k, v ->
                if (postmanWorkspaceChecker.checkWorkspace(v.toString())) {
                    workspaceMap!![k.toString()] = v.toString()
                } else {
                    logger.warn("workspace $v is not valid, will be removed.")
                    removeWorkspaceByModule(k.toString())
                }
            }
        }
    }

    private fun updateWorkspaces(handle: (Properties) -> Unit) {
        cacheLock.writeLock().withLock {
            val settings = settingBinder.read()
            val properties = Properties()
            if (settings.postmanWorkspaces != null) {
                properties.load(settings.postmanWorkspaces!!.byteInputStream())
            }
            handle(properties)

            settings.postmanWorkspaces = ByteArrayOutputStream().also { properties.store(it, "") }.toString()
            settingBinder.save(settings)
            if (workspaceMap == null) {
                workspaceMap = hashMapOf()
            } else {
                workspaceMap!!.clear()
            }
            // 已经在[setWorkspace]校验过workspace
            properties.forEach { k, v -> workspaceMap!![k.toString()] = v.toString() }
        }
    }

    fun setWorkspace(module: String, workspace: String) {
        if (postmanWorkspaceChecker.checkWorkspace(workspace)) {
            updateWorkspaces {
                it[module] = workspace
            }
            workspaceMap?.put(module, workspace)
        } else {
            logger.warn("workspace $workspace is not valid")
        }
    }

    fun removeWorkspaceByModule(module: String) {
        updateWorkspaces {
            it.remove(module)
        }
        workspaceMap?.remove(module)
    }

    fun removeWorkspace(workspace: String) {
        updateWorkspaces { properties ->
            val removedKeys = properties.entries
                .filter { it.value == workspace }
                .map { it.key }
                .toList()
            removedKeys.forEach {
                properties.remove(it)
                workspaceMap?.remove(it)
            }
        }
    }

    fun readWorkspaces(): HashMap<String, String> {
        if (workspaceMap == null) {
            initWorkspace()
        }
        return HashMap(workspaceMap)
    }

    //endregion workspace----------------------------------------------------

    fun wrapCollection(): Boolean {
        return settingBinder.read().wrapCollection
    }

    fun autoMergeScript(): Boolean {
        return settingBinder.read().autoMergeScript
    }

    fun postmanJson5FormatType(): PostmanJson5FormatType {
        return PostmanJson5FormatType.valueOf(settingBinder.read().postmanJson5FormatType)
    }

}

interface PostmanWorkspaceChecker {
    fun checkWorkspace(workspace: String): Boolean
}