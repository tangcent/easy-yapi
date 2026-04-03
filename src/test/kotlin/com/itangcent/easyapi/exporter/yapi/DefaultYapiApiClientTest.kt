package com.itangcent.easyapi.exporter.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import com.itangcent.easyapi.exporter.yapi.model.YapiHeader
import com.itangcent.easyapi.exporter.yapi.model.YapiQuery
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Unit tests for [DefaultYapiApiClient].
 *
 * All HTTP calls are intercepted via a mock [HttpClient] so no real server is needed.
 * Each test configures the mock to return a specific JSON response and then asserts
 * the client's behaviour (caching, deduplication, error propagation, etc.).
 */
class DefaultYapiApiClientTest {

    private lateinit var httpClient: HttpClient
    private lateinit var client: DefaultYapiApiClient

    private val serverUrl = "http://yapi.example.com"
    private val token = "test-token-abc"

    @Before
    fun setUp() {
        httpClient = mock()
        client = DefaultYapiApiClient(serverUrl, token, httpClient)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun mockResponse(body: String, code: Int = 200): HttpResponse =
        HttpResponse(code = code, body = body)

    private fun successJson(data: Any? = null): String {
        val obj = JsonObject().apply {
            addProperty("errcode", 0)
            addProperty("errmsg", "成功！")
            if (data is JsonObject) add("data", data)
            else if (data is JsonArray) add("data", data)
        }
        return obj.toString()
    }

    private fun errorJson(errcode: Int = 40011, errmsg: String = "error"): String =
        """{"errcode":$errcode,"errmsg":"$errmsg"}"""

    private fun projectDataJson(id: String = "42"): JsonObject =
        JsonObject().apply { addProperty("_id", id) }

    private fun cartJson(id: Long, name: String): JsonObject =
        JsonObject().apply {
            addProperty("_id", id)
            addProperty("name", name)
        }

    private fun apiJson(id: String, path: String, method: String): JsonObject =
        JsonObject().apply {
            addProperty("_id", id)
            addProperty("path", path)
            addProperty("method", method)
        }

    private fun apiListJson(vararg apis: JsonObject): String {
        val list = JsonArray().apply { apis.forEach { add(it) } }
        val data = JsonObject().apply { add("list", list) }
        return successJson(data)
    }

    // -------------------------------------------------------------------------
    // getProjectId
    // -------------------------------------------------------------------------

    @Test
    fun `getProjectId returns failure when serverUrl is blank`() = runBlocking {
        val c = DefaultYapiApiClient("", token, httpClient)
        val result = c.getProjectId()
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage()!!.contains("server URL"))
    }

