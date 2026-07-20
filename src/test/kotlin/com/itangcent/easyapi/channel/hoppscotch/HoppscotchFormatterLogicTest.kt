package com.itangcent.easyapi.channel.hoppscotch

import com.itangcent.easyapi.channel.hoppscotch.model.*
import com.itangcent.easyapi.core.export.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HoppscotchFormatter pure logic methods.
 * Methods that depend on Project/RuleEngine are tested via integration tests.
 */
class HoppscotchFormatterLogicTest {

    // ==================== parsePath ====================

    @Test
    fun `parsePath splits on forward slash`() {
        val result = HoppscotchFormatter.parsePath("/users/list")
        assertEquals(listOf("users", "list"), result)
    }

    @Test
    fun `parsePath trims leading and trailing slashes`() {
        val result = HoppscotchFormatter.parsePath("/users/list/")
        assertEquals(listOf("users", "list"), result)
    }

    @Test
    fun `parsePath handles single segment`() {
        val result = HoppscotchFormatter.parsePath("/users")
        assertEquals(listOf("users"), result)
    }

    @Test
    fun `parsePath handles empty string`() {
        val result = HoppscotchFormatter.parsePath("")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `parsePath handles root path`() {
        val result = HoppscotchFormatter.parsePath("/")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `parsePath handles path without leading slash`() {
        val result = HoppscotchFormatter.parsePath("users/list")
        assertEquals(listOf("users", "list"), result)
    }

    @Test
    fun `parsePath handles deep nesting`() {
        val result = HoppscotchFormatter.parsePath("/api/v1/users/{id}/posts")
        assertEquals(listOf("api", "v1", "users", "{id}", "posts"), result)
    }

    // ==================== HoppscotchFormatOptions ====================

    @Test
    fun `HoppscotchFormatOptions defaults`() {
        val opts = HoppscotchFormatOptions()
        assertEquals("https://<<host>>", opts.defaultHost)
        assertTrue(opts.appendTimestamp)
    }

    @Test
    fun `HoppscotchFormatOptions custom values`() {
        val opts = HoppscotchFormatOptions(defaultHost = "https://api.example.com", appendTimestamp = false)
        assertEquals("https://api.example.com", opts.defaultHost)
        assertFalse(opts.appendTimestamp)
    }

    // ==================== HoppCollection ====================

    @Test
    fun `HoppCollection defaults`() {
        val coll = HoppCollection(name = "Test")
        assertEquals(12, coll.v)
        assertEquals("Test", coll.name)
        assertTrue(coll.folders.isEmpty())
        assertTrue(coll.requests.isEmpty())
        assertEquals("", coll.preRequestScript)
        assertEquals("", coll.testScript)
    }

    @Test
    fun `HoppCollection with folders and requests`() {
        val request = HoppRESTRequest(
            name = "Get Users",
            method = "GET",
            endpoint = "https://api.example.com/users"
        )
        val folder = HoppCollection(name = "Users", requests = listOf(request))
        val coll = HoppCollection(name = "API", folders = listOf(folder))
        assertEquals(1, coll.folders.size)
        assertEquals("Users", coll.folders[0].name)
        assertEquals(1, coll.folders[0].requests.size)
    }

    @Test
    fun `HoppCollection with scripts`() {
        val coll = HoppCollection(
            name = "API",
            preRequestScript = "console.log('before')",
            testScript = "console.log('after')"
        )
        assertEquals("console.log('before')", coll.preRequestScript)
        assertEquals("console.log('after')", coll.testScript)
    }

    // ==================== HoppRESTRequest ====================

    @Test
    fun `HoppRESTRequest defaults`() {
        val req = HoppRESTRequest(name = "Test", method = "GET", endpoint = "/test")
        assertEquals("17", req.v)
        assertEquals("GET", req.method)
        assertTrue(req.params.isEmpty())
        assertTrue(req.headers.isEmpty())
        assertEquals("", req.preRequestScript)
        assertEquals("", req.testScript)
    }

    @Test
    fun `HoppRESTRequest with all fields`() {
        val req = HoppRESTRequest(
            name = "Create User",
            method = "POST",
            endpoint = "https://api.example.com/users",
            params = listOf(HoppKeyValue(key = "page", value = "1")),
            headers = listOf(HoppKeyValue(key = "Content-Type", value = "application/json")),
            body = HoppRequestBody(contentType = "application/json", body = """{"name":"Alice"}"""),
            preRequestScript = "pm.environment.set('token', 'abc')",
            testScript = "pm.response.to.have.status(200)",
            description = "Creates a new user"
        )
        assertEquals("POST", req.method)
        assertEquals(1, req.params.size)
        assertEquals(1, req.headers.size)
        assertNotNull(req.body)
        assertEquals("Creates a new user", req.description)
    }

    // ==================== HoppKeyValue ====================

    @Test
    fun `HoppKeyValue defaults`() {
        val kv = HoppKeyValue(key = "test")
        assertEquals("test", kv.key)
        assertEquals("", kv.value)
        assertTrue(kv.active)
        assertNull(kv.description)
    }

    @Test
    fun `HoppKeyValue with all fields`() {
        val kv = HoppKeyValue(key = "Authorization", value = "Bearer token", active = false, description = "Auth header")
        assertEquals("Authorization", kv.key)
        assertEquals("Bearer token", kv.value)
        assertFalse(kv.active)
        assertEquals("Auth header", kv.description)
    }

    // ==================== HoppAuth ====================

    @Test
    fun `HoppAuth defaults to inherit`() {
        val auth = HoppAuth()
        assertEquals("inherit", auth.authType)
        assertTrue(auth.authActive)
    }

    @Test
    fun `HoppAuth bearer`() {
        val auth = HoppAuth(authType = "bearer", authActive = true)
        assertEquals("bearer", auth.authType)
    }

    // ==================== HoppRequestBody ====================

    @Test
    fun `HoppRequestBody defaults to no body`() {
        val body = HoppRequestBody()
        assertNull(body.contentType)
        assertNull(body.body)
    }

    @Test
    fun `HoppRequestBody JSON body`() {
        val body = HoppRequestBody(contentType = "application/json", body = """{"key":"value"}""")
        assertEquals("application/json", body.contentType)
        assertEquals("""{"key":"value"}""", body.body)
    }

    @Test
    fun `HoppRequestBody form-urlencoded body`() {
        val body = HoppRequestBody(contentType = "application/x-www-form-urlencoded", body = "key=value")
        assertEquals("application/x-www-form-urlencoded", body.contentType)
    }

    @Test
    fun `HoppRequestBody multipart body`() {
        val formData = listOf(
            HoppFormDataEntry(key = "file", value = "test.txt", isFile = true),
            HoppFormDataEntry(key = "name", value = "Alice", isFile = false)
        )
        val body = HoppRequestBody(contentType = "multipart/form-data", body = formData)
        assertEquals("multipart/form-data", body.contentType)
        assertNotNull(body.body)
    }

    // ==================== HoppFormDataEntry ====================

    @Test
    fun `HoppFormDataEntry defaults`() {
        val entry = HoppFormDataEntry(key = "field")
        assertEquals("field", entry.key)
        assertEquals("", entry.value)
        assertTrue(entry.active)
        assertFalse(entry.isFile)
        assertNull(entry.contentType)
    }

    @Test
    fun `HoppFormDataEntry file entry`() {
        val entry = HoppFormDataEntry(key = "upload", value = "document.pdf", isFile = true, contentType = "application/pdf")
        assertTrue(entry.isFile)
        assertEquals("application/pdf", entry.contentType)
    }

    // ==================== HoppCollectionVariable ====================

    @Test
    fun `HoppCollectionVariable defaults`() {
        val v = HoppCollectionVariable(key = "host")
        assertEquals("host", v.key)
        assertEquals("", v.initialValue)
        assertEquals("", v.currentValue)
        assertFalse(v.secret)
    }

    @Test
    fun `HoppCollectionVariable with values`() {
        val v = HoppCollectionVariable(key = "token", initialValue = "abc", currentValue = "xyz", secret = true)
        assertEquals("abc", v.initialValue)
        assertEquals("xyz", v.currentValue)
        assertTrue(v.secret)
    }

    // ==================== HoppRequestVariable ====================

    @Test
    fun `HoppRequestVariable defaults`() {
        val v = HoppRequestVariable(key = "id")
        assertEquals("id", v.key)
        assertEquals("", v.value)
        assertTrue(v.active)
    }

    // ==================== hoppscotchGson ====================

    @Test
    fun `hoppscotchGson serializes nulls`() {
        val gson = hoppscotchGson(prettyPrint = false)
        val body = HoppRequestBody()
        val json = gson.toJson(body)
        assertTrue(json.contains("contentType"))
        assertTrue(json.contains("body"))
    }

    @Test
    fun `hoppscotchGson pretty print`() {
        val gson = hoppscotchGson(prettyPrint = true)
        val coll = HoppCollection(name = "Test")
        val json = gson.toJson(coll)
        assertTrue(json.contains("\n"))
    }

    @Test
    fun `hoppscotchGson compact print`() {
        val gson = hoppscotchGson(prettyPrint = false)
        val coll = HoppCollection(name = "Test")
        val json = gson.toJson(coll)
        assertFalse(json.contains("\n"))
    }

    @Test
    fun `hoppscotchGson serializes collection correctly`() {
        val gson = hoppscotchGson(prettyPrint = false)
        val coll = HoppCollection(
            name = "Test API",
            folders = listOf(HoppCollection(name = "Users")),
            requests = listOf(
                HoppRESTRequest(
                    name = "Get Users",
                    method = "GET",
                    endpoint = "https://api.example.com/users"
                )
            )
        )
        val json = gson.toJson(coll)
        assertTrue(json.contains("\"name\":"))
        assertTrue(json.contains("\"Test API\""))
        assertTrue(json.contains("\"method\":"))
    }

    // ==================== generateUniqueRefId ====================

    @Test
    fun `generateUniqueRefId with prefix`() {
        val id = generateUniqueRefId("coll")
        assertTrue(id.startsWith("coll_"))
    }

    @Test
    fun `generateUniqueRefId without prefix`() {
        val id = generateUniqueRefId()
        assertFalse(id.startsWith("_"))
        assertTrue(id.contains("_"))
    }

    @Test
    fun `generateUniqueRefId generates unique ids`() {
        val id1 = generateUniqueRefId("req")
        val id2 = generateUniqueRefId("req")
        assertNotEquals(id1, id2)
    }

    // ==================== ParameterBinding ====================

    @Test
    fun `ParameterBinding types`() {
        assertTrue(ParameterBinding.Query is ParameterBinding.Query)
        assertTrue(ParameterBinding.Path is ParameterBinding.Path)
        assertTrue(ParameterBinding.Header is ParameterBinding.Header)
        assertTrue(ParameterBinding.Cookie is ParameterBinding.Cookie)
        assertTrue(ParameterBinding.Body is ParameterBinding.Body)
        assertTrue(ParameterBinding.Form is ParameterBinding.Form)
        assertTrue(ParameterBinding.Ignored is ParameterBinding.Ignored)
    }

    // ==================== HttpMethod ====================

    @Test
    fun `HttpMethod fromSpring`() {
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
    fun `HttpMethod fromSpring case insensitive`() {
        assertEquals(HttpMethod.GET, HttpMethod.fromSpring("get"))
        assertEquals(HttpMethod.POST, HttpMethod.fromSpring("post"))
    }

    // ==================== ParameterType ====================

    @Test
    fun `ParameterType fromTypeName null returns TEXT`() {
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName(null))
    }

    @Test
    fun `ParameterType fromTypeName blank returns TEXT`() {
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName(""))
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName("  "))
    }

