package com.itangcent.idea.plugin.api.cache

import com.google.inject.Inject
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.idea.plugin.api.export.*
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.assertEquals

/**
 * Test case of [FileApiCacheRepository]
 */
internal class FileApiCacheRepositoryTest : AdvancedContextTest() {

    @Inject
    private lateinit var fileApiCacheRepository: FileApiCacheRepository

    @Inject
    private lateinit var requestHelper: RequestHelper

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun testFileApiCacheRepository() {
        val filePath = "/a/b.java"

        val fileApiCache = FileApiCache()
        fileApiCache.file = filePath
        fileApiCache.md5 = "1234567"
        fileApiCache.lastModified = System.currentTimeMillis()
        val request = Request()
        request.path = URL.of("test")
        request.method = "GET"
        requestHelper.addHeader(request, "token", "123456")
        requestHelper.addPathParam(request, "user", "tang")
        requestHelper.addParam(request, "q", "name", "condition")
        requestHelper.addFormParam(request, "q", "name", "condition")
        requestHelper.setContentType(request, "application/json")
        request.body = ""
        request.bodyAttr = "hello"
        request.name = "demo"
        request.desc = "a demo api"

        fileApiCache.requests = listOf(RequestWithKey("demo-api", request))

        fileApiCacheRepository.saveFileApiCache(filePath, fileApiCache)
        val apiCache = fileApiCacheRepository.getFileApiCache(filePath)!!
        assertEquals(fileApiCache, apiCache)
        assertEquals(request, apiCache.requests!!.first().request())

    }
}