    @Test
    fun `getProjectId returns failure when token is blank`() = runBlocking {
        val c = DefaultYapiApiClient(serverUrl, "", httpClient)
        val result = c.getProjectId()
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage()!!.contains("Token"))
    }

    @Test
    fun `getProjectId resolves from GET_PROJECT`() = runBlocking {
        val data = projectDataJson("99")
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(successJson(data)))

        val result = client.getProjectId()
        assertTrue(result.isSuccess)
        assertEquals("99", result.getOrNull())
    }

    @Test
    fun `getProjectId caches result on second call`() = runBlocking {
        val data = projectDataJson("99")
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(successJson(data)))

        client.getProjectId()
        client.getProjectId() // second call

        // HTTP should only be called once
        verify(httpClient, times(1)).execute(any())
        Unit
    }

    @Test
    fun `getProjectId falls back to list_menu when GET_PROJECT returns no data`() = runBlocking {
        // GET_PROJECT returns success but no _id
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse("""{"errcode":0,"errmsg":"成功！","data":{}}"""))

        // list_menu returns an item with project_id
        val menuItem = JsonObject().apply { addProperty("project_id", "77") }
        val menuArray = JsonArray().apply { add(menuItem) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_menu") }))
            .thenReturn(mockResponse(successJson(menuArray)))

        val result = client.getProjectId()
        assertTrue(result.isSuccess)
        assertEquals("77", result.getOrNull())
    }

    @Test
    fun `getProjectId returns failure when both endpoints fail`() = runBlocking {
        whenever(httpClient.execute(any())).thenReturn(mockResponse(errorJson(), code = 500))

        val result = client.getProjectId()
        assertFalse(result.isSuccess)
    }

    // -------------------------------------------------------------------------
    // parseResponse
    // -------------------------------------------------------------------------

    @Test
    fun `parseResponse returns failure for non-200 status`() {
        val res = mockResponse("{}", code = 500)
        val result = client.parseResponse(res) { "data" }
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage()!!.contains("500"))
    }

    @Test
    fun `parseResponse returns failure for non-zero errcode`() {
        val res = mockResponse(errorJson(40011, "token invalid"))
        val result = client.parseResponse(res) { "data" }
        assertFalse(result.isSuccess)
        assertEquals("token invalid", result.errorMessage())
    }

    @Test
    fun `parseResponse returns failure for invalid JSON`() {
        val res = mockResponse("not-json")
        val result = client.parseResponse(res) { "data" }
        assertFalse(result.isSuccess)
    }

    @Test
    fun `parseResponse returns failure when handler returns null`() {
        val res = mockResponse(successJson(JsonObject()))
        val result = client.parseResponse<String>(res) { null }
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage()!!.contains("Empty data"))
    }

    @Test
    fun `parseResponse returns success with extracted data`() {
        val data = JsonObject().apply { addProperty("_id", "123") }
        val res = mockResponse(successJson(data))
        val result = client.parseResponse(res) { it.getAsJsonObject("data")?.get("_id")?.asString }
        assertTrue(result.isSuccess)
        assertEquals("123", result.getOrNull())
    }

    // -------------------------------------------------------------------------
    // listCarts
    // -------------------------------------------------------------------------

    @Test
    fun `listCarts returns failure when project ID cannot be resolved`() = runBlocking {
        whenever(httpClient.execute(any())).thenReturn(mockResponse(errorJson(), code = 500))
        val result = client.listCarts()
        assertFalse(result.isSuccess)
    }

    @Test
    fun `listCarts returns parsed carts`() = runBlocking {
        // stub getProjectId
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(successJson(projectDataJson("1"))))

        val cartsArray = JsonArray().apply {
            add(cartJson(10, "Cart A"))
            add(cartJson(20, "Cart B"))
        }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/getCatMenu") }))
            .thenReturn(mockResponse(successJson(cartsArray)))

        val result = client.listCarts()
        assertTrue(result.isSuccess)
        val carts = result.getOrNull()!!
        assertEquals(2, carts.size)
        assertEquals("Cart A", carts[0].name)
        assertEquals(10L, carts[0].id)
        assertEquals("Cart B", carts[1].name)
    }

    // -------------------------------------------------------------------------
    // createCart
    // -------------------------------------------------------------------------

    @Test
    fun `createCart returns created cart`() = runBlocking {
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(successJson(projectDataJson("1"))))

        val newCart = cartJson(99, "New Cart")
        val data = JsonObject().apply {
            addProperty("_id", 99L)
            addProperty("name", "New Cart")
        }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/add_cat") }))
            .thenReturn(mockResponse(successJson(data)))

        val result = client.createCart("New Cart", "desc")
        assertTrue(result.isSuccess)
        assertEquals("New Cart", result.getOrNull()!!.name)
        assertEquals(99L, result.getOrNull()!!.id)
    }

    // -------------------------------------------------------------------------
    // findOrCreateCart
    // -------------------------------------------------------------------------

    @Test
    fun `findOrCreateCart returns existing cart id without creating`() = runBlocking {
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(successJson(projectDataJson("1"))))

        val cartsArray = JsonArray().apply { add(cartJson(55, "Existing")) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/getCatMenu") }))
            .thenReturn(mockResponse(successJson(cartsArray)))

        val result = client.findOrCreateCart("Existing")
        assertTrue(result.isSuccess)
        assertEquals("55", result.getOrNull())
        // add_cat should NOT be called
        verify(httpClient, never()).execute(argThat { url.contains("/api/interface/add_cat") })
        Unit
    }

    @Test
    fun `findOrCreateCart creates cart when not found`() = runBlocking {
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(successJson(projectDataJson("1"))))

        // listCarts returns empty
        whenever(httpClient.execute(argThat { url.contains("/api/interface/getCatMenu") }))
            .thenReturn(mockResponse(successJson(JsonArray())))

        val newCartData = JsonObject().apply {
            addProperty("_id", 88L)
            addProperty("name", "New")
        }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/add_cat") }))
            .thenReturn(mockResponse(successJson(newCartData)))

        val result = client.findOrCreateCart("New")
        assertTrue(result.isSuccess)
        assertEquals("88", result.getOrNull())
    }

    // -------------------------------------------------------------------------
    // listApis
    // -------------------------------------------------------------------------

    @Test
    fun `listApis returns api array`() = runBlocking {
        val body = apiListJson(apiJson("1", "/api/users", "GET"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val result = client.listApis("cat1")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.size())
    }

    @Test
    fun `listApis retries with larger limit when response hits limit`() = runBlocking {
        // First call returns exactly 1000 items (hits limit)
        val fullPage = JsonArray().apply { repeat(1000) { add(apiJson(it.toString(), "/p/$it", "GET")) } }
        val fullData = JsonObject().apply { add("list", fullPage) }
        whenever(httpClient.execute(argThat { url.contains("limit=1000") }))
            .thenReturn(mockResponse(successJson(fullData)))

        // Second call (with bumped limit) returns fewer items
        val partialPage = JsonArray().apply { add(apiJson("x", "/p/x", "GET")) }
        val partialData = JsonObject().apply { add("list", partialPage) }
        whenever(httpClient.execute(argThat { !url.contains("limit=1000") && url.contains("limit=") }))
            .thenReturn(mockResponse(successJson(partialData)))

        val result = client.listApis("cat1")
        assertTrue(result.isSuccess)
        // Should have retried and returned the partial page
        assertEquals(1, result.getOrNull()!!.size())
    }

    // -------------------------------------------------------------------------
    // findExistingApi
    // -------------------------------------------------------------------------

    @Test
    fun `findExistingApi returns id when match found`() = runBlocking {
        val body = apiListJson(
            apiJson("id-1", "/api/users", "GET"),
            apiJson("id-2", "/api/users", "POST")
        )
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val id = client.findExistingApi("cat1", "/api/users", "GET")
        assertEquals("id-1", id)
    }

    @Test
    fun `findExistingApi is case-insensitive on method`() = runBlocking {
        val body = apiListJson(apiJson("id-1", "/api/users", "get"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val id = client.findExistingApi("cat1", "/api/users", "GET")
        assertEquals("id-1", id)
    }

    @Test
    fun `findExistingApi returns null when no match`() = runBlocking {
        val body = apiListJson(apiJson("id-1", "/api/other", "GET"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val id = client.findExistingApi("cat1", "/api/users", "GET")
        assertNull(id)
    }

    // -------------------------------------------------------------------------
    // uploadApi
    // -------------------------------------------------------------------------

    @Test
    fun `uploadApi returns success in mock mode (blank serverUrl)`() = runBlocking {
        val c = DefaultYapiApiClient("", token, httpClient)
        val result = c.uploadApi(testDoc(), "cat1")
        assertTrue(result.isSuccess)
        verifyNoInteractions(httpClient)
    }

    @Test
    fun `uploadApi creates new api when no duplicate exists`() = runBlocking {
        // listApis returns empty — no duplicate
        val emptyList = JsonObject().apply { add("list", JsonArray()) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(successJson(emptyList)))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(successJson(JsonObject())))

        val result = client.uploadApi(testDoc("/api/users", "GET"), "cat1")
        assertTrue(result.isSuccess)

        // save should be called once, without an "id" field (new insert, not update)
        val captor = argumentCaptor<HttpRequest>()
        verify(httpClient, atLeastOnce()).execute(captor.capture())
        val saveCall = captor.allValues.first { it.url.contains("/api/interface/save") }
        assertFalse("Should not contain existing id", saveCall.body?.contains("\"id\":") == true)
    }

    @Test
    fun `uploadApi updates existing api when duplicate found`() = runBlocking {
        // listApis returns one matching api
        val body = apiListJson(apiJson("existing-id", "/api/users", "GET"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(successJson(JsonObject())))

        val result = client.uploadApi(testDoc("/api/users", "GET"), "cat1")
        assertTrue(result.isSuccess)

        // save body should contain the existing id
        val captor = argumentCaptor<HttpRequest>()
        verify(httpClient, atLeastOnce()).execute(captor.capture())
        val saveCall = captor.allValues.first { it.url.contains("/api/interface/save") }
        assertTrue("Should contain existing id", saveCall.body?.contains("existing-id") == true)
    }

    @Test
    fun `uploadApi returns failure when save endpoint returns error`() = runBlocking {
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(apiListJson()))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(errorJson(500, "save failed")))

        val result = client.uploadApi(testDoc(), "cat1")
        assertFalse(result.isSuccess)
        assertEquals("save failed", result.errorMessage())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun testDoc(path: String = "/api/test", method: String = "GET") = YapiApiDoc(
        title = "Test API",
        path = path,
        method = method,
        desc = "desc",
        reqHeaders = listOf(YapiHeader("Content-Type", "application/json")),
        reqQuery = listOf(YapiQuery("id", "1"))
    )
}
