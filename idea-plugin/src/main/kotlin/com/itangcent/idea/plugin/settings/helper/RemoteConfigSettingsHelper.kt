package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.intellij.config.resource.ResourceResolver
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.file.LocalFileRepository
import java.util.*

@Singleton
class RemoteConfigSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    @Inject
    private lateinit var resourceResolver: ResourceResolver

    @Inject
    private lateinit var localFileRepository: LocalFileRepository

    @Inject
    private lateinit var actionContext: ActionContext

    private val beanDAO: SqliteDataResourceHelper.SimpleBeanDAO by lazy {
        val sqliteDataResourceHelper = actionContext.instance(SqliteDataResourceHelper::class)
        sqliteDataResourceHelper.getSimpleBeanDAO(
            localFileRepository.getOrCreateFile(".remote.cache.v1.0.db").path, "DB_BEAN_BINDER"
        )
    }

    fun remoteConfigContent(): String {
        return settingBinder.read().remoteConfig
            .parse()
            .filter { it.first }
            .map { it.second }
            .distinct()
            .joinToString("\n") { loadConfig(it) }
    }

    fun loadConfig(url: String): String {
        var bytes = beanDAO.get(url.toByteArray())
        if (bytes == null) {
            resourceResolver.resolve(url).bytes?.let {
                beanDAO.set(url.toByteArray(), it)
                bytes = it
            }
        }
        return bytes?.toString(Charsets.UTF_8) ?: ""
    }
}

typealias RemoteConfig = LinkedList<Pair<Boolean, String>>

fun Array<String>.parse(): RemoteConfig {
    return LinkedList(this
        .map {
            if (it.startsWith('!')) {
                false to it.substring(1)
            } else {
                true to it
            }
        })
}

fun RemoteConfig.toConfig(): Array<String> {
    return this
        .map { (selected, text) ->
            if (selected) {
                text
            } else {
                "!$text"
            }
        }.toTypedArray()
}

fun RemoteConfig.setSelected(index: Int, selected: Boolean) {
    this[index] = (selected to this[index].second)
}

