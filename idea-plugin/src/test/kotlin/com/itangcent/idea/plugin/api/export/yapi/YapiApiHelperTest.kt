package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.inject.Inject
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Test case for YapiApiHelper extension functions
 */
class YapiApiHelperTest : AdvancedContextTest() {

    @Inject
    private lateinit var yapiApiHelper: YapiApiHelper

    private lateinit var mockHelper: MockYapiApiHelper

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        mockHelper = MockYapiApiHelper()
        builder.bind(YapiApiHelper::class) { it.toInstance(mockHelper) }
    }

    @Test
    fun testListApis() {
        // Test the listApis extension function that calls the primary function with null limit
        mockHelper.listApisResult = GsonUtils.parseToJsonTree(
            """[
            {"path": "/api/v1/users", "method": "GET"},
            {"path": "/api/v1/users", "method": "POST"}
        ]"""
        )!!.asJsonArray

        val result = yapiApiHelper.listApis("test-token", "test-catId")
        assertEquals(2, result?.size())
        assertEquals("GET", result?.get(0)?.asJsonObject?.get("method")?.asString)
        assertEquals("POST", result?.get(1)?.asJsonObject?.get("method")?.asString)

        // Verify the correct function was called with null limit
        assertEquals("test-token", mockHelper.lastToken)
        assertEquals("test-catId", mockHelper.lastCatId)
        assertNull(mockHelper.lastLimit)
    }

    @Test
    fun testExisted() {
        // Test when API exists
        // Set up mocks for testing with no catid (calls findExistApi by path/method)
        mockHelper.projectIdResult = "test-project-id"
        mockHelper.cartsResult = listOf(mapOf("_id" to "test-cart-id"))

        // Set up the mock response for listApis with the matching API
        mockHelper.listApisForCatId = mutableMapOf(
            "test-cart-id" to GsonUtils.parseToJsonTree(
                """[
                {"path": "/api/v1/users", "method": "GET", "_id": "123"}
                ]"""
            )!!.asJsonArray
        )

        val apiInfo = hashMapOf<String, Any?>(
            "path" to "/api/v1/users",
            "method" to "GET",
            "token" to "test-token"
        )

        assertTrue(yapiApiHelper.existed(apiInfo))

        // Test when API doesn't exist
        mockHelper.listApisForCatId["test-cart-id"] = GsonUtils.parseToJsonTree(
            """[
            {"path": "/api/different", "method": "GET", "_id": "456"}
            ]"""
        )!!.asJsonArray

        assertFalse(yapiApiHelper.existed(apiInfo))
    }

    @Test
    fun testFindExistApi() {
        // Setup test data with catId
        val apiInfo = hashMapOf<String, Any?>(
            "path" to "/api/v1/users",
            "method" to "GET",
            "token" to "test-token",
            "catid" to "test-catId"
        )

        // Set up mock with matching API
        mockHelper.listApisResult = GsonUtils.parseToJsonTree(
            """[
            {"path": "/api/v1/users", "method": "GET", "_id": "123"},
            {"path": "/api/v1/posts", "method": "GET", "_id": "456"}
        ]"""
        )!!.asJsonArray

        val result = yapiApiHelper.findExistApi(apiInfo)
        assertNotNull(result)
        assertEquals("123", result["_id"].asString)

        // Test when no matches are found
        val apiInfoNoMatch = hashMapOf<String, Any?>(
            "path" to "/api/v1/comments",
            "method" to "GET",
            "token" to "test-token",
            "catid" to "test-catId"
        )

        assertNull(yapiApiHelper.findExistApi(apiInfoNoMatch))

        // Test without catId, which calls the other findExistApi implementation
        val apiInfoNoCatId = hashMapOf<String, Any?>(
            "path" to "/api/v1/users",
            "method" to "GET",
            "token" to "test-token"
        )

        // Set up mocks for the findExistApi(token, path, method) call path
        mockHelper.projectIdResult = "test-project-id"
        mockHelper.cartsResult = listOf(mapOf("_id" to "cart-id1"))
        mockHelper.listApisForCatId = mutableMapOf(
            "cart-id1" to GsonUtils.parseToJsonTree(
                """[
                {"path": "/api/v1/users", "method": "GET", "_id": "123"}
                ]"""
            )!!.asJsonArray
        )

        val resultNoCatId = yapiApiHelper.findExistApi(apiInfoNoCatId)
        assertNotNull(resultNoCatId)
        assertEquals("123", resultNoCatId["_id"].asString)
    }

    @Test
    fun testFindExistApiWithPathAndMethod() {
        // Setup
        mockHelper.projectIdResult = "test-project"
        mockHelper.cartsResult = listOf(
            mapOf("_id" to "cat1"),
            mapOf("_id" to "cat2")
        )

        // First category has the API
        mockHelper.listApisForCatId = mutableMapOf(
            "cat1" to GsonUtils.parseToJsonTree(
                """[
                {"path": "/api/v1/users", "method": "GET", "_id": "123"},
                {"path": "/api/v1/posts", "method": "POST", "_id": "456"}
            ]"""
            )!!.asJsonArray
        )

        // Second category doesn't have matching API
        mockHelper.listApisForCatId["cat2"] = GsonUtils.parseToJsonTree(
            """[
            {"path": "/api/v1/comments", "method": "GET", "_id": "789"}
        ]"""
        )!!.asJsonArray

        // Test when API is found in first category
        val result = yapiApiHelper.findExistApi("test-token", "/api/v1/users", "GET")
        assertNotNull(result)
        assertEquals("123", result["_id"].asString)

        // Test when API is not found in any category
        assertNull(yapiApiHelper.findExistApi("test-token", "/api/not/exists", "POST"))

        // Test with null projectId
        mockHelper.projectIdResult = null
        assertNull(yapiApiHelper.findExistApi("invalid-token", "/api/v1/users", "GET"))

        // Test with null carts
        mockHelper.projectIdResult = "test-project"
        mockHelper.cartsResult = null
        assertNull(yapiApiHelper.findExistApi("test-token", "/api/v1/users", "GET"))
    }

    @Test
    fun testGetCartForFolder() {
        // Test when cart exists
        val folder = Folder("TestFolder", "TestDescription")

        mockHelper.findCartResult = "existing-cart-id"

        val cartInfo = yapiApiHelper.getCartForFolder(folder, "test-token")
        assertNotNull(cartInfo)
        assertEquals("existing-cart-id", cartInfo.cartId)
        assertEquals("TestFolder", cartInfo.cartName)
        assertEquals("test-token", cartInfo.privateToken)

        // Test when cart doesn't exist and needs to be created
        mockHelper.findCartResult = null
        mockHelper.addCartResult = true
        mockHelper.findCartSecondCallResult = "new-cart-id"

        val newCartInfo = yapiApiHelper.getCartForFolder(folder, "test-token")
        assertNotNull(newCartInfo)
        assertEquals("new-cart-id", newCartInfo.cartId)
        assertEquals("TestFolder", newCartInfo.cartName)
        assertEquals("test-token", newCartInfo.privateToken)

        // Test when cart creation fails
        mockHelper.findCartResult = null
        mockHelper.findCartSecondCallResult = null
        mockHelper.addCartResult = false
        val failedCartInfo = yapiApiHelper.getCartForFolder(folder, "test-token")
        assertNull(failedCartInfo)
    }

    /**
     * A mock implementation of YapiApiHelper for testing the extension functions
     */
    class MockYapiApiHelper : YapiApiHelper {
        // For tracking calls
        var lastToken: String? = null
        var lastCatId: String? = null
        var lastLimit: Int? = null

        // For controlling return values
        var listApisResult = GsonUtils.parseToJsonTree("[]")!!.asJsonArray
        var projectIdResult: String? = "test-project"
        var cartsResult: List<Any?>? = emptyList()
        var listApisForCatId = mutableMapOf<String, JsonArray>()
        var findCartResult: String? = null
        var findCartSecondCallResult: String? = null
        private var findCartCalled = false
        var addCartResult = false

        override fun getApiInfo(token: String, id: String): JsonObject? {
            TODO("Not part of this test")
        }

        override fun findApi(token: String, catId: String, apiName: String): String? {
            TODO("Not part of this test")
        }

        override fun findApis(token: String, catId: String): List<Any?>? {
            TODO("Not part of this test")
        }

        override fun listApis(token: String, catId: String, limit: Int?): JsonArray? {
            lastToken = token
            lastCatId = catId
            lastLimit = limit

            // Return the specific result for a category if specified for findExistApi
            return listApisForCatId[catId] ?: listApisResult
        }

        override fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean {
            TODO("Not part of this test")
        }

        override fun getApiWeb(module: String, cartName: String, apiName: String): String? {
            TODO("Not part of this test")
        }

        override fun findCart(token: String, name: String): String? {
            return if (!findCartCalled && findCartSecondCallResult != null) {
                findCartCalled = true
                findCartResult
            } else {
                findCartSecondCallResult ?: findCartResult
            }
        }

        override fun findCarts(projectId: String, token: String): List<Any?>? {
            return cartsResult
        }

        override fun addCart(privateToken: String, name: String, desc: String): Boolean {
            return addCartResult
        }

        override fun addCart(projectId: String, token: String, name: String, desc: String): Boolean {
            TODO("Not part of this test")
        }

        override fun getCartWeb(projectId: String, catId: String): String? {
            TODO("Not part of this test")
        }

        override fun findCartWeb(module: String, cartName: String): String? {
            TODO("Not part of this test")
        }

        override fun getProjectIdByToken(token: String): String? {
            return projectIdResult
        }

        override fun getProjectInfo(token: String, projectId: String?): JsonObject? {
            TODO("Not part of this test")
        }

        override fun getProjectInfo(token: String): JsonObject? {
            TODO("Not part of this test")
        }

        override fun copyApi(from: Map<String, String>, target: Map<String, String>) {
            TODO("Not part of this test")
        }
    }
} 