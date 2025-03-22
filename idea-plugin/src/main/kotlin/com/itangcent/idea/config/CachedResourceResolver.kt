package com.itangcent.idea.config

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.TimeSpanUtils
import com.itangcent.idea.plugin.api.cache.ProjectCacheRepository
import com.itangcent.idea.plugin.settings.helper.HttpSettingsHelper
import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.resource.DefaultResourceResolver
import com.itangcent.intellij.config.resource.URLResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.callWithTimeout
import com.itangcent.intellij.logger.Logger
import com.itangcent.utils.GiteeSupport
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.TimeUnit

@Singleton
open class CachedResourceResolver : DefaultResourceResolver() {
    companion object : Log() {
        private const val URL_CACHE_EXPIRE = "url.cache.expire"
    }

    @Inject
    private lateinit var projectCacheRepository: ProjectCacheRepository

    @Inject
    private lateinit var configReader: ConfigReader

    @Inject
    private lateinit var httpSettingsHelper: HttpSettingsHelper

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var logger: Logger

    private val beanDAO: SqliteDataResourceHelper.ExpiredBeanDAO by lazy {
        val sqliteDataResourceHelper = actionContext.instance(SqliteDataResourceHelper::class)
        sqliteDataResourceHelper.getExpiredBeanDAO(
            projectCacheRepository.getOrCreateFile(".url.cache.v2.1.db").path, "DB_BEAN_BINDER"
        )
    }

    override fun createUrlResource(url: String): URLResource {
        return CachedURLResource(URL(url))
    }

    open inner class CachedURLResource(url: URL) : URLResource(url) {

        private val urlCacheExpire: Long
            get() = (configReader.first(URL_CACHE_EXPIRE)?.let { str -> TimeSpanUtils.parse(str) }
                ?: TimeUnit.HOURS.toMillis(2))

        private val timeOut: Int
            get() = httpSettingsHelper.httpTimeOut(TimeUnit.MILLISECONDS)

        private fun loadCache(): ByteArray? {
            val rawUrl = url.toString()
            val key = rawUrl.toByteArray(Charsets.UTF_8)
            var valueBytes = beanDAO.get(key)
            if (valueBytes == null) {
                LOG.debug("read:$url")
                if (!httpSettingsHelper.checkTrustUrl(rawUrl, false)) {
                    logger.warn("[access forbidden] read:$url")
                    return byteArrayOf()
                }
                try {
                    valueBytes = actionContext.callWithTimeout(timeOut.toLong()) {
                        super.inputStream?.use { it.readBytes() }
                    }
                } catch (e: Exception) {
                    if (url.host.contains("githubusercontent.com")) {
                        GiteeSupport.convertUrlFromGithub(rawUrl)?.let { giteeUrl ->
                            if (e is SocketTimeoutException) {
                                logger.error(
                                    "failed fetch:[$url]\n" +
                                            "Maybe you can use [$giteeUrl] instead"
                                )
                            } else {
                                logger.traceError(
                                    "failed fetch:[$url]\n" +
                                            "Maybe you can use [$giteeUrl] instead", e
                                )
                            }
                            return null
                        }
                    }
                    logger.traceError("failed fetch:[$url]", e)
                }
                valueBytes?.let {
                    beanDAO.set(
                        key, it, System.currentTimeMillis() +
                                urlCacheExpire
                    )
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
            connection.connectTimeout = timeOut
            connection.readTimeout = timeOut
        }
    }
}
