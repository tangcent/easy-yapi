package com.itangcent.easyapi.channel.hoppscotch

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itangcent.easyapi.core.http.HttpResponse
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Tests for HoppscotchAuthService private methods via reflection.
 *
 * Covers the private helper methods that are not exercised by other tests:
 * - [HoppscotchAuthService.extractTokenFromCookieHeader]
 * - [HoppscotchAuthService.extractTokenFromResponseBody]
 * - [HoppscotchAuthService.getJsonString]
 * - [HoppscotchAuthService.parseJson]
 *
 * These methods are pure (no Project / HttpClient dependencies), so they can
 * be safely invoked via reflection on an instance constructed with a mock Project.
 */
class HoppscotchAuthServicePrivateMethodsTest {

    private val authService = HoppscotchAuthService(mock())

    // ==================== extractTokenFromCookieHeader ====================

    private fun invokeExtractTokenFromCookieHeader(
        response: HttpResponse,
        tokenName: String
    ): String? {
        val method = HoppscotchAuthService::class.java.getDeclaredMethod(
            "extractTokenFromCookieHeader", HttpResponse::class.java, String::class.java
        )
        method.isAccessible = true
        return method.invoke(authService, response, tokenName) as String?
    }

    @Test
    fun `extractTokenFromCookieHeader extracts token from Set-Cookie header`() {
        val response = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("access_token=abc123; Path=/; HttpOnly")),
            body = null
        )
        assertEquals("abc123", invokeExtractTokenFromCookieHeader(response, "access_token"))
    }

    @Test
    fun `extractTokenFromCookieHeader extracts token from lowercase set-cookie header`() {
        val response = HttpResponse(
            code = 200,
            headers = mapOf("set-cookie" to listOf("refresh_token=xyz789; Path=/; HttpOnly")),
            body = null
        )
        assertEquals("xyz789", invokeExtractTokenFromCookieHeader(response, "refresh_token"))
    }

    @Test
    fun `extractTokenFromCookieHeader returns null when no cookie header present`() {
        val response = HttpResponse(code = 200, headers = emptyMap(), body = null)
        assertNull(invokeExtractTokenFromCookieHeader(response, "access_token"))
    }

    @Test
    fun `extractTokenFromCookieHeader returns null when token name does not match`() {
        val response = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("other_token=abc123; Path=/; HttpOnly")),
            body = null
        )
        assertNull(invokeExtractTokenFromCookieHeader(response, "access_token"))
    }

    @Test
    fun `extractTokenFromCookieHeader finds correct token among multiple cookies`() {
        val response = HttpResponse(
            code = 200,
            headers = mapOf(
                "Set-Cookie" to listOf(
                    "session_id= sess123; Path=/; HttpOnly",
                    "access_token=my-access-token; Path=/; HttpOnly",
                    "refresh_token=my-refresh-token; Path=/; HttpOnly"
                )
            ),
            body = null
        )
        assertEquals("my-access-token", invokeExtractTokenFromCookieHeader(response, "access_token"))
        assertEquals("my-refresh-token", invokeExtractTokenFromCookieHeader(response, "refresh_token"))
    }

    @Test
    fun `extractTokenFromCookieHeader extracts token without attributes`() {
        val response = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("access_token=bareToken")),
            body = null
        )
        assertEquals("bareToken", invokeExtractTokenFromCookieHeader(response, "access_token"))
    }

    @Test
    fun `extractTokenFromCookieHeader returns empty string when token value is empty`() {
        val response = HttpResponse(
            code = 200,
            headers = mapOf("Set-Cookie" to listOf("access_token=; Path=/; HttpOnly")),
            body = null
        )
        assertEquals("", invokeExtractTokenFromCookieHeader(response, "access_token"))
    }

    // ==================== extractTokenFromResponseBody ====================

    private fun invokeExtractTokenFromResponseBody(response: HttpResponse): String? {
        val method = HoppscotchAuthService::class.java.getDeclaredMethod(
            "extractTokenFromResponseBody", HttpResponse::class.java
        )
        method.isAccessible = true
        return method.invoke(authService, response) as String?
    }

    @Test
    fun `extractTokenFromResponseBody extracts access_token from JSON body`() {
        val response = HttpResponse(
            code = 200,
            headers = emptyMap(),
            body = """{"access_token":"token-from-body","expires_in":3600}"""
        )
        assertEquals("token-from-body", invokeExtractTokenFromResponseBody(response))
    }

    @Test
    fun `extractTokenFromResponseBody returns null when body is null`() {
        val response = HttpResponse(code = 200, headers = emptyMap(), body = null)
        assertNull(invokeExtractTokenFromResponseBody(response))
    }

    @Test
    fun `extractTokenFromResponseBody returns null when body is blank`() {
        val response = HttpResponse(code = 200, headers = emptyMap(), body = "   ")
        assertNull(invokeExtractTokenFromResponseBody(response))
    }

    @Test
    fun `extractTokenFromResponseBody returns null when body is not valid JSON`() {
        val response = HttpResponse(
            code = 200,
            headers = emptyMap(),
            body = "this is not json"
        )
        assertNull(invokeExtractTokenFromResponseBody(response))
    }

    @Test
    fun `extractTokenFromResponseBody returns null when JSON has no access_token field`() {
        val response = HttpResponse(
            code = 200,
            headers = emptyMap(),
            body = """{"other_field":"value"}"""
        )
        assertNull(invokeExtractTokenFromResponseBody(response))
    }

    @Test
    fun `extractTokenFromResponseBody returns null when access_token is null in JSON`() {
        val response = HttpResponse(
            code = 200,
            headers = emptyMap(),
            body = """{"access_token":null}"""
        )
        assertNull(invokeExtractTokenFromResponseBody(response))
    }

    // ==================== getJsonString ====================

    private fun invokeGetJsonString(json: JsonObject?, field: String): String? {
        val method = HoppscotchAuthService::class.java.getDeclaredMethod(
            "getJsonString", JsonObject::class.java, String::class.java
        )
        method.isAccessible = true
        return method.invoke(authService, json, field) as String?
    }

    @Test
    fun `getJsonString returns null when json is null`() {
        assertNull(invokeGetJsonString(null, "message"))
    }

    @Test
    fun `getJsonString returns null when field is not present`() {
        val json = JsonObject()
        assertNull(invokeGetJsonString(json, "message"))
    }

    @Test
    fun `getJsonString returns string when field is a primitive string`() {
        val json = JsonObject()
        json.addProperty("message", "INVALID_EMAIL")
        assertEquals("INVALID_EMAIL", invokeGetJsonString(json, "message"))
    }

    @Test
    fun `getJsonString returns inner message when field is a nested object with message primitive`() {
        val json = JsonParser.parseString("""{"message":{"message":"AUTH_PROVIDER_NOT_SPECIFIED","statusCode":404}}""").asJsonObject
        assertEquals("AUTH_PROVIDER_NOT_SPECIFIED", invokeGetJsonString(json, "message"))
    }

    @Test
    fun `getJsonString returns string representation when field is a nested object without message`() {
        val json = JsonParser.parseString("""{"error":{"code":500}}""").asJsonObject
        val result = invokeGetJsonString(json, "error")
        assertNotNull(result)
        assertTrue(result!!.contains("\"code\":500"))
    }

    @Test
    fun `getJsonString returns string representation when inner message is not primitive`() {
        val json = JsonParser.parseString("""{"error":{"message":{"nested":"value"}}}""").asJsonObject
        val result = invokeGetJsonString(json, "error")
        assertNotNull(result)
        // Inner message is a JsonObject (not primitive), so falls to element.toString()
        assertTrue(result!!.contains("nested"))
    }

    @Test
    fun `getJsonString returns string representation for non-primitive non-object field`() {
        val json = JsonParser.parseString("""{"items":[1,2,3]}""").asJsonObject
        val result = invokeGetJsonString(json, "items")
        assertNotNull(result)
        assertTrue(result!!.contains("1"))
    }

    @Test
    fun `getJsonString returns value for numeric primitive field`() {
        val json = JsonObject()
        json.addProperty("code", 404)
        assertEquals("404", invokeGetJsonString(json, "code"))
    }

    @Test
    fun `getJsonString returns value for boolean primitive field`() {
        val json = JsonObject()
        json.addProperty("active", true)
        assertEquals("true", invokeGetJsonString(json, "active"))
    }

    // ==================== parseJson ====================

    private fun invokeParseJson(body: String?): JsonObject? {
        val method = HoppscotchAuthService::class.java.getDeclaredMethod(
            "parseJson", String::class.java
        )
        method.isAccessible = true
        return method.invoke(authService, body) as JsonObject?
    }

    @Test
    fun `parseJson returns null for null body`() {
        assertNull(invokeParseJson(null))
    }

    @Test
    fun `parseJson returns null for blank body`() {
        assertNull(invokeParseJson(""))
        assertNull(invokeParseJson("   "))
    }

    @Test
    fun `parseJson returns JsonObject for valid JSON`() {
        val result = invokeParseJson("""{"message":"hello","code":200}""")
        assertNotNull(result)
        assertEquals("hello", result?.get("message")?.asString)
        assertEquals(200, result?.get("code")?.asInt)
    }

    @Test
    fun `parseJson returns null for invalid JSON`() {
        assertNull(invokeParseJson("not valid json"))
    }

    @Test
    fun `parseJson returns null for JSON array instead of object`() {
        assertNull(invokeParseJson("[1, 2, 3]"))
    }

    @Test
    fun `parseJson returns empty JsonObject for empty JSON object`() {
        val result = invokeParseJson("{}")
        assertNotNull(result)
        assertEquals(0, result?.size())
    }
}
