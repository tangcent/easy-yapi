package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppFormDataEntry
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppKeyValue
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppRequestBody
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.model.ObjectModel
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Tests for HoppscotchFormatter private methods via reflection.
 *
 * Covers the private helper methods that are not exercised by other tests:
 * - [HoppscotchFormatter.buildBody] (all 5 branches)
 * - [HoppscotchFormatter.buildParams]
 * - [HoppscotchFormatter.buildHeaders]
 * - [HoppscotchFormatter.buildEndpointUrl]
 * - [HoppscotchFormatter.convertToHoppscotchVarSyntax]
 *
 * The [HoppscotchFormatter.ruleEngine] field is `lazy`, so constructing the
 * formatter with a mock Project is safe as long as we only call private
 * methods that don't access `ruleEngine` — which all the tested methods are.
 */
class HoppscotchFormatterPrivateMethodsTest {

    private val formatter = HoppscotchFormatter(mock())

    // ==================== convertToHoppscotchVarSyntax ====================

    private fun invokeConvertToHoppscotchVarSyntax(value: String): String {
        val method = HoppscotchFormatter::class.java.getDeclaredMethod(
            "convertToHoppscotchVarSyntax", String::class.java
        )
        method.isAccessible = true
        return method.invoke(formatter, value) as String
    }

    @Test
    fun `convertToHoppscotchVarSyntax replaces mustache with angle brackets`() {
        assertEquals("https://<<host>>/api", invokeConvertToHoppscotchVarSyntax("https://{{host}}/api"))
    }

    @Test
    fun `convertToHoppscotchVarSyntax trims whitespace`() {
        assertEquals("https://host/api", invokeConvertToHoppscotchVarSyntax("  https://host/api  "))
    }

    @Test
    fun `convertToHoppscotchVarSyntax removes backticks`() {
        assertEquals("https://host/api", invokeConvertToHoppscotchVarSyntax("`https://host/api`"))
    }

    @Test
    fun `convertToHoppscotchVarSyntax handles plain URL`() {
        assertEquals("https://host/api", invokeConvertToHoppscotchVarSyntax("https://host/api"))
    }

    @Test
    fun `convertToHoppscotchVarSyntax handles multiple variables`() {
        assertEquals(
            "https://<<host>>/<<resource>>/<<id>>",
            invokeConvertToHoppscotchVarSyntax("https://{{host}}/{{resource}}/{{id}}")
        )
    }

    @Test
    fun `convertToHoppscotchVarSyntax handles empty string`() {
        assertEquals("", invokeConvertToHoppscotchVarSyntax(""))
    }

    @Test
    fun `convertToHoppscotchVarSyntax handles backticks with variables`() {
        assertEquals("https://<<host>>/api", invokeConvertToHoppscotchVarSyntax("`https://{{host}}/api`"))
    }

    // ==================== buildEndpointUrl ====================

    private fun invokeBuildEndpointUrl(meta: HttpMetadata, host: String): String {
        val method = HoppscotchFormatter::class.java.getDeclaredMethod(
            "buildEndpointUrl", HttpMetadata::class.java, String::class.java
        )
        method.isAccessible = true
        return method.invoke(formatter, meta, host) as String
    }

    @Test
    fun `buildEndpointUrl combines host and path`() {
        val meta = HttpMetadata(path = "/api/users", method = HttpMethod.GET)
        assertEquals("https://example.com/api/users", invokeBuildEndpointUrl(meta, "https://example.com"))
    }

    @Test
    fun `buildEndpointUrl adds leading slash if missing`() {
        val meta = HttpMetadata(path = "api/users", method = HttpMethod.GET)
        assertEquals("https://example.com/api/users", invokeBuildEndpointUrl(meta, "https://example.com"))
    }

    @Test
    fun `buildEndpointUrl removes trailing slash from host`() {
        val meta = HttpMetadata(path = "/api/users", method = HttpMethod.GET)
        assertEquals("https://example.com/api/users", invokeBuildEndpointUrl(meta, "https://example.com/"))
    }

    @Test
    fun `buildEndpointUrl replaces mustache with angle brackets`() {
        val meta = HttpMetadata(path = "/api/{{resource}}", method = HttpMethod.GET)
        assertEquals("https://<<host>>/api/<<resource>>", invokeBuildEndpointUrl(meta, "https://{{host}}"))
    }

