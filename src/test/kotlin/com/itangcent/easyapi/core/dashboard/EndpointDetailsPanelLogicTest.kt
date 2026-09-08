package com.itangcent.easyapi.core.dashboard

import com.itangcent.easyapi.core.http.FormParam
import org.junit.Assert.*
import org.junit.Test

class EndpointDetailsPanelLogicTest {

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

    // ── mergeJsonBody ───────────────────────────────────────────────────────

    private fun normalize(json: String?) = json?.replace("\\s".toRegex(), "")

    @Test
    fun `mergeJsonBody returns model body when there is no cached body`() {
        val model = """{"toolType":"","toolId":""}"""
        assertEquals(model, normalize(EndpointDetailsPanelLogic.mergeJsonBody(model, null)))
        assertEquals(model, normalize(EndpointDetailsPanelLogic.mergeJsonBody(model, "  ")))
    }

    @Test
    fun `mergeJsonBody returns cached body when the model has no body`() {
        val cached = """{"a":1}"""
        assertEquals(cached, normalize(EndpointDetailsPanelLogic.mergeJsonBody(null, cached)))
        assertEquals(cached, normalize(EndpointDetailsPanelLogic.mergeJsonBody("", cached)))
    }

    @Test
    fun `mergeJsonBody adds fields newly added to the model`() {
        val model = """{"toolType":"","toolId":"","scope":""}"""
        val cached = """{"toolType":"A","toolId":"B"}"""
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody(model, cached))
        assertEquals("""{"toolType":"A","toolId":"B","scope":""}""", merged)
    }

    @Test
    fun `mergeJsonBody drops fields removed from the model`() {
        val model = """{"toolType":""}"""
        val cached = """{"toolType":"A","removed":"X"}"""
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody(model, cached))
        assertEquals("""{"toolType":"A"}""", merged)
    }

    @Test
    fun `mergeJsonBody keeps user edited values including nulls and falsy values`() {
        val model = """{"a":"","b":"","c":""}"""
        val cached = """{"a":"edited","b":null,"c":0}"""
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody(model, cached))
        assertEquals("""{"a":"edited","b":null,"c":0}""", merged)
    }

    @Test
    fun `mergeJsonBody follows the model key order`() {
        val model = """{"z":"","a":""}"""
        val cached = """{"a":"1","z":"2"}"""
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody(model, cached))
        assertEquals("""{"z":"2","a":"1"}""", merged)
    }

    @Test
    fun `mergeJsonBody merges nested objects recursively`() {
        val model = """{"data":{"x":"","y":""},"top":""}"""
        val cached = """{"data":{"x":"1"},"top":"T"}"""
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody(model, cached))
        assertEquals("""{"data":{"x":"1","y":""},"top":"T"}""", merged)
    }

    @Test
    fun `mergeJsonBody keeps cached arrays as a whole`() {
        val model = """{"items":[]}"""
        val cached = """{"items":[1,2,3]}"""
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody(model, cached))
        assertEquals("""{"items":[1,2,3]}""", merged)
    }

    @Test
    fun `mergeJsonBody falls back to cached body for non-object JSON`() {
        val cached = """["a","b"]"""
        assertEquals(cached, normalize(EndpointDetailsPanelLogic.mergeJsonBody("""{"a":1}""", cached)))
    }

    @Test
    fun `mergeJsonBody falls back to cached body for non-JSON payloads`() {
        assertEquals("<xml/>", EndpointDetailsPanelLogic.mergeJsonBody("""{"a":1}""", "<xml/>"))
        assertEquals("{broken", EndpointDetailsPanelLogic.mergeJsonBody("""{"a":1}""", "{broken"))
    }

    // ── mergeJsonBody3 (three-way) ───────────────────────────────────────────

    @Test
    fun `mergeJsonBody3 falls back to two-way when baseBody is blank`() {
        val model = """{"a":1,"b":2}"""
        val cached = """{"a":10}"""
        // No base -> identical to mergeJsonBody: model wins key set, cache wins values.
        assertEquals(
            normalize(EndpointDetailsPanelLogic.mergeJsonBody(model, cached)),
            normalize(EndpointDetailsPanelLogic.mergeJsonBody3(null, model, cached))
        )
    }

    @Test
    fun `mergeJsonBody3 keeps a field the user deleted`() {
        val base = """{"a":1,"b":2}"""
        val model = """{"a":1,"b":2}"""
        val cached = """{"a":10}""" // user deleted b
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody3(base, model, cached))!!
        assertTrue(merged.contains("\"a\""))
        assertFalse("user-deleted field must stay deleted", merged.contains("\"b\""))
    }

    @Test
    fun `mergeJsonBody3 drops a field the source deleted`() {
        val base = """{"a":1,"b":2}"""
        val model = """{"a":1}""" // source deleted b
        val cached = """{"a":10,"b":2}""" // user never touched b
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody3(base, model, cached))!!
        assertTrue(merged.contains("\"a\""))
        assertFalse("source-deleted field must disappear", merged.contains("\"b\""))
    }

    @Test
    fun `mergeJsonBody3 carries a field the source added`() {
        val base = """{"a":1}"""
        val model = """{"a":1,"b":2}""" // source added b
        val cached = """{"a":10}"""
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody3(base, model, cached))!!
        assertTrue(merged.contains("\"a\""))
        assertTrue("source-added field must be carried over", merged.contains("\"b\""))
    }

    @Test
    fun `mergeJsonBody3 recurses into nested objects`() {
        val base = """{"outer":{"x":1,"y":2}}"""
        val model = """{"outer":{"x":1,"y":2,"z":3}}""" // source added z
        val cached = """{"outer":{"x":10}}""" // user edited x, deleted y
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody3(base, model, cached))!!
        assertTrue(merged.contains("\"x\""))
        assertTrue(merged.contains("10"))
        assertTrue("nested source-added field must appear", merged.contains("\"z\""))
        assertFalse("nested user-deleted field must stay deleted", merged.contains("\"y\""))
    }

    @Test
    fun `mergeJsonBody3 keeps cached scalar over model`() {
        val base = """{"a":1,"b":2}"""
        val model = """{"a":1,"b":2}"""
        val cached = """{"a":10,"b":20}"""
        val merged = normalize(EndpointDetailsPanelLogic.mergeJsonBody3(base, model, cached))!!
        assertTrue(merged.contains("10"))
        assertTrue(merged.contains("20"))
    }

    // ── formatCategoryOf ────────────────────────────────────────────────────

    @Test
    fun `formatCategoryOf classifies xml media types`() {
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.XML, EndpointDetailsPanelLogic.formatCategoryOf("application/xml"))
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.XML, EndpointDetailsPanelLogic.formatCategoryOf("text/xml"))
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.XML, EndpointDetailsPanelLogic.formatCategoryOf("application/soap+xml"))
        // Case-insensitive
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.XML, EndpointDetailsPanelLogic.formatCategoryOf("TEXT/XML"))
    }

    @Test
    fun `formatCategoryOf classifies html media types`() {
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.HTML, EndpointDetailsPanelLogic.formatCategoryOf("text/html"))
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.HTML, EndpointDetailsPanelLogic.formatCategoryOf("application/xhtml+xml"))
    }

    @Test
    fun `formatCategoryOf falls back to JSON`() {
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.JSON, EndpointDetailsPanelLogic.formatCategoryOf("application/json"))
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.JSON, EndpointDetailsPanelLogic.formatCategoryOf("text/plain"))
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.JSON, EndpointDetailsPanelLogic.formatCategoryOf(null))
        assertEquals(EndpointDetailsPanelLogic.FormatCategory.JSON, EndpointDetailsPanelLogic.formatCategoryOf("  "))
    }

    // ── formatByContentType ─────────────────────────────────────────────────

    @Test
    fun `formatByContentType formats xml bodies`() {
        val result = EndpointDetailsPanelLogic.formatByContentType("<root><item>v</item></root>", "application/xml")
        assertTrue(result.contains("<root>"))
        assertTrue(result.contains("\n"))
    }

    @Test
    fun `formatByContentType formats html bodies`() {
        val result = EndpointDetailsPanelLogic.formatByContentType("<div> <p>text</p> </div>", "text/html")
        assertTrue(result.contains("<div>"))
        assertTrue(result.contains("\n"))
    }

    @Test
    fun `formatByContentType formats json by default`() {
        val result = EndpointDetailsPanelLogic.formatByContentType("""{"a":1}""", null)
        assertTrue(result.contains("\n"))
    }

    // ── truncateForEditor ───────────────────────────────────────────────────

    @Test
    fun `truncateForEditor keeps text within limit unchanged`() {
        val text = "short body"
        assertEquals(text, EndpointDetailsPanelLogic.truncateForEditor(text))
    }

    @Test
    fun `truncateForEditor replaces oversized text with placeholder`() {
        val huge = "x".repeat(EndpointDetailsPanelLogic.MAX_EDITOR_RESPONSE_CHARS + 1)
        val result = EndpointDetailsPanelLogic.truncateForEditor(huge)
        assertTrue(result.length < huge.length)
        assertTrue(result.contains("too large"))
    }
}
