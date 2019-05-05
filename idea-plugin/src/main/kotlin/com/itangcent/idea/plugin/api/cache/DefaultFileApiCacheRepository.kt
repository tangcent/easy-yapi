package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.common.utils.FileUtils
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.binder.DbBeanBinder
import com.itangcent.intellij.extend.lazy
import com.itangcent.intellij.file.BeanBinder
import com.itangcent.intellij.file.LocalFileRepository

class DefaultFileApiCacheRepository : FileApiCacheRepository {

    @Inject
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

    private var emptyFileBinder: BeanBinder<EmptyApiCache>? = null

    private var emptyApiCache: EmptyApiCache? = null

    private fun init() {
        if (emptyFileBinder == null) {
            emptyFileBinder = DbBeanBinder(projectCacheRepository!!.getOrCreateFile(".easy.empty.files.db").path,
                    EmptyApiCache::class.simpleName!!) { EmptyApiCache() }.lazy()
            emptyApiCache = emptyFileBinder!!.read()
        }
    }

    override fun getFileApiCache(filePath: String): FileApiCache? {
        init()

        try {
            val fileApiCache = emptyApiCache!!.getFileApiCache(filePath)
            if (fileApiCache != null) {
                return fileApiCache
            }

            val now = System.currentTimeMillis()
            val file = projectCacheRepository!!.getFile(".$filePath.cache")
            if (file == null || file.lastModified() >= now) {
                return null
            }
            return GsonUtils.fromJson(FileUtils.read(file), FileApiCache::class)
        } catch (e: Exception) {
            return null
        }
    }

    override fun saveFileApiCache(filePath: String, fileApiCache: FileApiCache) {
        init()

        if (fileApiCache.requests.isNullOrEmpty()) {
            emptyApiCache!![filePath] = "${System.currentTimeMillis()}#${fileApiCache.md5}"
            emptyFileBinder!!.save(emptyApiCache!!)
            projectCacheRepository!!.deleteFile(".$filePath.cache")
        } else {
            FileUtils.write(projectCacheRepository!!.getOrCreateFile(".$filePath.cache"),
                    GsonUtils.toJson(fileApiCache))
            if (emptyApiCache!!.remove(filePath) != null) {
                emptyFileBinder!!.save(emptyApiCache!!)
            }
        }
    }

    class EmptyApiCache : HashMap<String, String>() {
        fun getFileApiCache(filePath: String): FileApiCache? {
            val lastModifiedAndMd5 = get(filePath) ?: return null
            val fileApiCache = FileApiCache()
            fileApiCache.file = filePath
            fileApiCache.lastModified = lastModifiedAndMd5.substringBefore("#", "0").toLong()
            fileApiCache.md5 = lastModifiedAndMd5.substringAfter("#", "")
            return fileApiCache
        }
    }
}