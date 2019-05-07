package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.intellij.file.LocalFileRepository

class DefaultFileApiCacheRepository : FileApiCacheRepository {

    @Inject
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

    private var dbBeanBinderFactory: DbBeanBinderFactory<FileApiCache>? = null

    private fun init() {
        if (dbBeanBinderFactory == null) {
            synchronized(this) {
                dbBeanBinderFactory = DbBeanBinderFactory(projectCacheRepository!!.getOrCreateFile(".api.cache.db").path)
                { FileApiCache() }
            }
        }
    }

    override fun getFileApiCache(filePath: String): FileApiCache? {
        init()
        return try {
            val fileApiCache = dbBeanBinderFactory!!.getBeanBinder(filePath).read()
            when {
                fileApiCache.file == null -> null
                else -> fileApiCache
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun saveFileApiCache(filePath: String, fileApiCache: FileApiCache) {
        init()
        dbBeanBinderFactory!!.getBeanBinder(filePath).save(fileApiCache)
    }
}