    @Test
    fun `buildEndpointUrl trims leading backticks from combined URL`() {
        // trim('`') only removes backticks from the start/end of the full combined string,
        // not from the middle. A backtick at the end of host is in the middle after concat.
        val meta = HttpMetadata(path = "/api/users", method = HttpMethod.GET)
        val result = invokeBuildEndpointUrl(meta, "`https://example.com`")
        assertEquals("https://example.com`/api/users", result)
    }

    @Test
    fun `buildEndpointUrl trims trailing backticks from combined URL`() {
        // When the path itself ends with a backtick, trim('`') removes it from the end
        val meta = HttpMetadata(path = "/api/users`", method = HttpMethod.GET)
        val result = invokeBuildEndpointUrl(meta, "https://example.com")
        // combined = "https://example.com/api/users`" -> trim('`') -> "https://example.com/api/users"
        assertEquals("https://example.com/api/users", result)
    }

    @Test
    fun `buildEndpointUrl handles root path`() {
        val meta = HttpMetadata(path = "/", method = HttpMethod.GET)
        assertEquals("https://example.com/", invokeBuildEndpointUrl(meta, "https://example.com"))
    }

    @Test
    fun `buildEndpointUrl handles empty path`() {
        val meta = HttpMetadata(path = "", method = HttpMethod.GET)
        assertEquals("https://example.com/", invokeBuildEndpointUrl(meta, "https://example.com"))
    }

    // ==================== buildParams ====================

