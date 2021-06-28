package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.ui.Messages
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.update
import com.itangcent.idea.swing.MessagesHelper
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

    //region privateToken----------------------------------------------------

    fun hasPrivateToken(): Boolean {
        return getPrivateToken().notNullOrEmpty()
    }

    fun getPrivateToken(dumb: Boolean = true): String? {
        settingBinder.read().postmanToken?.let { return it.trim() }
        if (!dumb) {
            val postmanPrivateToken = messagesHelper.showInputDialog(
                "Input Postman Private Token",
                "Postman Private Token", Messages.getInformationIcon()
            )
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
                val modulePrivateWorkspace = inputNewWorkspace(module)
                if (modulePrivateWorkspace.notNullOrBlank()) {
                    setWorkspace(module, modulePrivateWorkspace!!)
                    return modulePrivateWorkspace
                }
            }
        }

        return null
    }

    private fun inputNewWorkspace(module: String): String? {
        val inputTitle = "Workspace"
        return messagesHelper.showInputDialog(
            "Input $inputTitle Of Module:$module",
            "Postman $inputTitle", Messages.getInformationIcon()
        )
    }

    private fun initWorkspace() {
        workspaceMap = hashMapOf()
        val settings = settingBinder.read()
        if (settings.postmanWorkspaces != null) {
            val properties = Properties()
            properties.load(settings.postmanWorkspaces!!.byteInputStream())
            properties.forEach { k, v -> workspaceMap!![k.toString()] = v.toString() }
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
            properties.forEach { t, u -> workspaceMap!![t.toString()] = u.toString() }
        }
    }

    fun setWorkspace(module: String, workspace: String) {
        updateWorkspaces {
            it[module] = workspace
        }
        workspaceMap?.put(module, workspace)
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