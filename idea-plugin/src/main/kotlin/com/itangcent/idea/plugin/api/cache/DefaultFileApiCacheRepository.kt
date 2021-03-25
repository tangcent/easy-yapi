package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.itangcent.common.logger.traceError
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.logger.Logger

@Singleton
class DefaultFileApiCacheRepository : FileApiCacheRepository {

    @Inject
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

    @Inject
    private lateinit var logger: Logger

    private var dbBeanBinderFactory: DbBeanBinderFactory<FileApiCache>? = null

    private fun init() {
        if (dbBeanBinderFactory == null) {
            synchronized(this) {
                dbBeanBinderFactory = DbBeanBinderFactory(projectCacheRepository!!.getOrCreateFile(".api.cache.v1.1.db").path)
                { NULL_FILE_API_CACHE }
            }
        }
    }

    override fun getFileApiCache(filePath: String): FileApiCache? {
        init()
        return try {
            val fileApiCache = dbBeanBinderFactory!!.getBeanBinder(filePath).read()
            when {
                fileApiCache === NULL_FILE_API_CACHE -> null
                fileApiCache.file == null -> null
                else -> fileApiCache
            }
        } catch (e: Exception) {
            logger.traceError("failed getFileApiCache of [$filePath]", e)
            null
        }
    }

    override fun saveFileApiCache(filePath: String, fileApiCache: FileApiCache) {
        init()
        dbBeanBinderFactory!!.getBeanBinder(filePath).save(fileApiCache)
    }

    companion object {
        val NULL_FILE_API_CACHE = FileApiCache()
    }
}