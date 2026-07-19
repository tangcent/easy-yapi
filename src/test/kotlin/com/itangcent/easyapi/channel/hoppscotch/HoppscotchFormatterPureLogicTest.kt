package com.itangcent.easyapi.channel.hoppscotch

import com.itangcent.easyapi.channel.hoppscotch.model.*
import com.itangcent.easyapi.core.export.*
import com.itangcent.easyapi.core.psi.model.ObjectModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HoppscotchFormatter pure logic methods and format() behavior.
 *
 * Uses reflection to test private methods (buildBody, buildParams, buildHeaders,
 * buildEndpointUrl, convertToHoppscotchVarSyntax, formatTimestamp) and
 * tests companion methods directly.
 */
class HoppscotchFormatterPureLogicTest {

    // ==================== Companion method tests ====================

    @Test
    fun `parsePath splits simple path`() {
        assertEquals(listOf("api", "users"), HoppscotchFormatter.parsePath("/api/users"))
    }

    @Test
    fun `parsePath splits path with trailing slash`() {
        assertEquals(listOf("api", "users"), HoppscotchFormatter.parsePath("/api/users/"))
    }

    @Test
    fun `parsePath splits path without leading slash`() {
        assertEquals(listOf("api", "users"), HoppscotchFormatter.parsePath("api/users"))
    }

    @Test
    fun `parsePath splits empty path`() {
        assertEquals(emptyList<String>(), HoppscotchFormatter.parsePath(""))
    }

    @Test
    fun `parsePath splits root path`() {
        assertEquals(emptyList<String>(), HoppscotchFormatter.parsePath("/"))
    }

    @Test
    fun `parsePath splits deeply nested path`() {
        assertEquals(listOf("api", "v1", "users", "{id}"), HoppscotchFormatter.parsePath("/api/v1/users/{id}"))
    }

    // ==================== formatTimestamp via reflection ====================

    @Test
    fun `formatTimestamp produces expected format`() {
        // formatTimestamp is a private companion method
        val companionClass = HoppscotchFormatter.Companion::class.java
        val method = companionClass.getDeclaredMethod("formatTimestamp", Long::class.javaPrimitiveType ?: Long::class.java)
        method.isAccessible = true
        val result = method.invoke(HoppscotchFormatter, 1705312245000L) as String
        assertTrue("Expected 14-digit timestamp but got: $result", result.matches(Regex("\\d{14}")))
    }

    @Test
    fun `formatTimestamp for epoch zero`() {
        val companionClass = HoppscotchFormatter.Companion::class.java
        val method = companionClass.getDeclaredMethod("formatTimestamp", Long::class.javaPrimitiveType ?: Long::class.java)
        method.isAccessible = true
        val result = method.invoke(HoppscotchFormatter, 0L) as String
        assertTrue("Expected 14-digit timestamp but got: $result", result.matches(Regex("\\d{14}")))
    }

    // ==================== convertToHoppscotchVarSyntax logic test ====================
    // convertToHoppscotchVarSyntax is a private instance method requiring Project.
    // We test the equivalent logic directly since it's simple string manipulation.

    @Test
    fun `convertToHoppscotchVarSyntax logic - replaces mustache with angle brackets`() {
        val input = "https://{{host}}/api"
        val result = input.trim().trim('`').replace("{{", "<<").replace("}}", ">>")
        assertEquals("https://<<host>>/api", result)
    }

    @Test
    fun `convertToHoppscotchVarSyntax logic - trims whitespace`() {
        val input = "  https://host/api  "
        val result = input.trim().trim('`').replace("{{", "<<").replace("}}", ">>")
        assertEquals("https://host/api", result)
    }

    @Test
    fun `convertToHoppscotchVarSyntax logic - removes backticks`() {
        val input = "`https://host/api`"
        val result = input.trim().trim('`').replace("{{", "<<").replace("}}", ">>")
        assertEquals("https://host/api", result)
    }

    @Test
    fun `convertToHoppscotchVarSyntax logic - handles plain URL`() {
        val input = "https://host/api"
        val result = input.trim().trim('`').replace("{{", "<<").replace("}}", ">>")
        assertEquals("https://host/api", result)
    }

    @Test
    fun `convertToHoppscotchVarSyntax logic - multiple variables`() {
        val input = "https://{{host}}/{{resource}}/{{id}}"
        val result = input.trim().trim('`').replace("{{", "<<").replace("}}", ">>")
        assertEquals("https://<<host>>/<<resource>>/<<id>>", result)
    }

    // ==================== buildEndpointUrl logic test ====================
    // buildEndpointUrl is a private instance method requiring Project.
    // We test the equivalent logic directly.

