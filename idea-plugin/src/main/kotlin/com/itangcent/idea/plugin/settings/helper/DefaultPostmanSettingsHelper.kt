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

@Singleton
open class DefaultPostmanSettingsHelper : PostmanSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    @Inject
    private lateinit var messagesHelper: MessagesHelper

    @Inject
    private lateinit var postmanApiHelper: PostmanApiHelper

    //region privateToken----------------------------------------------------

    override fun hasPrivateToken(): Boolean {
        return getPrivateToken().notNullOrEmpty()
    }

    override fun getPrivateToken(dumb: Boolean): String? {
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

    override fun getWorkspace(dumb: Boolean): String? {
        var postmanWorkspace = settingBinder.read().postmanWorkspace
        if (postmanWorkspace == null && !dumb) {
            postmanWorkspace = selectWorkspace()
            if (postmanWorkspace.notNullOrBlank()) {
                settingBinder.update {
                    it.postmanWorkspace = postmanWorkspace
                }
            }
        }
        return postmanWorkspace
    }

    override fun selectWorkspace(): String? {
        val workspaces = postmanApiHelper.getAllWorkspaces() ?: return null
        var workspaceNames = workspaces.map { it.nameWithType() ?: "" }
        if (workspaceNames.distinct().size != workspaceNames.size) {
            workspaceNames = workspaceNames.mapIndexed { index, workspace -> "${index + 1}: $workspace" }
        }
        return messagesHelper.showEditableChooseDialog(
            "Select Workspace For Current Project",
            "Postman Workspace",
            Messages.getInformationIcon(),
            workspaceNames.toTypedArray(),
            workspaceNames.firstOrNull()
        )?.let {
            val index = workspaceNames.indexOf(it)
            if (index == -1) {
                return@let null
            }
            return@let workspaces[index].id
        }
    }

    //endregion workspace----------------------------------------------------

    override fun wrapCollection(): Boolean {
        return settingBinder.read().wrapCollection
    }

    override fun autoMergeScript(): Boolean {
        return settingBinder.read().autoMergeScript
    }

    override fun postmanJson5FormatType(): PostmanJson5FormatType {
        return PostmanJson5FormatType.valueOf(settingBinder.read().postmanJson5FormatType)
    }
}