package com.itangcent.easyapi.exporter.channel.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.itangcent.easyapi.exporter.channel.yapi.model.MutableYapiApiDoc
import com.itangcent.easyapi.exporter.channel.yapi.model.MutableYapiHeader
import com.itangcent.easyapi.exporter.channel.yapi.model.MutableYapiQuery
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.mockito.kotlin.*

/**
 * Unit tests for [DefaultYapiApiClient].
 *
 * All HTTP calls are intercepted via a mock [HttpClient] so no real server is needed.
 * Each test configures the mock to return a specific JSON response and then asserts
 * the client's behaviour (caching, deduplication, error propagation, etc.).
 *
 * Extends [EasyApiLightCodeInsightFixtureTestCase] so the client's `project`-dependent
 * services (e.g. [com.itangcent.easyapi.logging.IdeaConsoleProvider]) resolve against
 * the real fixture project, consistent with every other project-service-dependent test.
 */
class DefaultYapiApiClientTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var httpClient: HttpClient
    private lateinit var client: DefaultYapiApiClient

    private val serverUrl = "http://yapi.example.com"
    private val token = "test-token-abc"

    override fun setUp() {
        super.setUp()
        httpClient = mock()
        client = DefaultYapiApiClient(serverUrl, token, httpClient, project = project)
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
    // Helpers for fork-style responses (code/message instead of errcode/errmsg)
    // -------------------------------------------------------------------------

    /** Builds a fork-style success JSON: {"code":0,"message":"成功！",...} */
    private fun forkSuccessJson(data: Any? = null): String {
        val obj = JsonObject().apply {
            addProperty("code", 0)
            addProperty("message", "成功！")
            if (data is JsonObject) add("data", data)
            else if (data is JsonArray) add("data", data)
        }
        return obj.toString()
    }

    private fun forkErrorJson(code: Int = 401, message: String = "token无效"): String =
        """{"code":$code,"message":"$message"}"""

    // -------------------------------------------------------------------------
    // getProjectId
    // -------------------------------------------------------------------------

    fun testGetProjectIdReturnsFailureWhenServerUrlIsBlank() = runBlocking {
        val c = DefaultYapiApiClient("", token, httpClient, project = project)
        val result = c.getProjectId()
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage()!!.contains("server URL"))
    }

    fun testGetProjectIdReturnsFailureWhenTokenIsBlank() = runBlocking {
        val c = DefaultYapiApiClient(serverUrl, "", httpClient, project = project)
        val result = c.getProjectId()
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage()!!.contains("Token"))
    }

    fun testGetProjectIdResolvesFromGetProject() = runBlocking {
        val data = projectDataJson("99")
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(successJson(data)))

        val result = client.getProjectId()
        assertTrue(result.isSuccess)
        assertEquals("99", result.getOrNull())
    }

    fun testGetProjectIdCachesResultOnSecondCall() = runBlocking {
        val data = projectDataJson("99")
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(successJson(data)))

        client.getProjectId()
        client.getProjectId() // second call

        // HTTP should only be called once
        verify(httpClient, times(1)).execute(any())
        Unit
    }

    fun testGetProjectIdFallsBackToListMenuWhenGetProjectReturnsNoData() = runBlocking {
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

    fun testGetProjectIdReturnsFailureWhenBothEndpointsFail() = runBlocking {
        whenever(httpClient.execute(any())).thenReturn(mockResponse(errorJson(), code = 500))

        val result = client.getProjectId()
        assertFalse(result.isSuccess)
    }

    fun testGetProjectIdResolvesViaListMenuWhenForkServerReturnsCode0WithoutData() = runBlocking {
        // Fork server returns {"code":0,"message":"成功！"} with no data from GET_PROJECT
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse("""{"code":0,"message":"成功！"}"""))

        // list_menu returns an item with project_id
        val menuItem = JsonObject().apply { addProperty("project_id", "55") }
        val menuArray = JsonArray().apply { add(menuItem) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_menu") }))
            .thenReturn(mockResponse("""{"code":0,"message":"成功！","data":${menuArray}}"""))

        val result = client.getProjectId()
        assertTrue(result.isSuccess)
        assertEquals("55", result.getOrNull())
    }

    // -------------------------------------------------------------------------
    // Fork-format response handling (code/message instead of errcode/errmsg)
    // -------------------------------------------------------------------------

    fun testGetProjectIdResolvesFromGetProjectWithForkCode0Response() = runBlocking {
        val data = projectDataJson("200")
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(forkSuccessJson(data)))

        val result = client.getProjectId()
        assertTrue(result.isSuccess)
        assertEquals("200", result.getOrNull())
    }

    fun testGetProjectIdResolvesWhenErrcodeIsNonZeroButErrmsgIndicatesSuccess() = runBlocking {
        // Some forks return errcode=200 with errmsg="成功" to mean success
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse("""{"errcode":200,"errmsg":"成功","data":{"_id":"42"}}"""))

        val result = client.getProjectId()
        assertTrue(result.isSuccess)
        assertEquals("42", result.getOrNull())
    }

    fun testGetProjectIdResolvesWhenErrcodeIs200WithNoMessage() = runBlocking {
        // errcode=200 alone is a success code
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse("""{"errcode":200,"data":{"_id":"99"}}"""))

        val result = client.getProjectId()
        assertTrue(result.isSuccess)
        assertEquals("99", result.getOrNull())
    }

    fun testGetProjectIdFailsWhenForkServerReturnsNonZeroCode() = runBlocking {
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(forkErrorJson(401, "token无效")))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_menu") }))
            .thenReturn(mockResponse(forkErrorJson(401, "token无效")))

        val result = client.getProjectId()
        assertFalse(result.isSuccess)
    }

    fun testListCartsWorksWithForkFormatResponses() = runBlocking {
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse(forkSuccessJson(projectDataJson("1"))))

        val cartsArray = JsonArray().apply { add(cartJson(30, "Fork Cart")) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/getCatMenu") }))
            .thenReturn(mockResponse(forkSuccessJson(cartsArray)))

        val result = client.listCarts()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.size)
        assertEquals("Fork Cart", result.getOrNull()!![0].name)
    }

    fun testUploadApiSucceedsWithForkFormatResponses() = runBlocking {
        val emptyList = JsonObject().apply { add("list", JsonArray()) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(forkSuccessJson(emptyList)))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(forkSuccessJson(JsonObject())))

        val result = client.uploadApi(testDoc(), "cat1")
        assertTrue(result.isSuccess)
    }

    fun testUploadApiFailsWhenForkServerReturnsErrorCode() = runBlocking {
        val emptyList = JsonObject().apply { add("list", JsonArray()) }
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(forkSuccessJson(emptyList)))

        whenever(httpClient.execute(argThat { url.contains("/api/interface/save") }))
            .thenReturn(mockResponse(forkErrorJson(500, "save failed")))

        val result = client.uploadApi(testDoc(), "cat1")
        assertFalse(result.isSuccess)
        assertEquals("save failed", result.errorMessage())
    }

    fun testGetProjectIdHandlesResponseWithOnlySuccessMessageAndNoCodeField() = runBlocking {
        // Some forks return just {"message":"成功","data":{...}}
        whenever(httpClient.execute(argThat { url.contains("/api/project/get") }))
            .thenReturn(mockResponse("""{"message":"成功","data":{"_id":"333"}}"""))

        val result = client.getProjectId()
        assertTrue(result.isSuccess)
        assertEquals("333", result.getOrNull())
    }

    // -------------------------------------------------------------------------
    // listCarts
    // -------------------------------------------------------------------------

    fun testListCartsReturnsFailureWhenProjectIdCannotBeResolved() = runBlocking {
        whenever(httpClient.execute(any())).thenReturn(mockResponse(errorJson(), code = 500))
        val result = client.listCarts()
        assertFalse(result.isSuccess)
    }

    fun testListCartsReturnsParsedCarts() = runBlocking {
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

    fun testCreateCartReturnsCreatedCart() = runBlocking {
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

    fun testFindOrCreateCartReturnsExistingCartIdWithoutCreating() = runBlocking {
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

    fun testFindOrCreateCartCreatesCartWhenNotFound() = runBlocking {
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

    fun testListApisReturnsApiArray() = runBlocking {
        val body = apiListJson(apiJson("1", "/api/users", "GET"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val result = client.listApis("cat1")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()!!.size())
    }

    fun testListApisRetriesWithLargerLimitWhenResponseHitsLimit() = runBlocking {
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

    fun testFindExistingApiReturnsIdWhenMatchFound() = runBlocking {
        val body = apiListJson(
            apiJson("id-1", "/api/users", "GET"),
            apiJson("id-2", "/api/users", "POST")
        )
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val id = client.findExistingApi("cat1", "/api/users", "GET")
        assertEquals("id-1", id)
    }

    fun testFindExistingApiIsCaseInsensitiveOnMethod() = runBlocking {
        val body = apiListJson(apiJson("id-1", "/api/users", "get"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val id = client.findExistingApi("cat1", "/api/users", "GET")
        assertEquals("id-1", id)
    }

    fun testFindExistingApiReturnsNullWhenNoMatch() = runBlocking {
        val body = apiListJson(apiJson("id-1", "/api/other", "GET"))
        whenever(httpClient.execute(argThat { url.contains("/api/interface/list_cat") }))
            .thenReturn(mockResponse(body))

        val id = client.findExistingApi("cat1", "/api/users", "GET")
        assertNull(id)
    }

    // -------------------------------------------------------------------------
    // uploadApi
    // -------------------------------------------------------------------------

    fun testUploadApiReturnsSuccessInMockModeBlankServerUrl() = runBlocking {
        val c = DefaultYapiApiClient("", token, httpClient, project = project)
        val result = c.uploadApi(testDoc(), "cat1")
        assertTrue(result.isSuccess)
        verifyNoInteractions(httpClient)
    }

    fun testUploadApiCreatesNewApiWhenNoDuplicateExists() = runBlocking {
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

    fun testUploadApiUpdatesExistingApiWhenDuplicateFound() = runBlocking {
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

    fun testUploadApiReturnsFailureWhenSaveEndpointReturnsError() = runBlocking {
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

    private fun testDoc(path: String = "/api/test", method: String = "GET") = MutableYapiApiDoc.create(
        title = "Test API",
        path = path,
        method = method,
        desc = "desc",
        reqHeaders = listOf(MutableYapiHeader("Content-Type", "application/json")),
        reqQuery = listOf(MutableYapiQuery("id", "1"))
    )
}