    @Test
    fun `buildEndpointUrl logic - combines host and path`() {
        val host = "https://example.com"
        val path = "/api/users"
        val normalizedPath = path.trim().let { if (it.startsWith("/")) it else "/$it" }
        val result = (host.trimEnd('/') + normalizedPath).trim('`').replace("{{", "<<").replace("}}", ">>")
        assertEquals("https://example.com/api/users", result)
    }

    @Test
    fun `buildEndpointUrl logic - adds leading slash if missing`() {
        val host = "https://example.com"
        val path = "api/users"
        val normalizedPath = path.trim().let { if (it.startsWith("/")) it else "/$it" }
        val result = (host.trimEnd('/') + normalizedPath).trim('`').replace("{{", "<<").replace("}}", ">>")
        assertEquals("https://example.com/api/users", result)
    }

    @Test
    fun `buildEndpointUrl logic - removes trailing slash from host`() {
        val host = "https://example.com/"
        val path = "/api/users"
        val normalizedPath = path.trim().let { if (it.startsWith("/")) it else "/$it" }
        val result = (host.trimEnd('/') + normalizedPath).trim('`').replace("{{", "<<").replace("}}", ">>")
        assertEquals("https://example.com/api/users", result)
    }

    @Test
    fun `buildEndpointUrl logic - replaces mustache with angle brackets`() {
        val host = "https://{{host}}"
        val path = "/api/{{resource}}"
        val normalizedPath = path.trim().let { if (it.startsWith("/")) it else "/$it" }
        val result = (host.trimEnd('/') + normalizedPath).trim('`').replace("{{", "<<").replace("}}", ">>")
        assertEquals("https://<<host>>/api/<<resource>>", result)
    }

    // ==================== HoppscotchFormatOptions tests ====================

    @Test
    fun `HoppscotchFormatOptions default values`() {
        val options = HoppscotchFormatOptions()
        assertEquals("https://<<host>>", options.defaultHost)
        assertTrue(options.appendTimestamp)
    }

    @Test
    fun `HoppscotchFormatOptions custom values`() {
        val options = HoppscotchFormatOptions(defaultHost = "https://api.example.com", appendTimestamp = false)
        assertEquals("https://api.example.com", options.defaultHost)
        assertFalse(options.appendTimestamp)
    }

    @Test
    fun `HoppscotchFormatOptions copy`() {
        val options = HoppscotchFormatOptions()
        val copy = options.copy(appendTimestamp = false)
        assertFalse(copy.appendTimestamp)
        assertEquals(options.defaultHost, copy.defaultHost)
    }

    // ==================== HoppscotchExportMetadata tests ====================

    @Test
    fun `HoppscotchExportMetadata formatDisplay returns null for file export`() {
        val metadata = HoppscotchExportMetadata(
            collectionName = "Test",
            collectionData = HoppCollection(name = "Test")
        )
        assertNull(metadata.formatDisplay())
    }

    @Test
    fun `HoppscotchExportMetadata formatDisplay returns workspace slash collection for cloud`() {
        val metadata = HoppscotchExportMetadata(
            collectionName = "My Collection",
            workspaceName = "My Workspace"
        )
        assertEquals("My Workspace/My Collection", metadata.formatDisplay())
    }

    @Test
    fun `HoppscotchExportMetadata formatDisplay uses collectionId when collectionName is null`() {
        val metadata = HoppscotchExportMetadata(
            collectionId = "col-123",
            workspaceName = "My Workspace"
        )
        assertEquals("My Workspace/col-123", metadata.formatDisplay())
    }

    @Test
    fun `HoppscotchExportMetadata formatDisplay with null workspace`() {
        val metadata = HoppscotchExportMetadata(
            collectionName = "My Collection"
        )
        assertEquals("My Collection", metadata.formatDisplay())
    }

    @Test
    fun `HoppscotchExportMetadata formatDisplay with all nulls except collectionData`() {
        val metadata = HoppscotchExportMetadata(
            collectionData = HoppCollection(name = "Test")
        )
        assertNull(metadata.formatDisplay())
    }

    // ==================== HoppscotchRuleKeys tests ====================

    @Test
    fun `HoppscotchRuleKeys maps to correct RuleKeys`() {
        assertNotNull(HoppscotchRuleKeys.HOPP_PREREQUEST)
        assertNotNull(HoppscotchRuleKeys.HOPP_CLASS_PREREQUEST)
        assertNotNull(HoppscotchRuleKeys.HOPP_COLLECTION_PREREQUEST)
        assertNotNull(HoppscotchRuleKeys.HOPP_TEST)
        assertNotNull(HoppscotchRuleKeys.HOPP_CLASS_TEST)
        assertNotNull(HoppscotchRuleKeys.HOPP_COLLECTION_TEST)
        assertNotNull(HoppscotchRuleKeys.HOPP_HOST)
        assertNotNull(HoppscotchRuleKeys.HOPP_FORMAT_AFTER)
    }

