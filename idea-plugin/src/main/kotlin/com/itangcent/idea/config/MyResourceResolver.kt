package com.itangcent.idea.config

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.intellij.config.resource.DefaultResourceResolver
import com.itangcent.intellij.config.resource.URLResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.file.LocalFileRepository
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.URL
import java.util.concurrent.TimeUnit

@Singleton
open class MyResourceResolver : DefaultResourceResolver() {

    @Inject
    @Named("projectCacheRepository")
    private lateinit var projectCacheRepository: LocalFileRepository

    private val beanDAO: SqliteDataResourceHelper.ExpiredBeanDAO by lazy {
        val context = ActionContext.getContext()
        val sqliteDataResourceHelper = context!!.instance(SqliteDataResourceHelper::class)
        sqliteDataResourceHelper.getExpiredBeanDAO(
                projectCacheRepository.getOrCreateFile(".url.cache.v2.1.db").path, "DB_BEAN_BINDER")
    }

    override fun createUrlResource(url: String): URLResource {
        return CachedURLResource(URL(url))
    }

    open inner class CachedURLResource(url: URL) : URLResource(url) {

        private fun loadCache(): ByteArray? {
            val key = url.toString().toByteArray(Charsets.UTF_8)
            var valueBytes = beanDAO.get(key)
            if (valueBytes == null) {
                valueBytes = super.inputStream?.readBytes()
                valueBytes?.let { beanDAO.set(key, it, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)) }
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
    }
}
