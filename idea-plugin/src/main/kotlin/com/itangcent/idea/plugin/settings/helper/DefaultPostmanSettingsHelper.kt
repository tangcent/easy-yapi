package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.ui.Messages
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.settings.PostmanExportMode
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.update
import com.itangcent.idea.plugin.settings.xml.addPostmanCollections
import com.itangcent.idea.plugin.settings.xml.postmanCollectionsAsPairs
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext

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
                postmanToken = postmanPrivateToken
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
                    this.postmanWorkspace = postmanWorkspace
                }
            }
        }
        return postmanWorkspace
    }

    private fun selectWorkspace(): String? {
        val workspaces = postmanApiHelper.getAllWorkspaces() ?: return null
        return messagesHelper.showEditableChooseDialog(
            "Select Workspace For Current Project",
            "Postman Workspace",
            Messages.getInformationIcon(),
            workspaces.toTypedArray(),
            { it.nameWithType() ?: "" },
            workspaces.firstOrNull()
        )?.let {
            return@let it.id
        }
    }

    //endregion workspace----------------------------------------------------

    override fun wrapCollection(): Boolean {
        return settingBinder.read().wrapCollection
    }

    override fun autoMergeScript(): Boolean {
        return settingBinder.read().autoMergeScript
    }

    override fun buildExample(): Boolean {
        return settingBinder.read().postmanBuildExample
    }

    override fun postmanJson5FormatType(): PostmanJson5FormatType {
        return PostmanJson5FormatType.valueOf(settingBinder.read().postmanJson5FormatType)
    }

    override fun postmanExportMode(): PostmanExportMode {
        return settingBinder.read().postmanExportMode?.let { PostmanExportMode.valueOf(it) } ?: PostmanExportMode.COPY
    }

    override fun getCollectionId(module: String, dumb: Boolean): String? {
        var collectionId = settingBinder.read().postmanCollectionsAsPairs().firstOrNull { it.first == module }?.second
        if (collectionId == null && !dumb) {
            collectionId = selectCollection(module)
            if (collectionId.notNullOrBlank()) {
                addCollectionId(module, collectionId!!)
            }
        }
        return collectionId
    }

    private fun selectCollection(module: String): String? {
        val collections: List<HashMap<String, Any?>> = postmanApiHelper.getAllCollectionPreferred() ?: return null
        return messagesHelper.showEditableChooseDialog(
            "Select a collection to save apis in [$module] to",
            "Postman Collection",
            Messages.getInformationIcon(),
            collections.toTypedArray(),
            { it["name"] as? String ?: "" },
            collections.firstOrNull()
        )?.let {
            return@let it["id"] as? String
        }
    }

    override fun addCollectionId(module: String, collectionId: String) {
        settingBinder.update { addPostmanCollections(module, collectionId) }
    }
}

fun PostmanApiHelper.getAllCollectionPreferred(): List<HashMap<String, Any?>>? {
    val postmanWorkspace = ActionContext.getContext()?.instance(PostmanSettingsHelper::class)?.getWorkspace()
    return if (postmanWorkspace == null) {
        (this.getAllCollection())
    } else {
        (this.getCollectionByWorkspace(postmanWorkspace))
    }
}