package com.itangcent.easyapi.core.dashboard

import com.itangcent.easyapi.core.http.FormParam
import org.junit.Assert.*
import org.junit.Test

class EndpointDetailsPanelLogicBuildFormParamsTest {

    // ── buildFormParams ──────────────────────────────────────────────────────

    @Test
    fun `buildFormParams with text fields`() {
        val rows = listOf(
            Triple("username", "alice", false),
            Triple("password", "secret", false)
        )
        val result = EndpointDetailsPanelLogic.buildFormParams(rows)
        assertEquals(2, result.size)
        assertEquals(FormParam.Text("username", "alice"), result[0])
        assertEquals(FormParam.Text("password", "secret"), result[1])
    }

    @Test
    fun `buildFormParams skips rows with empty name`() {
        val rows = listOf(
            Triple("", "value", false),
            Triple("valid", "value", false)
        )
        val result = EndpointDetailsPanelLogic.buildFormParams(rows)
        assertEquals(1, result.size)
        assertEquals(FormParam.Text("valid", "value"), result[0])
    }

    @Test
    fun `buildFormParams skips file rows with blank value`() {
        val rows = listOf(
            Triple("file", "", true),
            Triple("name", "value", false)
        )
        val result = EndpointDetailsPanelLogic.buildFormParams(rows)
        assertEquals(1, result.size)
        assertEquals(FormParam.Text("name", "value"), result[0])
    }

    @Test
    fun `buildFormParams skips file rows when fileLoader returns null`() {
        val rows = listOf(
            Triple("file", "/nonexistent/path", true)
        )
        val result = EndpointDetailsPanelLogic.buildFormParams(rows) { _ -> null }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildFormParams creates file param from fileLoader`() {
        val rows = listOf(
            Triple("upload", "/path/to/file.txt", true)
        )
        val result = EndpointDetailsPanelLogic.buildFormParams(rows) { _ ->
            "file.txt" to "content".toByteArray()
        }
        assertEquals(1, result.size)
        val fileParam = result[0] as FormParam.File
        assertEquals("upload", fileParam.name)
        assertEquals("file.txt", fileParam.fileName)
        assertEquals("content", String(fileParam.bytes))
    }

    @Test
    fun `buildFormParams uses application octet-stream when mime type detection fails`() {
        val rows = listOf(
            Triple("upload", "/path/to/file.dat", true)
        )
        val result = EndpointDetailsPanelLogic.buildFormParams(rows) { _ ->
            "file.dat" to ByteArray(0)
        }
        assertEquals(1, result.size)
        val fileParam = result[0] as FormParam.File
        assertEquals("application/octet-stream", fileParam.contentType)
    }

    @Test
    fun `buildFormParams with empty rows returns empty list`() {
        val result = EndpointDetailsPanelLogic.buildFormParams(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `buildFormParams with custom fileLoader providing mime type`() {
        val rows = listOf(
            Triple("upload", "/path/to/image.png", true)
        )
        val result = EndpointDetailsPanelLogic.buildFormParams(rows) { _ ->
            "image.png" to ByteArray(0)
        }
        assertEquals(1, result.size)
        val fileParam = result[0] as FormParam.File
        // The mime type is detected by Files.probeContentType which may return
        // image/png or application/octet-stream depending on the environment
        assertNotNull(fileParam.contentType)
    }

    @Test
    fun `buildFormParams mixed text and file rows`() {
        val rows = listOf(
            Triple("name", "test", false),
            Triple("file", "/path/to/data.json", true),
            Triple("description", "a file upload", false)
        )
        val result = EndpointDetailsPanelLogic.buildFormParams(rows) { _ ->
            "data.json" to "{}".toByteArray()
        }
        assertEquals(3, result.size)
        assertTrue(result[0] is FormParam.Text)
        assertTrue(result[1] is FormParam.File)
        assertTrue(result[2] is FormParam.Text)
    }

    // ── formatJson additional tests ──────────────────────────────────────────

    @Test
    fun `formatJson handles nested JSON`() {
        val nested = """{"user":{"name":"Alice","address":{"city":"NYC"}}}"""
        val result = EndpointDetailsPanelLogic.formatJson(nested)
        assertTrue(result.contains("\"user\""))
        assertTrue(result.contains("\"name\""))
        assertTrue(result.contains("\"Alice\""))
        assertTrue(result.contains("\"city\""))
        assertTrue(result.contains("NYC"))
    }

    @Test
    fun `formatJson handles JSON array`() {
        val array = """[1,2,3]"""
        val result = EndpointDetailsPanelLogic.formatJson(array)
        assertTrue(result.contains("1"))
        assertTrue(result.contains("2"))
        assertTrue(result.contains("3"))
    }

    @Test
    fun `formatJson handles null JSON value`() {
        val json = """{"key":null}"""
        val result = EndpointDetailsPanelLogic.formatJson(json)
        // Gson without serializeNulls may omit null values; just verify it doesn't crash
        assertNotNull(result)
        assertFalse(result.isBlank())
    }

    @Test
    fun `formatJson handles boolean JSON values`() {
        val json = """{"active":true,"deleted":false}"""
        val result = EndpointDetailsPanelLogic.formatJson(json)
        assertTrue(result.contains("true"))
        assertTrue(result.contains("false"))
    }

    // ── resolvePath additional tests ─────────────────────────────────────────

    @Test
    fun `resolvePath with no params returns template unchanged`() {
        val result = EndpointDetailsPanelLogic.resolvePath("/users/{id}", emptyList())
        assertEquals("/users/{id}", result)
    }

    @Test
    fun `resolvePath with empty key skips substitution`() {
        val result = EndpointDetailsPanelLogic.resolvePath(
            "/users/{id}",
            listOf("" to "42")
        )
        assertEquals("/users/{id}", result)
    }

    @Test
    fun `resolvePath with empty value skips substitution`() {
        val result = EndpointDetailsPanelLogic.resolvePath(
            "/users/{id}",
            listOf("id" to "")
        )
        assertEquals("/users/{id}", result)
    }

    @Test
    fun `resolvePath URL-encodes special characters`() {
        val result = EndpointDetailsPanelLogic.resolvePath(
            "/search/{query}",
            listOf("query" to "hello world & more")
        )
        assertFalse(result.contains("hello world"))
        assertTrue(result.contains("hello+world"))
    }

    @Test
    fun `resolvePath with multiple same-key params replaces all occurrences`() {
        // Path template with same key appearing twice
        val result = EndpointDetailsPanelLogic.resolvePath(
            "/users/{id}/copy/{id}",
            listOf("id" to "42")
        )
        assertEquals("/users/42/copy/42", result)
    }
}
