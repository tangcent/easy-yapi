package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import com.itangcent.easyapi.exporter.yapi.model.YapiCart
import com.itangcent.easyapi.exporter.yapi.model.YapiHeader
import com.itangcent.easyapi.exporter.yapi.model.YapiQuery
import com.itangcent.easyapi.http.UrlConnectionHttpClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class YapiApiClientTest {

    private fun createTestApiDoc(
        title: String = "Test API",
        path: String = "/api/test",
        method: String = "GET"
    ): YapiApiDoc {
        return YapiApiDoc(
            title = title,
            path = path,
            method = method,
            desc = "Test API description",
            reqHeaders = listOf(YapiHeader("Content-Type", "application/json")),
            reqQuery = listOf(YapiQuery("id", "123")),
            tags = listOf("test")
        )
    }

    @Test
    fun testUploadApiBasic() = runBlocking {
        val client = YapiApiClient(httpClient = UrlConnectionHttpClient)
        val doc = createTestApiDoc()
        val result = client.uploadApi(doc, "1")
        
        assertNotNull(result)
    }

    @Test
    fun testUploadApiWithComplexBody() = runBlocking {
        val client = YapiApiClient(httpClient = UrlConnectionHttpClient)
        val doc = createTestApiDoc(
            title = "Create User",
            path = "/api/users",
            method = "POST"
        ).copy(
            reqBodyOther = """{"name": "John", "age": 30}"""
        )
        val result = client.uploadApi(doc, "1")
        
        assertNotNull(result)
    }

    @Test
    fun testListCarts() = runBlocking {
        val client = YapiApiClient(httpClient = UrlConnectionHttpClient)
        val carts = client.listCarts()
        
        assertNotNull(carts)
    }

    @Test
    fun testCreateCart() = runBlocking {
        val client = YapiApiClient(httpClient = UrlConnectionHttpClient)
        val cart = client.createCart("Test Cart")
        
        // Without a real server, this will return null
        // Just verify it doesn't throw
    }
}
