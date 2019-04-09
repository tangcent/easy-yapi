package com.itangcent.idea.plugin.api.cache

interface FileApiCacheRepository {

    fun getFileApiCache(filePath: String): FileApiCache?

    fun saveFileApiCache(filePath: String, fileApiCache: FileApiCache)

}