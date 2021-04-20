package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.ui.Messages
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.update
import com.itangcent.idea.swing.MessagesHelper

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