package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.exporter.postman.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PostmanFormatter pure logic methods.
 * Methods that depend on Project/RuleEngine are tested via integration tests.
 */
class PostmanFormatterLogicTest {

    // ==================== parsePath ====================

    @Test
    fun `parsePath splits on forward slash`() {
        val result = PostmanFormatter.parsePath("/users/list")
        assertEquals(listOf("users", "list"), result)
    }

    @Test
    fun `parsePath trims leading and trailing slashes`() {
        val result = PostmanFormatter.parsePath("/users/list/")
        assertEquals(listOf("users", "list"), result)
    }

    @Test
    fun `parsePath handles single segment`() {
        val result = PostmanFormatter.parsePath("/users")
        assertEquals(listOf("users"), result)
    }

    @Test
    fun `parsePath handles empty string`() {
        val result = PostmanFormatter.parsePath("")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `parsePath handles root path`() {
        val result = PostmanFormatter.parsePath("/")
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun `parsePath resolves path variables with braces`() {
        val result = PostmanFormatter.parsePath("/users/{userId}/posts")
        assertEquals(listOf("users", ":userId", "posts"), result)
    }

    @Test
    fun `parsePath resolves path variables with braces and colon`() {
        val result = PostmanFormatter.parsePath("/users/{userId:regex}/posts")
        assertEquals(listOf("users", ":userId", "posts"), result)
    }

    @Test
    fun `parsePath handles deep nesting`() {
        val result = PostmanFormatter.parsePath("/api/v1/users/{id}/posts")
        assertEquals(listOf("api", "v1", "users", ":id", "posts"), result)
    }

    // ==================== buildScript ====================

    @Test
    fun `buildScript returns null for null content`() {
        val formatter = createFormatter()
        assertNull(formatter.buildScript("prerequest", null))
    }

    @Test
    fun `buildScript returns null for blank content`() {
        val formatter = createFormatter()
        assertNull(formatter.buildScript("prerequest", ""))
    }

    @Test
    fun `buildScript returns null for whitespace-only content`() {
        val formatter = createFormatter()
        assertNull(formatter.buildScript("prerequest", "   \n  \n  "))
    }

    @Test
    fun `buildScript creates prerequest event`() {
        val formatter = createFormatter()
        val event = formatter.buildScript("prerequest", "pm.environment.set('token', 'abc')")
        assertNotNull(event)
        assertEquals("prerequest", event!!.listen)
        assertEquals(listOf("pm.environment.set('token', 'abc')"), event.script.exec)
    }

    @Test
    fun `buildScript creates test event`() {
        val formatter = createFormatter()
        val event = formatter.buildScript("test", "pm.response.to.have.status(200)")
        assertNotNull(event)
        assertEquals("test", event!!.listen)
        assertEquals(listOf("pm.response.to.have.status(200)"), event.script.exec)
    }

    @Test
    fun `buildScript splits multiline content and filters blank lines`() {
        val formatter = createFormatter()
        val content = """
            |var token = pm.environment.get('token');
            |
            |pm.request.headers.add({key: 'Authorization', value: 'Bearer ' + token});
        """.trimMargin()
        val event = formatter.buildScript("prerequest", content)
        assertNotNull(event)
        assertEquals(2, event!!.script.exec.size)
        assertEquals("var token = pm.environment.get('token');", event.script.exec[0])
        assertEquals("pm.request.headers.add({key: 'Authorization', value: 'Bearer ' + token});", event.script.exec[1])
    }

    // ==================== PostmanFormatOptions ====================

    @Test
    fun `PostmanFormatOptions defaults`() {
        val opts = PostmanFormatOptions()
        assertFalse(opts.buildExample)
        assertTrue(opts.autoMergeScript)
        assertFalse(opts.wrapCollection)
        assertEquals("{{host}}", opts.defaultHost)
        assertTrue(opts.appendTimestamp)
    }

    @Test
    fun `PostmanFormatOptions custom values`() {
        val opts = PostmanFormatOptions(
            buildExample = true,
            autoMergeScript = false,
            wrapCollection = true,
            defaultHost = "https://api.example.com",
            appendTimestamp = false
        )
        assertTrue(opts.buildExample)
        assertFalse(opts.autoMergeScript)
        assertTrue(opts.wrapCollection)
        assertEquals("https://api.example.com", opts.defaultHost)
        assertFalse(opts.appendTimestamp)
    }

    // ==================== PostmanEndpointContext ====================

    @Test
    fun `PostmanEndpointContext defaults`() {
        val ctx = PostmanEndpointContext(
            endpoint = com.itangcent.easyapi.exporter.model.ApiEndpoint(
                metadata = com.itangcent.easyapi.exporter.model.HttpMetadata(
                    path = "/test",
                    method = com.itangcent.easyapi.exporter.model.HttpMethod.GET
                )
            )
        )
        assertTrue(ctx.responses.isEmpty())
        assertNull(ctx.preRequestScript)
        assertNull(ctx.testScript)
        assertNull(ctx.psiElement)
        assertNull(ctx.psiClass)
    }

    // ==================== PostmanResponseData ====================

    @Test
    fun `PostmanResponseData defaults`() {
        val data = PostmanResponseData()
        assertNull(data.name)
        assertNull(data.statusCode)
        assertTrue(data.headers.isEmpty())
        assertNull(data.body)
        assertNull(data.description)
    }

    // ==================== PostmanHeaderData ====================

    @Test
    fun `PostmanHeaderData defaults`() {
        val data = PostmanHeaderData(name = "Content-Type")
        assertEquals("Content-Type", data.name)
        assertNull(data.value)
    }

    // ==================== PostmanCollection ====================

    @Test
    fun `PostmanCollection structure`() {
        val coll = PostmanCollection(
            info = CollectionInfo(name = "Test API"),
            item = listOf(
                PostmanItem(name = "Get Users", request = PostmanRequest(method = "GET", url = PostmanUrl(raw = "http://example.com/api/users")))
            )
        )
        assertEquals("Test API", coll.info.name)
        assertEquals(1, coll.item.size)
    }

    // ==================== PostmanItem ====================

    @Test
    fun `PostmanItem with request`() {
        val item = PostmanItem(
            name = "Get Users",
            request = PostmanRequest(method = "GET", url = PostmanUrl(raw = "http://example.com/api/users")),
            response = emptyList(),
            event = emptyList()
        )
        assertEquals("Get Users", item.name)
        assertEquals("GET", item.request?.method)
    }

    @Test
    fun `PostmanItem as folder`() {
        val item = PostmanItem(
            name = "Users",
            item = listOf(
                PostmanItem(name = "Get User", request = PostmanRequest(method = "GET", url = PostmanUrl(raw = "http://example.com/api/users")))
            )
        )
        assertEquals("Users", item.name)
        assertEquals(1, item.item.size)
    }

    // ==================== PostmanRequest ====================

    @Test
    fun `PostmanRequest defaults`() {
        val req = PostmanRequest(method = "GET", url = PostmanUrl(raw = "http://example.com/api"))
        assertEquals("GET", req.method)
        assertTrue(req.header.isEmpty())
        assertNull(req.body)
        assertEquals("http://example.com/api", req.url.raw)
    }

    // ==================== PostmanBody ====================

    @Test
    fun `PostmanBody raw JSON`() {
        val body = PostmanBody(
            mode = "raw",
            raw = """{"key":"value"}""",
            options = mapOf("raw" to mapOf("language" to "json"))
        )
        assertEquals("raw", body.mode)
        assertEquals("""{"key":"value"}""", body.raw)
    }

    @Test
    fun `PostmanBody urlencoded`() {
        val body = PostmanBody(
            mode = "urlencoded",
            urlencoded = listOf(
                PostmanFormParam(key = "name", value = "Alice", type = "text")
            )
        )
        assertEquals("urlencoded", body.mode)
        assertEquals(1, body.urlencoded!!.size)
    }

    @Test
    fun `PostmanBody formdata`() {
        val body = PostmanBody(
            mode = "formdata",
            formdata = listOf(
                PostmanFormParam(key = "file", value = "test.txt", type = "file")
            )
        )
        assertEquals("formdata", body.mode)
        assertEquals(1, body.formdata!!.size)
    }

    // ==================== PostmanUrl ====================

    @Test
    fun `PostmanUrl structure`() {
        val url = PostmanUrl(
            raw = "https://api.example.com/users?page=1",
            host = listOf("https://api.example.com"),
            path = listOf("users"),
            query = listOf(PostmanQuery(key = "page", value = "1", equals = true)),
            variable = emptyList()
        )
        assertEquals("https://api.example.com/users?page=1", url.raw)
        assertEquals(1, url.query.size)
    }

    // ==================== PostmanQuery ====================

    @Test
    fun `PostmanQuery defaults`() {
        val q = PostmanQuery(key = "page", value = "1", equals = true)
        assertEquals("page", q.key)
        assertEquals("1", q.value)
        assertTrue(q.equals)
        assertNull(q.description)
    }

    // ==================== PostmanPathVariable ====================

    @Test
    fun `PostmanPathVariable defaults`() {
        val pv = PostmanPathVariable(key = "id", value = "123")
        assertEquals("id", pv.key)
        assertEquals("123", pv.value)
        assertNull(pv.description)
    }

    // ==================== PostmanEvent ====================

    @Test
    fun `PostmanEvent prerequest`() {
        val event = PostmanEvent(
            listen = "prerequest",
            script = PostmanScript(exec = listOf("pm.environment.set('token', 'abc')"))
        )
        assertEquals("prerequest", event.listen)
        assertEquals(1, event.script.exec.size)
    }

    @Test
    fun `PostmanEvent test`() {
        val event = PostmanEvent(
            listen = "test",
            script = PostmanScript(exec = listOf("pm.response.to.have.status(200)"))
        )
        assertEquals("test", event.listen)
    }

    // ==================== PostmanFormParam ====================

    @Test
    fun `PostmanFormParam defaults`() {
        val param = PostmanFormParam(key = "name", value = "Alice", type = "text")
        assertEquals("name", param.key)
        assertEquals("Alice", param.value)
        assertEquals("text", param.type)
        assertNull(param.description)
    }

    // ==================== PostmanResponse ====================

    @Test
    fun `PostmanResponse structure`() {
        val response = PostmanResponse(
            name = "Success Example",
            status = "OK",
            code = 200,
            header = emptyList(),
            body = """{"id":1}""",
            originalRequest = PostmanRequest(method = "GET", url = PostmanUrl(raw = "http://example.com/api"))
        )
        assertEquals("Success Example", response.name)
        assertEquals(200, response.code)
        assertEquals("OK", response.status)
    }

    // ==================== CollectionInfo ====================

    @Test
    fun `CollectionInfo structure`() {
        val info = CollectionInfo(name = "My API", description = "Test collection")
        assertEquals("My API", info.name)
        assertEquals("Test collection", info.description)
    }

    private fun createFormatter(): PostmanFormatter {
        // Use a fixed time provider for deterministic tests
        return PostmanFormatter(
            project = mockProject(),
            options = PostmanFormatOptions(appendTimestamp = false),
            systemTimeProvider = { 1700000000000L }
        )
    }

    private fun mockProject(): com.intellij.openapi.project.Project {
        // For logic-only tests, we don't actually use the project
        // But PostmanFormatter requires it. We'll use a mock approach.
        return org.mockito.Mockito.mock(com.intellij.openapi.project.Project::class.java)
    }
}
