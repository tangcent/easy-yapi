package com.itangcent.easyapi.dashboard.script

import org.junit.Assert.*
import org.junit.Test

class PmRequestTest {

    @Test
    fun testDefaultConstruction() {
        val request = PmRequest()
        assertEquals("", request.url)
        assertEquals("GET", request.method)
        assertTrue(request.headers.all().isEmpty())
        assertNull(request.body.raw)
        assertEquals("raw", request.body.mode)
    }

    @Test
    fun testCustomConstruction() {
        val request = PmRequest(url = "http://api.com", method = "POST")
        assertEquals("http://api.com", request.url)
        assertEquals("POST", request.method)
    }

    @Test
    fun testMutableFields() {
        val request = PmRequest()
        request.url = "http://updated.com"
        request.method = "PUT"
        assertEquals("http://updated.com", request.url)
        assertEquals("PUT", request.method)
    }
}

class PmHeaderListTest {

    @Test
    fun testDefaultConstruction() {
        val headers = PmHeaderList()
        assertNull(headers.get("Content-Type"))
        assertFalse(headers.has("Content-Type"))
        assertTrue(headers.all().isEmpty())
    }

    @Test
    fun testConstructionWithInitialHeaders() {
        val headers = PmHeaderList(listOf("Content-Type" to "application/json"))
        assertEquals("application/json", headers.get("Content-Type"))
    }

    @Test
    fun testAdd() {
        val headers = PmHeaderList()
        headers.add("Content-Type", "application/json")
        assertEquals("application/json", headers.get("Content-Type"))
    }

    @Test
    fun testAddMultipleSameName() {
        val headers = PmHeaderList()
        headers.add("X-Custom", "value1")
        headers.add("X-Custom", "value2")
        assertEquals("value1", headers.get("X-Custom"))
        assertEquals(2, headers.all().size)
    }

    @Test
    fun testAddFromMap() {
        val headers = PmHeaderList()
        headers.add(mapOf("key" to "Authorization", "value" to "Bearer token"))
        assertEquals("Bearer token", headers.get("Authorization"))
    }

    @Test
    fun testAddFromMapMissingKey() {
        val headers = PmHeaderList()
        headers.add(mapOf("value" to "some-value"))
        assertTrue(headers.all().isEmpty())
    }

    @Test
    fun testUpsertInserts() {
        val headers = PmHeaderList()
        headers.upsert("Content-Type", "application/json")
        assertEquals("application/json", headers.get("Content-Type"))
    }

    @Test
    fun testUpsertReplaces() {
        val headers = PmHeaderList()
        headers.upsert("Content-Type", "text/plain")
        headers.upsert("Content-Type", "application/json")
        assertEquals("application/json", headers.get("Content-Type"))
        assertEquals(1, headers.all().size)
    }

    @Test
    fun testCaseInsensitiveLookup() {
        val headers = PmHeaderList()
        headers.add("Content-Type", "application/json")
        assertEquals("application/json", headers.get("content-type"))
        assertEquals("application/json", headers.get("CONTENT-TYPE"))
        assertTrue(headers.has("content-type"))
    }

    @Test
    fun testRemove() {
        val headers = PmHeaderList()
        headers.add("Content-Type", "application/json")
        headers.remove("content-type")
        assertFalse(headers.has("Content-Type"))
    }

    @Test
    fun testAll() {
        val headers = PmHeaderList()
        headers.add("Content-Type", "application/json")
        headers.add("Authorization", "Bearer token")
        val all = headers.all()
        assertEquals(2, all.size)
        assertEquals("Content-Type", all[0]["key"])
        assertEquals("application/json", all[0]["value"])
    }

    @Test
    fun testToPairs() {
        val headers = PmHeaderList()
        headers.add("A", "1")
        val pairs = headers.toPairs()
        assertEquals(1, pairs.size)
        assertEquals("A" to "1", pairs[0])
    }

    @Test
    fun testFromPairs() {
        val headers = PmHeaderList()
        headers.add("Old", "value")
        headers.fromPairs(listOf("New" to "value"))
        assertEquals(1, headers.all().size)
        assertEquals("value", headers.get("New"))
        assertNull(headers.get("Old"))
    }
}

class PmRequestBodyTest {

    @Test
    fun testDefaultConstruction() {
        val body = PmRequestBody()
        assertNull(body.raw)
        assertEquals("raw", body.mode)
        assertTrue(body.urlencoded.all().isEmpty())
        assertTrue(body.formdata.all().isEmpty())
    }

    @Test
    fun testRawBody() {
        val body = PmRequestBody(raw = "{\"name\":\"Alice\"}", mode = "raw")
        assertEquals("{\"name\":\"Alice\"}", body.raw)
    }

    @Test
    fun testUrlencodedBody() {
        val body = PmRequestBody(mode = "urlencoded")
        body.urlencoded.add("grant_type", "password")
        body.urlencoded.add("username", "alice")
        assertEquals("password", body.urlencoded.get("grant_type"))
        assertEquals("alice", body.urlencoded.get("username"))
    }

    @Test
    fun testFormdataBody() {
        val body = PmRequestBody(mode = "formdata")
        body.formdata.add("file", "/path/to/file", "file")
        assertEquals("/path/to/file", body.formdata.get("file"))
    }
}

class PmPropertyListTest {

    @Test
    fun testDefaultConstruction() {
        val list = PmPropertyList()
        assertTrue(list.all().isEmpty())
        assertNull(list.get("key"))
        assertFalse(list.has("key"))
    }

    @Test
    fun testAddKeyValue() {
        val list = PmPropertyList()
        list.add("username", "alice")
        assertEquals("alice", list.get("username"))
        assertTrue(list.has("username"))
    }

    @Test
    fun testAddKeyValueWithType() {
        val list = PmPropertyList()
        list.add("avatar", "/photo.jpg", "file")
        assertEquals("/photo.jpg", list.get("avatar"))
        val entry = list.all()[0]
        assertEquals("avatar", entry["key"])
        assertEquals("/photo.jpg", entry["value"])
        assertEquals("file", entry["type"])
    }

    @Test
    fun testAddFromMap() {
        val list = PmPropertyList()
        list.add(mapOf("key" to "token", "value" to "abc123", "type" to "text"))
        assertEquals("abc123", list.get("token"))
    }

    @Test
    fun testRemove() {
        val list = PmPropertyList()
        list.add("key1", "value1")
        list.add("key2", "value2")
        list.remove("key1")
        assertNull(list.get("key1"))
        assertEquals("value2", list.get("key2"))
    }

    @Test
    fun testAll() {
        val list = PmPropertyList()
        list.add("a", "1")
        list.add("b", "2")
        val all = list.all()
        assertEquals(2, all.size)
    }

    @Test
    fun testDuplicateKeys() {
        val list = PmPropertyList()
        list.add("key", "first")
        list.add("key", "second")
        assertEquals("first", list.get("key"))
        assertEquals(2, list.all().size)
    }
}