    @Test
    fun `ParameterType fromTypeName file returns FILE`() {
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("MultipartFile"))
    }

    @Test
    fun `ParameterType rawType`() {
        assertEquals("text", ParameterType.TEXT.rawType())
        assertEquals("file", ParameterType.FILE.rawType())
    }

    // ==================== ApiEndpoint helpers ====================

    @Test
    fun `ApiEndpoint isHttp`() {
        val endpoint = ApiEndpoint(metadata = HttpMetadata(path = "/test", method = HttpMethod.GET))
        assertTrue(endpoint.isHttp)
        assertFalse(endpoint.isGrpc)
    }

    @Test
    fun `ApiEndpoint httpMetadata`() {
        val httpMeta = HttpMetadata(path = "/test", method = HttpMethod.GET)
        val endpoint = ApiEndpoint(metadata = httpMeta)
        assertNotNull(endpoint.httpMetadata)
        assertEquals("/test", endpoint.httpMetadata?.path)
    }

    @Test
    fun `ApiEndpoint path for HTTP`() {
        val endpoint = ApiEndpoint(metadata = HttpMetadata(path = "/api/users", method = HttpMethod.GET))
        assertEquals("/api/users", endpoint.path)
    }

    @Test
    fun `ApiEndpoint setParam adds query param`() {
        val endpoint = ApiEndpoint(metadata = HttpMetadata(path = "/test", method = HttpMethod.GET))
        endpoint.setParam("page", "1", true, "Page number")
        assertEquals(1, endpoint.httpMetadata?.parameters?.size)
        assertEquals("page", endpoint.httpMetadata?.parameters?.first()?.name)
        assertEquals(ParameterBinding.Query, endpoint.httpMetadata?.parameters?.first()?.binding)
    }

    @Test
    fun `ApiEndpoint setFormParam adds form param`() {
        val endpoint = ApiEndpoint(metadata = HttpMetadata(path = "/test", method = HttpMethod.POST))
        endpoint.setFormParam("username", "alice", true, "Username")
        assertEquals(1, endpoint.httpMetadata?.parameters?.size)
        assertEquals(ParameterBinding.Form, endpoint.httpMetadata?.parameters?.first()?.binding)
    }

    @Test
    fun `ApiEndpoint setPathParam adds path param`() {
        val endpoint = ApiEndpoint(metadata = HttpMetadata(path = "/users/{id}", method = HttpMethod.GET))
        endpoint.setPathParam("id", "1", "User ID")
        assertEquals(1, endpoint.httpMetadata?.parameters?.size)
        assertEquals(ParameterBinding.Path, endpoint.httpMetadata?.parameters?.first()?.binding)
        assertTrue(endpoint.httpMetadata?.parameters?.first()?.required == true)
    }

    @Test
    fun `ApiEndpoint setHeader adds header`() {
        val endpoint = ApiEndpoint(metadata = HttpMetadata(path = "/test", method = HttpMethod.GET))
        endpoint.setHeader("Authorization", "Bearer token", true, "Auth token")
        assertEquals(1, endpoint.httpMetadata?.headers?.size)
        assertEquals("Authorization", endpoint.httpMetadata?.headers?.first()?.name)
    }

    @Test
    fun `ApiEndpoint appendDesc appends description`() {
        val endpoint = ApiEndpoint(name = "Test", description = "Original", metadata = HttpMetadata(path = "/test", method = HttpMethod.GET))
        endpoint.appendDesc(" - extra")
        assertEquals("Original - extra", endpoint.description)
    }

    @Test
    fun `ApiEndpoint appendDesc with null description`() {
        val endpoint = ApiEndpoint(name = "Test", description = null, metadata = HttpMetadata(path = "/test", method = HttpMethod.GET))
        endpoint.appendDesc("new desc")
        assertEquals("new desc", endpoint.description)
    }

    // ==================== HttpMetadata ====================

    @Test
    fun `HttpMetadata defaults`() {
        val meta = HttpMetadata(path = "/test", method = HttpMethod.GET)
        assertTrue(meta.parameters.isEmpty())
        assertTrue(meta.headers.isEmpty())
        assertNull(meta.contentType)
        assertNull(meta.body)
        assertEquals("HTTP", meta.protocol)
    }

    @Test
    fun `HttpMetadata with all fields`() {
        val meta = HttpMetadata(
            path = "/api/users",
            method = HttpMethod.POST,
            parameters = mutableListOf(
                ApiParameter(name = "name", binding = ParameterBinding.Form, example = "Alice")
            ),
            headers = mutableListOf(
                ApiHeader(name = "Content-Type", value = "application/json")
            ),
            contentType = "application/json",
            body = null
        )
        assertEquals(HttpMethod.POST, meta.method)
        assertEquals(1, meta.parameters.size)
        assertEquals(1, meta.headers.size)
        assertEquals("application/json", meta.contentType)
    }

    // ==================== ApiParameter ====================

    @Test
    fun `ApiParameter defaults`() {
        val param = ApiParameter(name = "test")
        assertEquals("test", param.name)
        assertEquals(ParameterType.TEXT, param.type)
        assertFalse(param.required)
        assertNull(param.binding)
        assertNull(param.defaultValue)
        assertNull(param.description)
        assertNull(param.example)
        assertNull(param.enumValues)
    }

    // ==================== ApiHeader ====================

    @Test
    fun `ApiHeader defaults`() {
        val header = ApiHeader(name = "X-Custom")
        assertEquals("X-Custom", header.name)
        assertNull(header.value)
        assertNull(header.description)
        assertNull(header.example)
        assertFalse(header.required)
    }
}
