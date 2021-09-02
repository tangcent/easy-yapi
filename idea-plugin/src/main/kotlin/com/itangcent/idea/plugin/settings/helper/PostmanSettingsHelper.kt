package com.itangcent.idea.plugin.settings.helper

import com.google.inject.ImplementedBy
import com.itangcent.idea.plugin.settings.PostmanExportMode
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType

@ImplementedBy(DefaultPostmanSettingsHelper::class)
interface PostmanSettingsHelper {

    fun hasPrivateToken(): Boolean

    fun getPrivateToken(dumb: Boolean = true): String?

    fun getWorkspace(dumb: Boolean = true): String?

    fun wrapCollection(): Boolean

    fun autoMergeScript(): Boolean

    fun postmanJson5FormatType(): PostmanJson5FormatType

    fun postmanExportMode(): PostmanExportMode

    fun getCollectionId(module: String, dumb: Boolean = true): String?

    fun addCollectionId(module: String, collectionId: String)
}