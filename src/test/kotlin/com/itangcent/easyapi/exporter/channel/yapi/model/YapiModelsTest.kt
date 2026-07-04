package com.itangcent.easyapi.exporter.channel.yapi.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for YAPI model classes in [com.itangcent.easyapi.exporter.channel.yapi.model].
 *
 * Covers [YapiApiDoc.copy], data class semantics of [MutableYapiApiDoc],
 * and [Extension] support on all model types.
 */
class YapiModelsTest {

    // -------------------------------------------------------------------------
    // MutableYapiApiDoc.create
    // -------------------------------------------------------------------------

    @Test
    fun `create builds instance with required fields`() {
        val doc = MutableYapiApiDoc.create(
            title = "Get User",
            path = "/api/users/{id}",
            method = "get"
        )
        assertEquals("Get User", doc.title)
        assertEquals("/api/users/{id}", doc.path)
        assertEquals("get", doc.method)
    }

    @Test
    fun `create respects all optional fields`() {
        val doc = MutableYapiApiDoc.create(
            title = "T",
            path = "/p",
            method = "post",
            desc = "description",
            markdown = "# markdown",
            status = "done",
            tag = listOf("user", "admin"),
            reqHeaders = listOf(MutableYapiHeader(name = "X-Auth")),
            reqQuery = listOf(MutableYapiQuery(name = "page")),
            reqParams = listOf(MutableYapiPathParam(name = "id")),
            reqBodyForm = listOf(MutableYapiFormParam(name = "file")),
            reqBodyOther = "{}",
            reqBodyType = "json",
            reqBodyIsJsonSchema = true,
            resBody = "{}",
            resBodyType = "json",
            resBodyIsJsonSchema = true,
            tags = listOf("api"),
            open = true
        )
        assertEquals("description", doc.desc)
        assertEquals("# markdown", doc.markdown)
        assertEquals("done", doc.status)
        assertEquals(listOf("user", "admin"), doc.tag)
        assertEquals(1, doc.reqHeaders?.size)
        assertEquals(1, doc.reqQuery?.size)
        assertEquals(1, doc.reqParams?.size)
        assertEquals(1, doc.reqBodyForm?.size)
        assertEquals("{}", doc.reqBodyOther)
        assertEquals("json", doc.reqBodyType)
        assertTrue(doc.reqBodyIsJsonSchema)
        assertEquals("{}", doc.resBody)
        assertEquals("json", doc.resBodyType)
        assertTrue(doc.resBodyIsJsonSchema)
        assertEquals(listOf("api"), doc.tags)
        assertEquals(true, doc.open)
    }

