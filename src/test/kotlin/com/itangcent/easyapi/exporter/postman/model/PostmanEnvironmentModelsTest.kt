package com.itangcent.easyapi.exporter.postman.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for Postman environment data classes: serialization, deserialization,
 * defaults, and equality.
 *
 * Spec: requirements/REQ-5 (Data Models)
 */
class PostmanEnvironmentModelsTest {

    // ── PostmanEnvironmentInfo ───────────────────────────────────────────────

    @Test
    fun `PostmanEnvironmentInfo defaults uid to null`() {
        val info = PostmanEnvironmentInfo(id = "env-1", name = "dev")
        assertEquals("env-1", info.id)
        assertEquals("dev", info.name)
        assertNull(info.uid)
    }

    @Test
    fun `PostmanEnvironmentInfo equality and copy`() {
        val a = PostmanEnvironmentInfo("1", "dev", "uid-1")
        val b = a.copy()
        assertEquals(a, b)
        val c = a.copy(name = "prod")
        assertNotEquals(a, c)
        assertEquals("prod", c.name)
    }

    @Test
    fun `PostmanEnvironmentInfo serializes to JSON with uid`() {
        val info = PostmanEnvironmentInfo("env-1", "dev", "uid-1")
        val json = PostmanGson.compact.toJson(info)
        assertTrue(json.contains("\"id\":\"env-1\""))
        assertTrue(json.contains("\"name\":\"dev\""))
        assertTrue(json.contains("\"uid\":\"uid-1\""))
    }

    @Test
    fun `PostmanEnvironmentInfo deserializes from JSON`() {
        val json = """{"id":"env-1","name":"dev","uid":"uid-1"}"""
        val info = PostmanGson.pretty.fromJson(json, PostmanEnvironmentInfo::class.java)
        assertEquals("env-1", info.id)
        assertEquals("dev", info.name)
        assertEquals("uid-1", info.uid)
    }

    // ── PostmanEnvironmentValue ──────────────────────────────────────────────

    @Test
    fun `PostmanEnvironmentValue defaults enabled=true and type=text`() {
        val v = PostmanEnvironmentValue(key = "URL", value = "http://localhost")
        assertEquals("URL", v.key)
        assertEquals("http://localhost", v.value)
        assertTrue(v.enabled)
        assertEquals("text", v.type)
    }

    @Test
    fun `PostmanEnvironmentValue can be disabled`() {
        val v = PostmanEnvironmentValue(key = "X", value = "y", enabled = false)
        assertFalse(v.enabled)
    }

    @Test
    fun `PostmanEnvironmentValue can have secret type`() {
        val v = PostmanEnvironmentValue(key = "TOKEN", value = "secret", type = "secret")
        assertEquals("secret", v.type)
    }

    @Test
    fun `PostmanEnvironmentValue serializes all fields`() {
        val v = PostmanEnvironmentValue("URL", "http://x", enabled = true, type = "text")
        val json = PostmanGson.compact.toJson(v)
        assertTrue(json.contains("\"key\":\"URL\""))
        assertTrue(json.contains("\"value\":\"http://x\""))
        assertTrue(json.contains("\"enabled\":true"))
        assertTrue(json.contains("\"type\":\"text\""))
    }

    @Test
    fun `PostmanEnvironmentValue deserializes from JSON`() {
        val json = """{"key":"URL","value":"http://x","enabled":false,"type":"secret"}"""
        val v = PostmanGson.pretty.fromJson(json, PostmanEnvironmentValue::class.java)
        assertEquals("URL", v.key)
        assertEquals("http://x", v.value)
        assertFalse(v.enabled)
        assertEquals("secret", v.type)
    }

    // ── PostmanEnvironmentDetail ─────────────────────────────────────────────

    @Test
    fun `PostmanEnvironmentDetail defaults id empty and values empty`() {
        val detail = PostmanEnvironmentDetail(name = "dev")
        assertEquals("", detail.id)
        assertEquals("dev", detail.name)
        assertNull(detail.uid)
        assertTrue(detail.values.isEmpty())
    }

    @Test
    fun `PostmanEnvironmentDetail holds values list`() {
        val detail = PostmanEnvironmentDetail(
            id = "env-1",
            name = "dev",
            uid = "uid-1",
            values = listOf(
                PostmanEnvironmentValue("A", "1"),
                PostmanEnvironmentValue("B", "2")
            )
        )
        assertEquals(2, detail.values.size)
        assertEquals("A", detail.values[0].key)
    }

    @Test
    fun `PostmanEnvironmentDetail serializes nested values`() {
        val detail = PostmanEnvironmentDetail(
            name = "dev",
            values = listOf(PostmanEnvironmentValue("URL", "http://x"))
        )
        val json = PostmanGson.compact.toJson(detail)
        assertTrue(json.contains("\"name\":\"dev\""))
        assertTrue(json.contains("\"values\""))
        assertTrue(json.contains("\"key\":\"URL\""))
    }

    @Test
    fun `PostmanEnvironmentDetail deserializes from JSON with values`() {
        val json = """
            {"id":"env-1","name":"dev","uid":"u1","values":[
                {"key":"A","value":"1","enabled":true,"type":"text"}
            ]}
        """.trimIndent()
        val detail = PostmanGson.pretty.fromJson(json, PostmanEnvironmentDetail::class.java)
        assertEquals("env-1", detail.id)
        assertEquals("dev", detail.name)
        assertEquals("u1", detail.uid)
        assertEquals(1, detail.values.size)
        assertEquals("A", detail.values[0].key)
        assertEquals("1", detail.values[0].value)
    }

    // ── PostmanEnvironmentCreateRequest ──────────────────────────────────────

    @Test
    fun `PostmanEnvironmentCreateRequest wraps environment under environment key`() {
        val env = PostmanEnvironmentDetail(name = "dev", values = listOf(PostmanEnvironmentValue("A", "1")))
        val req = PostmanEnvironmentCreateRequest(env)
        val json = PostmanGson.compact.toJson(req)
        // Top-level must be "environment"
        assertTrue(json.startsWith("{\"environment\":"))
        assertTrue(json.contains("\"name\":\"dev\""))
    }

    @Test
    fun `PostmanEnvironmentCreateRequest deserializes`() {
        val json = """{"environment":{"name":"dev","values":[]}}"""
        val req = PostmanGson.pretty.fromJson(json, PostmanEnvironmentCreateRequest::class.java)
        assertEquals("dev", req.environment.name)
        assertTrue(req.environment.values.isEmpty())
    }

    // ── PostmanEnvironmentUpdateRequest ──────────────────────────────────────

    @Test
    fun `PostmanEnvironmentUpdateRequest wraps environment under environment key`() {
        val env = PostmanEnvironmentDetail(id = "env-1", name = "dev")
        val req = PostmanEnvironmentUpdateRequest(env)
        val json = PostmanGson.compact.toJson(req)
        assertTrue(json.startsWith("{\"environment\":"))
        assertTrue(json.contains("\"id\":\"env-1\""))
    }

    @Test
    fun `PostmanEnvironmentUpdateRequest deserializes`() {
        val json = """{"environment":{"id":"env-1","name":"dev","values":[]}}"""
        val req = PostmanGson.pretty.fromJson(json, PostmanEnvironmentUpdateRequest::class.java)
        assertEquals("env-1", req.environment.id)
        assertEquals("dev", req.environment.name)
    }
}
