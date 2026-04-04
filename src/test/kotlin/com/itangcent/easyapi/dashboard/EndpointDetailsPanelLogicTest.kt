package com.itangcent.easyapi.dashboard

import com.itangcent.easyapi.http.FormParam
import org.junit.Assert.*
import org.junit.Test

class EndpointDetailsPanelLogicTest {

    // ── formatJson ───────────────────────────────────────────────────────────

    @Test
    fun `formatJson returns blank input unchanged`() {
        assertEquals("", EndpointDetailsPanelLogic.formatJson(""))
        assertEquals("   ", EndpointDetailsPanelLogic.formatJson("   "))
    }

    @Test
    fun `formatJson pretty-prints compact JSON`() {
        val compact = """{"name":"Alice","age":30}"""
        val result = EndpointDetailsPanelLogic.formatJson(compact)
        assertTrue(result.contains("\n"))
        assertTrue(result.contains("\"name\""))
        assertTrue(result.contains("\"Alice\""))
    }

    @Test
    fun `formatJson returns original string for invalid JSON`() {
        val invalid = "not json at all"
        assertEquals(invalid, EndpointDetailsPanelLogic.formatJson(invalid))
    }

    @Test
    fun `formatJson handles already-formatted JSON`() {
        val pretty = "{\n  \"key\": \"value\"\n}"
        val result = EndpointDetailsPanelLogic.formatJson(pretty)
        assertTrue(result.contains("\"key\""))
    }

    // ── resolvePath ──────────────────────────────────────────────────────────

    @Test
    fun `resolvePath substitutes single variable`() {
        val result = EndpointDetailsPanelLogic.resolvePath("/users/{id}", listOf("id" to "42"))
        assertEquals("/users/42", result)
    }

    @Test
    fun `resolvePath substitutes multiple variables`() {
        val result = EndpointDetailsPanelLogic.resolvePath(
            "/orgs/{org}/repos/{repo}",
            listOf("org" to "acme", "repo" to "api")
        )
        assertEquals("/orgs/acme/repos/api", result)
    }

    @Test
    fun `resolvePath URL-encodes values with special characters`() {
        val result = EndpointDetailsPanelLogic.resolvePath("/search/{q}", listOf("q" to "hello world"))
        assertEquals("/search/hello+world", result)
    }

    @Test
    fun `resolvePath skips empty key or value`() {
        val result = EndpointDetailsPanelLogic.resolvePath("/users/{id}", listOf("" to "42", "id" to ""))
        assertEquals("/users/{id}", result)
    }

    @Test
    fun `resolvePath leaves unmatched variables intact`() {
        val result = EndpointDetailsPanelLogic.resolvePath("/users/{id}", emptyList())
        assertEquals("/users/{id}", result)
    }

    // ── buildFormParams ──────────────────────────────────────────────────────

    @Test
    fun `buildFormParams skips rows with empty name`() {
        val rows = listOf(Triple("", "value", false))
        assertTrue(EndpointDetailsPanelLogic.buildFormParams(rows).isEmpty())
    }

    @Test
    fun `buildFormParams creates Text params for non-file rows`() {
        val rows = listOf(
            Triple("username", "alice", false),
            Triple("age", "30", false)
        )
        val params = EndpointDetailsPanelLogic.buildFormParams(rows)
        assertEquals(2, params.size)
        assertTrue(params[0] is FormParam.Text)
        assertEquals("username", (params[0] as FormParam.Text).name)
        assertEquals("alice", (params[0] as FormParam.Text).value)
    }

    @Test
    fun `buildFormParams skips file row with blank path`() {
        val rows = listOf(Triple("file", "", true))
        assertTrue(EndpointDetailsPanelLogic.buildFormParams(rows).isEmpty())
    }

    @Test
    fun `buildFormParams skips file row when loader returns null`() {
        val rows = listOf(Triple("file", "/nonexistent/file.txt", true))
        val params = EndpointDetailsPanelLogic.buildFormParams(rows, fileLoader = { null })
        assertTrue(params.isEmpty())
    }

    @Test
    fun `buildFormParams creates File param when loader succeeds`() {
        val fakeBytes = "hello".toByteArray()
        val rows = listOf(Triple("upload", "/tmp/hello.txt", true))
        val params = EndpointDetailsPanelLogic.buildFormParams(rows, fileLoader = { "hello.txt" to fakeBytes })
        assertEquals(1, params.size)
        val fileParam = params[0] as FormParam.File
        assertEquals("upload", fileParam.name)
        assertEquals("hello.txt", fileParam.fileName)
        assertArrayEquals(fakeBytes, fileParam.bytes)
    }

    @Test
    fun `buildFormParams mixes text and file rows`() {
        val fakeBytes = byteArrayOf(1, 2, 3)
        val rows = listOf(
            Triple("note", "hello", false),
            Triple("attachment", "/tmp/doc.pdf", true)
        )
        val params = EndpointDetailsPanelLogic.buildFormParams(rows, fileLoader = { "doc.pdf" to fakeBytes })
        assertEquals(2, params.size)
        assertTrue(params[0] is FormParam.Text)
        assertTrue(params[1] is FormParam.File)
    }
}
