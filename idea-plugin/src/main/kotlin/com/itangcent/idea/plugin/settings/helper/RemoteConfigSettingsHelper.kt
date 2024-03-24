package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.idea.sqlite.get
import com.itangcent.idea.sqlite.set
import com.itangcent.intellij.config.ConfigContent
import com.itangcent.intellij.config.resource.ResourceResolver
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.logger.Logger
import java.util.*

@Singleton
class RemoteConfigSettingsHelper {
    @Inject
    private lateinit var logger: Logger

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

    fun remoteConfigContent(): List<ConfigContent> {
        return settingBinder.read().remoteConfig
            .parse()
            .filter { it.first }
            .map { loadConfig(it.second) }
    }

    fun loadConfig(url: String): ConfigContent {
        return ConfigContent(
            content = beanDAO.get(url) ?: refreshConfig(url) ?: "",
            type = getContentTypeFromUrl(url)
        )
    }

    private fun getContentTypeFromUrl(url: String): String {
        return url.substringBefore('?').substringAfterLast('.').ifBlank { "properties" }
    }

    fun refreshConfig(url: String): String? {
        val resource = try {
            resourceResolver.resolve(url)
        } catch (e: Exception) {
            logger.error("failed to load config: $url")
            null
        }
        return resource?.content?.let {
            beanDAO.set(url, it)
            it
        }
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

