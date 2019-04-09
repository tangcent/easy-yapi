package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.common.utils.FileUtils
import com.itangcent.common.utils.GsonUtils
import com.itangcent.intellij.file.LocalFileRepository

class DefaultFileApiCacheRepository : FileApiCacheRepository {

    @Inject
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

//    private var indexFileBinder: FileBeanBinder<IndexInfo>? = null

//    private var indexInfo: IndexInfo? = null

    override fun getFileApiCache(filePath: String): FileApiCache? {
        try {
            val now = System.currentTimeMillis()
            val file = projectCacheRepository!!.getFile(".$filePath.cache")
            if (file.lastModified() >= now) {
                return null
            }
            return GsonUtils.fromJson(FileUtils.read(file), FileApiCache::class)
        } catch (e: Exception) {
            return null
        }
    }

    override fun saveFileApiCache(filePath: String, fileApiCache: FileApiCache) {
        FileUtils.write(projectCacheRepository!!.getFile(".$filePath.cache"),
                GsonUtils.toJson(fileApiCache))
    }
//
//    fun getCacheFile(filePath: String) {
//        val hashCode = filePath.hashCode()
//    }
//
//    fun getIndexInfo(): IndexInfo {
//        if (indexInfo == null) {
//            indexFileBinder = FileBeanBinder(projectCacheRepository.getFile(".project_cache_index"),
//                    IndexInfo::class)
//            indexInfo = indexFileBinder!!.read()
//        }
//        return indexInfo ?: IndexInfo()
//    }
//
//    fun saveIndexInfo(indexInfo: IndexInfo) {
//        indexFileBinder!!.save(indexInfo)
//    }
//
//    class IndexInfo {
//
//        private var lastIndex: Int? = null
//
//        private var indexes: HashMap<Int, ArrayList<Int>>? = null
//
//        fun findIndex(hashcode: Int): Int? {
//            return indexes.entries
//                    .filter { it.value.contains(hashcode) }
//                    .map { it.key }
//                    .firstOrNull(
//        }
//
//        fun updateIndex(hashcode: Int, index: Int) {
//            if (indexes == null) {
//                indexes = HashMap()
//            }
//            indexes!!.values
//                    .asSequence()
//                    .filter { it.contains(hashcode) }
//                    .forEach { it.remove(hashcode) }
//            indexes!!.computeIfAbsent(index) { ArrayList() }
//                    .add(hashcode)
//        }
//
//    }
}

typealias FileApiCacheIndo = List<FileApiCache>