    @Test
    fun `create with exts populates extension map`() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            exts = mapOf("custom" to "value", "enabled" to true)
        )
        assertEquals(mapOf("custom" to "value", "enabled" to true), doc.getExts())
    }

    @Test
    fun `create with empty exts yields empty extension map`() {
        val doc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        assertTrue(doc.getExts().isEmpty())
    }

    // -------------------------------------------------------------------------
    // MutableYapiApiDoc.from
    // -------------------------------------------------------------------------

    @Test
    fun `from copies all fields from source`() {
        val original = MutableYapiApiDoc.create(
            title = "Original", path = "/orig", method = "put",
            desc = "desc", markdown = "md", status = "undone",
            open = false
        )
        val copy = MutableYapiApiDoc.from(original)
        assertEquals(original.title, copy.title)
        assertEquals(original.path, copy.path)
        assertEquals(original.method, copy.method)
        assertEquals(original.desc, copy.desc)
        assertEquals(original.markdown, copy.markdown)
        assertEquals(original.status, copy.status)
        assertEquals(original.open, copy.open)
    }

    @Test
    fun `from copies extension map from source`() {
        val original = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            exts = mapOf("k1" to "v1", "k2" to 42)
        )
        val copy = MutableYapiApiDoc.from(original)
        assertEquals(mapOf("k1" to "v1", "k2" to 42), copy.getExts())
    }

    @Test
    fun `from result is independent from source`() {
        val original = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        original.setExt("key", "original")
        val copy = MutableYapiApiDoc.from(original)

        original.setExt("key", "modified")
        assertEquals("modified", original.getExts()["key"])
        assertEquals("original", copy.getExts()["key"])
    }

    // -------------------------------------------------------------------------
    // YapiApiDoc.copy (interface-level copy)
    // -------------------------------------------------------------------------

    @Test
    fun `copy via interface creates equal instance`() {
        val doc: YapiApiDoc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            desc = "desc", reqBodyType = "json"
        )
        val copied = doc.copy()
        assertEquals(doc.title, copied.title)
        assertEquals(doc.path, copied.path)
        assertEquals(doc.method, copied.method)
        assertEquals(doc.desc, copied.desc)
        assertEquals(doc.reqBodyType, copied.reqBodyType)
    }

    @Test
    fun `copy via interface with overridden fields`() {
        val doc: YapiApiDoc = MutableYapiApiDoc.create(
            title = "Original", path = "/old", method = "get"
        )
        val copied = doc.copy(title = "Updated", path = "/new")
        assertEquals("Updated", copied.title)
        assertEquals("/new", copied.path)
        assertEquals("get", copied.method) // unchanged
    }

    @Test
    fun `copy via interface carries exts by default`() {
        val doc: YapiApiDoc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            exts = mapOf("custom" to "value")
        )
        val copied = doc.copy()
        assertEquals(mapOf("custom" to "value"), copied.getExts())
    }

    @Test
    fun `copy via interface drops exts when passed explicitly`() {
        val doc: YapiApiDoc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            exts = mapOf("custom" to "value")
        )
        val copied = doc.copy(exts = emptyMap())
        assertTrue(copied.getExts().isEmpty())
    }

    // -------------------------------------------------------------------------
    // MutableYapiApiDoc data class copy (with exts)
    // -------------------------------------------------------------------------

    @Test
    fun `data class copy preserves exts via putAllExts`() {
        val doc = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            exts = mapOf("k" to "v")
        )
        val copied = MutableYapiApiDoc.from(doc)
        assertEquals(mapOf("k" to "v"), copied.getExts())
    }

    @Test
    fun `data class component functions work`() {
        val doc = MutableYapiApiDoc.create(
            title = "Title", path = "/path", method = "post"
        )
        val (title, path, method) = doc
        assertEquals("Title", title)
        assertEquals("/path", path)
        assertEquals("post", method)
    }

    // -------------------------------------------------------------------------
    // Data class equality
    // -------------------------------------------------------------------------

    @Test
    fun `data class equality same values`() {
        val a = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        val b = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `data class equality different values`() {
        val a = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        val b = MutableYapiApiDoc.create(title = "U", path = "/p", method = "get")
        assertNotEquals(a, b)
    }

    @Test
    fun `data class equality ignores exts`() {
        val a = MutableYapiApiDoc.create(
            title = "T", path = "/p", method = "get",
            exts = mapOf("k" to "v")
        )
        val b = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        // exts are not in the primary constructor, so they don't affect equality
        assertEquals(a, b)
    }

    // -------------------------------------------------------------------------
    // Extension helpers (setExt / putAllExts)
    // -------------------------------------------------------------------------

    @Test
    fun `setExt adds and overwrites keys`() {
        val doc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        doc.setExt("key", "value")
        assertEquals("value", doc.getExts()["key"])

        doc.setExt("key", "updated")
        assertEquals("updated", doc.getExts()["key"])
    }

    @Test
    fun `putAllExts merges multiple entries`() {
        val doc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        doc.putAllExts(mapOf("a" to 1, "b" to 2))
        assertEquals(2, doc.getExts().size)
        assertEquals(1, doc.getExts()["a"])
        assertEquals(2, doc.getExts()["b"])
    }

    @Test
    fun `putAllExts overwrites existing keys`() {
        val doc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        doc.setExt("key", "old")
        doc.putAllExts(mapOf("key" to "new"))
        assertEquals("new", doc.getExts()["key"])
    }

    @Test
    fun `getExts returns immutable view`() {
        val doc = MutableYapiApiDoc.create(title = "T", path = "/p", method = "get")
        doc.setExt("k", "v")
        val view = doc.getExts()
        assertThrows(UnsupportedOperationException::class.java) {
            (view as MutableMap<String, Any?>)["k"] = "x"
        }
    }

    // -------------------------------------------------------------------------
    // Sub-model extensions
    // -------------------------------------------------------------------------

    @Test
    fun `YapiHeader extension defaults to empty`() {
        val header = MutableYapiHeader(name = "Authorization")
        assertTrue(header.getExts().isEmpty())
    }

    @Test
    fun `YapiHeader carries custom extensions`() {
        val header = MutableYapiHeader(
            name = "X-Custom"
        ).also { it.putAllExts(mapOf("custom_field" to "extra")) }
        assertEquals(mapOf("custom_field" to "extra"), header.getExts())
    }

    @Test
    fun `YapiQuery extension defaults to empty`() {
        val query = MutableYapiQuery(name = "page")
        assertTrue(query.getExts().isEmpty())
    }

    @Test
    fun `YapiQuery carries custom extensions`() {
        val query = MutableYapiQuery(
            name = "q"
        ).also { it.putAllExts(mapOf("custom" to "value")) }
        assertEquals(mapOf("custom" to "value"), query.getExts())
    }

    @Test
    fun `YapiPathParam extension defaults to empty`() {
        val param = MutableYapiPathParam(name = "id")
        assertTrue(param.getExts().isEmpty())
    }

    @Test
    fun `YapiPathParam carries custom extensions`() {
        val param = MutableYapiPathParam(
            name = "id"
        ).also { it.putAllExts(mapOf("custom" to "value")) }
        assertEquals(mapOf("custom" to "value"), param.getExts())
    }

    @Test
    fun `YapiFormParam extension defaults to empty`() {
        val form = MutableYapiFormParam(name = "file")
        assertTrue(form.getExts().isEmpty())
    }

    @Test
    fun `YapiFormParam carries custom extensions`() {
        val form = MutableYapiFormParam(
            name = "file"
        ).also { it.putAllExts(mapOf("custom" to "value")) }
        assertEquals(mapOf("custom" to "value"), form.getExts())
    }

    // -------------------------------------------------------------------------
    // Model data class equality (sub-models)
    // -------------------------------------------------------------------------

    @Test
    fun `YapiHeader data class equality`() {
        val a = MutableYapiHeader(name = "X-Auth", value = "Bearer x")
        val b = MutableYapiHeader(name = "X-Auth", value = "Bearer x")
        assertEquals(a, b)
    }

    @Test
    fun `YapiQuery data class equality`() {
        val a = MutableYapiQuery(name = "page", value = "1")
        val b = MutableYapiQuery(name = "page", value = "1")
        assertEquals(a, b)
    }

    @Test
    fun `YapiPathParam data class equality`() {
        val a = MutableYapiPathParam(name = "id", example = "42")
        val b = MutableYapiPathParam(name = "id", example = "42")
        assertEquals(a, b)
    }

    @Test
    fun `YapiFormParam data class equality`() {
        val a = MutableYapiFormParam(name = "file", required = 1)
        val b = MutableYapiFormParam(name = "file", required = 1)
        assertEquals(a, b)
    }
}
