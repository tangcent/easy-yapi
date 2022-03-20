package com.itangcent.idea.config

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.itangcent.common.utils.TimeSpanUtils
import com.itangcent.idea.plugin.settings.helper.HttpSettingsHelper
import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.resource.DefaultResourceResolver
import com.itangcent.intellij.config.resource.URLResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.logger.Logger
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.TimeUnit

@Singleton
open class CachedResourceResolver : DefaultResourceResolver() {

    @Inject(optional = true)
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

    @Inject(optional = true)
    private val localFileRepository: LocalFileRepository? = null

    @Inject
    private lateinit var configReader: ConfigReader

    @Inject
    private lateinit var httpSettingsHelper: HttpSettingsHelper

    @Inject
    protected val logger: Logger? = null

    private val beanDAO: SqliteDataResourceHelper.ExpiredBeanDAO by lazy {
        val context = ActionContext.getContext()
        val sqliteDataResourceHelper = context!!.instance(SqliteDataResourceHelper::class)
        sqliteDataResourceHelper.getExpiredBeanDAO(
                (projectCacheRepository
                        ?: localFileRepository)!!.getOrCreateFile(".url.cache.v2.1.db").path, "DB_BEAN_BINDER")
    }

    override fun createUrlResource(url: String): URLResource {
        return CachedURLResource(URL(url))
    }

    open inner class CachedURLResource(url: URL) : URLResource(url) {

        private fun loadCache(): ByteArray? {
            val key = url.toString().toByteArray(Charsets.UTF_8)
            var valueBytes = beanDAO.get(key)
            if (valueBytes == null) {
                LOG.debug("read:$url")
                if (!httpSettingsHelper.checkTrustUrl(url.toString(), false)) {
                    logger?.warn("[access forbidden] read:$url")
                    return byteArrayOf()
                }
                valueBytes = super.inputStream?.use { it.readBytes() }
                valueBytes?.let {
                    beanDAO.set(key, it, System.currentTimeMillis() +
                            (configReader.first(URL_CACHE_EXPIRE)?.let { str -> TimeSpanUtils.parse(str) }
                                    ?: TimeUnit.HOURS.toMillis(2)))
                }
            }
            return valueBytes
        }

        override val inputStream: InputStream?
            get() {
                val valueBytes = loadCache()
                return valueBytes?.let { ByteArrayInputStream(it) }
            }

        override val bytes: ByteArray?
            get() = loadCache()

        override val content: String?
            get() = loadCache()?.let { String(it, Charsets.UTF_8) }

        override fun onConnection(connection: URLConnection) {
            ActionContext.getContext()?.instance(HttpSettingsHelper::class)
                    ?.let {
                        connection.connectTimeout = it.httpTimeOut(TimeUnit.MILLISECONDS)
                    }
        }
    }
}

private const val URL_CACHE_EXPIRE = "url.cache.expire"
private val LOG = com.intellij.openapi.diagnostic.Logger.getInstance(CachedResourceResolver::class.java)