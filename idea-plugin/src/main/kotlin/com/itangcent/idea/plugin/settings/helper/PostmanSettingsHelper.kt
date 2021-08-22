package com.itangcent.idea.plugin.settings.helper

import com.google.inject.ImplementedBy
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType

@ImplementedBy(DefaultPostmanSettingsHelper::class)
interface PostmanSettingsHelper {

    fun hasPrivateToken(): Boolean

    fun getPrivateToken(dumb: Boolean = true): String?

    fun getWorkspace(dumb: Boolean = true): String?

    fun selectWorkspace(): String?

    fun wrapCollection(): Boolean

    fun autoMergeScript(): Boolean

    fun postmanJson5FormatType(): PostmanJson5FormatType
}