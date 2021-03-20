package com.itangcent.idea.plugin.api.cache

import com.google.inject.ImplementedBy

@ImplementedBy(DefaultFileApiCacheRepository::class)
interface FileApiCacheRepository {

    fun getFileApiCache(filePath: String): FileApiCache?

    fun saveFileApiCache(filePath: String, fileApiCache: FileApiCache)

}