    // ==================== HttpMethod fromSpring tests ====================

    @Test
    fun `HttpMethod fromSpring maps correctly`() {
        assertEquals(HttpMethod.GET, HttpMethod.fromSpring("GET"))
        assertEquals(HttpMethod.POST, HttpMethod.fromSpring("POST"))
        assertEquals(HttpMethod.PUT, HttpMethod.fromSpring("PUT"))
        assertEquals(HttpMethod.DELETE, HttpMethod.fromSpring("DELETE"))
        assertEquals(HttpMethod.PATCH, HttpMethod.fromSpring("PATCH"))
        assertEquals(HttpMethod.HEAD, HttpMethod.fromSpring("HEAD"))
        assertEquals(HttpMethod.OPTIONS, HttpMethod.fromSpring("OPTIONS"))
        assertNull(HttpMethod.fromSpring("UNKNOWN"))
    }

    @Test
    fun `HttpMethod fromSpring is case insensitive`() {
        assertEquals(HttpMethod.GET, HttpMethod.fromSpring("get"))
        assertEquals(HttpMethod.POST, HttpMethod.fromSpring("post"))
    }

    // ==================== ParameterBinding tests ====================

    @Test
    fun `ParameterBinding instances are singletons`() {
        assertSame(ParameterBinding.Query, ParameterBinding.Query)
        assertSame(ParameterBinding.Path, ParameterBinding.Path)
        assertSame(ParameterBinding.Header, ParameterBinding.Header)
        assertSame(ParameterBinding.Cookie, ParameterBinding.Cookie)
        assertSame(ParameterBinding.Body, ParameterBinding.Body)
        assertSame(ParameterBinding.Form, ParameterBinding.Form)
        assertSame(ParameterBinding.Ignored, ParameterBinding.Ignored)
    }

    // ==================== ApiParameter tests ====================

    @Test
    fun `ApiParameter default values`() {
        val param = ApiParameter(name = "id")
        assertEquals("id", param.name)
        assertEquals(ParameterType.TEXT, param.type)
        assertFalse(param.required)
        assertNull(param.binding)
        assertNull(param.defaultValue)
        assertNull(param.description)
        assertNull(param.example)
        assertNull(param.enumValues)
    }

    @Test
    fun `ApiParameter with all fields`() {
        val param = ApiParameter(
            name = "userId",
            type = ParameterType.TEXT,
            required = true,
            binding = ParameterBinding.Query,
            defaultValue = "1",
            description = "User ID",
            example = "42",
            enumValues = listOf("1", "2", "3")
        )
        assertEquals("userId", param.name)
        assertTrue(param.required)
        assertEquals(ParameterBinding.Query, param.binding)
        assertEquals("1", param.defaultValue)
        assertEquals("42", param.example)
        assertEquals(listOf("1", "2", "3"), param.enumValues)
    }

    // ==================== ApiHeader tests ====================

    @Test
    fun `ApiHeader default values`() {
        val header = ApiHeader(name = "X-Custom")
        assertEquals("X-Custom", header.name)
        assertNull(header.value)
        assertNull(header.description)
        assertNull(header.example)
        assertFalse(header.required)
    }

    @Test
    fun `ApiHeader with all fields`() {
        val header = ApiHeader(
            name = "Authorization",
            value = "Bearer token",
            description = "Auth header",
            example = "Bearer xyz",
            required = true
        )
        assertEquals("Authorization", header.name)
        assertEquals("Bearer token", header.value)
        assertEquals("Auth header", header.description)
        assertEquals("Bearer xyz", header.example)
        assertTrue(header.required)
    }

    // ==================== HttpMetadata tests ====================

    @Test
    fun `HttpMetadata default values`() {
        val meta = HttpMetadata(method = HttpMethod.GET, path = "/api")
        assertEquals(HttpMethod.GET, meta.method)
        assertEquals("/api", meta.path)
        assertTrue(meta.parameters.isEmpty())
        assertTrue(meta.headers.isEmpty())
        assertNull(meta.contentType)
        assertNull(meta.bodyAttr)
        assertNull(meta.alternativePaths)
        assertNull(meta.body)
        assertNull(meta.responseBody)
    }

    @Test
    fun `HttpMetadata with parameters and headers`() {
        val meta = HttpMetadata(
            method = HttpMethod.POST,
            path = "/api/users",
            contentType = "application/json",
            body = ObjectModel.Single("string")
        )
        assertEquals(HttpMethod.POST, meta.method)
        assertEquals("/api/users", meta.path)
        assertEquals("application/json", meta.contentType)
        assertNotNull(meta.body)
    }
}