    private fun invokeBuildParams(meta: HttpMetadata): List<HoppKeyValue> {
        val method = HoppscotchFormatter::class.java.getDeclaredMethod(
            "buildParams", HttpMetadata::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(formatter, meta) as List<HoppKeyValue>
    }

    @Test
    fun `buildParams returns empty list for no parameters`() {
        val meta = HttpMetadata(path = "/api", method = HttpMethod.GET)
        assertTrue(invokeBuildParams(meta).isEmpty())
    }

    @Test
    fun `buildParams includes query parameters`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            parameters = mutableListOf(
                ApiParameter(name = "page", binding = ParameterBinding.Query, example = "1")
            )
        )
        val params = invokeBuildParams(meta)
        assertEquals(1, params.size)
        assertEquals("page", params[0].key)
        assertEquals("1", params[0].value)
        assertTrue(params[0].active)
    }

    @Test
    fun `buildParams includes path parameters`() {
        val meta = HttpMetadata(
            path = "/api/users/{id}",
            method = HttpMethod.GET,
            parameters = mutableListOf(
                ApiParameter(name = "id", binding = ParameterBinding.Path, example = "42")
            )
        )
        val params = invokeBuildParams(meta)
        assertEquals(1, params.size)
        assertEquals("id", params[0].key)
        assertEquals("42", params[0].value)
    }

    @Test
    fun `buildParams excludes form parameters`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            parameters = mutableListOf(
                ApiParameter(name = "page", binding = ParameterBinding.Query, example = "1"),
                ApiParameter(name = "username", binding = ParameterBinding.Form, example = "alice")
            )
        )
        val params = invokeBuildParams(meta)
        assertEquals(1, params.size)
        assertEquals("page", params[0].key)
    }

    @Test
    fun `buildParams excludes header and body parameters`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            parameters = mutableListOf(
                ApiParameter(name = "page", binding = ParameterBinding.Query, example = "1"),
                ApiParameter(name = "X-Custom", binding = ParameterBinding.Header, example = "val"),
                ApiParameter(name = "body", binding = ParameterBinding.Body, example = "data")
            )
        )
        val params = invokeBuildParams(meta)
        assertEquals(1, params.size)
        assertEquals("page", params[0].key)
    }

    @Test
    fun `buildParams uses defaultValue when example is null`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            parameters = mutableListOf(
                ApiParameter(name = "page", binding = ParameterBinding.Query, defaultValue = "10")
            )
        )
        val params = invokeBuildParams(meta)
        assertEquals("10", params[0].value)
    }

    @Test
    fun `buildParams uses empty string when neither example nor defaultValue`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            parameters = mutableListOf(
                ApiParameter(name = "page", binding = ParameterBinding.Query)
            )
        )
        val params = invokeBuildParams(meta)
        assertEquals("", params[0].value)
    }

    @Test
    fun `buildParams includes description`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            parameters = mutableListOf(
                ApiParameter(name = "page", binding = ParameterBinding.Query, example = "1", description = "Page number")
            )
        )
        val params = invokeBuildParams(meta)
        assertEquals("Page number", params[0].description)
    }

    // ==================== buildHeaders ====================

    private fun invokeBuildHeaders(meta: HttpMetadata): List<HoppKeyValue> {
        val method = HoppscotchFormatter::class.java.getDeclaredMethod(
            "buildHeaders", HttpMetadata::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(formatter, meta) as List<HoppKeyValue>
    }

    @Test
    fun `buildHeaders returns empty list for no headers`() {
        val meta = HttpMetadata(path = "/api", method = HttpMethod.GET)
        assertTrue(invokeBuildHeaders(meta).isEmpty())
    }

    @Test
    fun `buildHeaders uses value when present`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            headers = mutableListOf(
                ApiHeader(name = "Authorization", value = "Bearer token")
            )
        )
        val headers = invokeBuildHeaders(meta)
        assertEquals(1, headers.size)
        assertEquals("Authorization", headers[0].key)
        assertEquals("Bearer token", headers[0].value)
        assertTrue(headers[0].active)
    }

    @Test
    fun `buildHeaders uses example when value is null`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            headers = mutableListOf(
                ApiHeader(name = "Authorization", value = null, example = "Bearer example")
            )
        )
        val headers = invokeBuildHeaders(meta)
        assertEquals("Bearer example", headers[0].value)
    }

    @Test
    fun `buildHeaders uses empty string when neither value nor example`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            headers = mutableListOf(
                ApiHeader(name = "X-Custom", value = null, example = null)
            )
        )
        val headers = invokeBuildHeaders(meta)
        assertEquals("", headers[0].value)
    }

    @Test
    fun `buildHeaders includes description`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            headers = mutableListOf(
                ApiHeader(name = "Authorization", value = "Bearer token", description = "Auth header")
            )
        )
        val headers = invokeBuildHeaders(meta)
        assertEquals("Auth header", headers[0].description)
    }

    @Test
    fun `buildHeaders handles multiple headers`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            headers = mutableListOf(
                ApiHeader(name = "Content-Type", value = "application/json"),
                ApiHeader(name = "Accept", value = "application/json"),
                ApiHeader(name = "Authorization", value = "Bearer token")
            )
        )
        val headers = invokeBuildHeaders(meta)
        assertEquals(3, headers.size)
    }

    // ==================== buildBody ====================

    private fun invokeBuildBody(meta: HttpMetadata): HoppRequestBody {
        val method = HoppscotchFormatter::class.java.getDeclaredMethod(
            "buildBody", HttpMetadata::class.java
        )
        method.isAccessible = true
        return method.invoke(formatter, meta) as HoppRequestBody
    }

    @Test
    fun `buildBody returns JSON body when content type is json`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "application/json",
            body = ObjectModel.Single("string")
        )
        val body = invokeBuildBody(meta)
        assertEquals("application/json", body.contentType)
        assertNotNull(body.body)
        assertTrue(body.body is String)
    }

    @Test
    fun `buildBody returns empty JSON object when content type is json but no body`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "application/json",
            body = null
        )
        val body = invokeBuildBody(meta)
        assertEquals("application/json", body.contentType)
        assertEquals("{}", body.body)
    }

    @Test
    fun `buildBody handles uppercase JSON content type`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "APPLICATION/JSON",
            body = ObjectModel.Single("string")
        )
        val body = invokeBuildBody(meta)
        assertEquals("application/json", body.contentType)
        assertNotNull(body.body)
    }

    @Test
    fun `buildBody returns form-urlencoded body`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "application/x-www-form-urlencoded",
            parameters = mutableListOf(
                ApiParameter(name = "username", binding = ParameterBinding.Form, example = "alice"),
                ApiParameter(name = "password", binding = ParameterBinding.Form, example = "secret")
            )
        )
        val body = invokeBuildBody(meta)
        assertEquals("application/x-www-form-urlencoded", body.contentType)
        val bodyStr = body.body as String
        assertTrue(bodyStr.contains("username=alice"))
        assertTrue(bodyStr.contains("password=secret"))
        assertTrue(bodyStr.contains("&"))
    }

    @Test
    fun `buildBody form-urlencoded uses defaultValue when example is null`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "application/x-www-form-urlencoded",
            parameters = mutableListOf(
                ApiParameter(name = "name", binding = ParameterBinding.Form, defaultValue = "default-val")
            )
        )
        val body = invokeBuildBody(meta)
        val bodyStr = body.body as String
        assertTrue(bodyStr.contains("name=default-val"))
    }

    @Test
    fun `buildBody form-urlencoded URL-encodes values`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "application/x-www-form-urlencoded",
            parameters = mutableListOf(
                ApiParameter(name = "query", binding = ParameterBinding.Form, example = "hello world&more")
            )
        )
        val body = invokeBuildBody(meta)
        val bodyStr = body.body as String
        // Space and & should be URL-encoded
        assertTrue(bodyStr.contains("query=hello+world%26more"))
    }

    @Test
    fun `buildBody returns multipart body for multipart content type`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "multipart/form-data",
            parameters = mutableListOf(
                ApiParameter(name = "file", binding = ParameterBinding.Form, example = "test.txt"),
                ApiParameter(name = "name", binding = ParameterBinding.Form, example = "Alice")
            )
        )
        val body = invokeBuildBody(meta)
        assertEquals("multipart/form-data", body.contentType)
        assertNotNull(body.body)
        @Suppress("UNCHECKED_CAST")
        val entries = body.body as List<HoppFormDataEntry>
        assertEquals(2, entries.size)
        assertEquals("file", entries[0].key)
        assertEquals("test.txt", entries[0].value)
        assertTrue(entries[0].active)
        assertFalse(entries[0].isFile)
    }

    @Test
    fun `buildBody returns multipart body for form-data content type`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "application/form-data",
            parameters = mutableListOf(
                ApiParameter(name = "name", binding = ParameterBinding.Form, example = "Alice")
            )
        )
        val body = invokeBuildBody(meta)
        assertEquals("multipart/form-data", body.contentType)
        assertNotNull(body.body)
    }

    @Test
    fun `buildBody multipart uses defaultValue when example is null`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "multipart/form-data",
            parameters = mutableListOf(
                ApiParameter(name = "name", binding = ParameterBinding.Form, defaultValue = "default-name")
            )
        )
        val body = invokeBuildBody(meta)
        @Suppress("UNCHECKED_CAST")
        val entries = body.body as List<HoppFormDataEntry>
        assertEquals("default-name", entries[0].value)
    }

    @Test
    fun `buildBody multipart uses empty string when no example or defaultValue`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "multipart/form-data",
            parameters = mutableListOf(
                ApiParameter(name = "name", binding = ParameterBinding.Form)
            )
        )
        val body = invokeBuildBody(meta)
        @Suppress("UNCHECKED_CAST")
        val entries = body.body as List<HoppFormDataEntry>
        assertEquals("", entries[0].value)
    }

    @Test
    fun `buildBody returns JSON body when no content type but body is present`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = null,
            body = ObjectModel.Single("string")
        )
        val body = invokeBuildBody(meta)
        assertEquals("application/json", body.contentType)
        assertNotNull(body.body)
    }

    @Test
    fun `buildBody returns empty body when no content type and no body`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            contentType = null,
            body = null
        )
        val body = invokeBuildBody(meta)
        assertNull(body.contentType)
        assertNull(body.body)
    }

    @Test
    fun `buildBody returns empty body for unknown content type without body`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.GET,
            contentType = "text/plain",
            body = null
        )
        val body = invokeBuildBody(meta)
        assertNull(body.contentType)
        assertNull(body.body)
    }

    @Test
    fun `buildBody returns JSON body for unknown content type with body`() {
        val meta = HttpMetadata(
            path = "/api",
            method = HttpMethod.POST,
            contentType = "text/plain",
            body = ObjectModel.Single("string")
        )
        val body = invokeBuildBody(meta)
        // Falls through to the `if (meta.body != null)` branch
        assertEquals("application/json", body.contentType)
        assertNotNull(body.body)
    }
}
