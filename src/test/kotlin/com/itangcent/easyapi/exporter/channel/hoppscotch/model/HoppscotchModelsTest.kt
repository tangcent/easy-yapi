package com.itangcent.easyapi.exporter.channel.hoppscotch.model

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive tests for Hoppscotch model data classes and utility functions.
 */
class HoppscotchModelsTest {

    // ==================== HoppCollection tests ====================

    @Test
    fun `HoppCollection default values`() {
        val collection = HoppCollection(name = "Test")
        assertEquals(12, collection.v)
        assertEquals("Test", collection.name)
        assertTrue(collection._ref_id.startsWith("coll_"))
        assertTrue(collection.folders.isEmpty())
        assertTrue(collection.requests.isEmpty())
        assertEquals(HoppAuth(), collection.auth)
        assertTrue(collection.headers.isEmpty())
        assertTrue(collection.variables.isEmpty())
        assertNull(collection.description)
        assertEquals("", collection.preRequestScript)
        assertEquals("", collection.testScript)
    }

    @Test
    fun `HoppCollection with all fields`() {
        val collection = HoppCollection(
            name = "Full Collection",
            folders = listOf(HoppCollection(name = "Sub")),
            requests = listOf(HoppRESTRequest(name = "GET /api", method = "GET", endpoint = "/api")),
            auth = HoppAuth(authType = "bearer"),
            headers = listOf(HoppKeyValue(key = "Auth", value = "token")),
            variables = listOf(HoppCollectionVariable(key = "host", initialValue = "https://api.example.com")),
            description = "Test description",
            preRequestScript = "console.log('pre')",
            testScript = "console.log('test')"
        )
        assertEquals(1, collection.folders.size)
        assertEquals(1, collection.requests.size)
        assertEquals("bearer", collection.auth.authType)
        assertEquals("Test description", collection.description)
    }

    @Test
    fun `HoppCollection copy`() {
        val original = HoppCollection(name = "Original")
        val copy = original.copy(name = "Copied")
        assertEquals("Copied", copy.name)
        assertEquals(original.v, copy.v)
    }

    @Test
    fun `HoppCollection equals and hashCode`() {
        val c1 = HoppCollection(name = "Test", v = 12)
        val c2 = HoppCollection(name = "Test", v = 12)
        // They won't be equal because _ref_id is different
        // But if we set the same _ref_id they should be equal
        val c3 = c1.copy()
        assertEquals(c1, c3)
    }

    // ==================== HoppRESTRequest tests ====================

    @Test
    fun `HoppRESTRequest default values`() {
        val request = HoppRESTRequest(name = "GET /api", method = "GET", endpoint = "/api")
        assertEquals("17", request.v)
        assertEquals("GET /api", request.name)
        assertEquals("GET", request.method)
        assertEquals("/api", request.endpoint)
        assertTrue(request._ref_id.startsWith("req_"))
        assertTrue(request.params.isEmpty())
        assertTrue(request.headers.isEmpty())
        assertEquals(HoppAuth(), request.auth)
        assertEquals(HoppRequestBody(), request.body)
        assertEquals("", request.preRequestScript)
        assertEquals("", request.testScript)
        assertTrue(request.requestVariables.isEmpty())
        assertTrue(request.responses.isEmpty())
        assertNull(request.description)
    }

    @Test
    fun `HoppRESTRequest with all fields`() {
        val request = HoppRESTRequest(
            name = "POST /api/users",
            method = "POST",
            endpoint = "https://api.example.com/api/users",
            params = listOf(HoppKeyValue(key = "page", value = "1")),
            headers = listOf(HoppKeyValue(key = "Content-Type", value = "application/json")),
            body = HoppRequestBody(contentType = "application/json", body = "{}"),
            preRequestScript = "pm.request.headers.add('X-Custom')",
            testScript = "pm.response.to.have.status(200)",
            description = "Create a new user"
        )
        assertEquals(1, request.params.size)
        assertEquals(1, request.headers.size)
        assertEquals("application/json", request.body.contentType)
        assertEquals("Create a new user", request.description)
    }

    // ==================== HoppKeyValue tests ====================

    @Test
    fun `HoppKeyValue default values`() {
        val kv = HoppKeyValue(key = "name")
        assertEquals("name", kv.key)
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

    @Test
    fun `HoppKeyValue equals and hashCode`() {
        val kv1 = HoppKeyValue(key = "name", value = "value")
        val kv2 = HoppKeyValue(key = "name", value = "value")
        assertEquals(kv1, kv2)
        assertEquals(kv1.hashCode(), kv2.hashCode())
    }

    // ==================== HoppAuth tests ====================

    @Test
    fun `HoppAuth default values`() {
        val auth = HoppAuth()
        assertEquals("inherit", auth.authType)
        assertTrue(auth.authActive)
    }

    @Test
    fun `HoppAuth custom values`() {
        val auth = HoppAuth(authType = "bearer", authActive = false)
        assertEquals("bearer", auth.authType)
        assertFalse(auth.authActive)
    }

    @Test
    fun `HoppAuth equals and hashCode`() {
        val a1 = HoppAuth()
        val a2 = HoppAuth()
        assertEquals(a1, a2)
        assertEquals(a1.hashCode(), a2.hashCode())
    }

    // ==================== HoppRequestBody tests ====================

    @Test
    fun `HoppRequestBody default values`() {
        val body = HoppRequestBody()
        assertNull(body.contentType)
        assertNull(body.body)
    }

    @Test
    fun `HoppRequestBody with JSON body`() {
        val body = HoppRequestBody(contentType = "application/json", body = """{"name":"John"}""")
        assertEquals("application/json", body.contentType)
        assertEquals("""{"name":"John"}""", body.body)
    }

    @Test
    fun `HoppRequestBody with form data body`() {
        val formData = listOf(HoppFormDataEntry(key = "file", value = "test.txt", isFile = true))
        val body = HoppRequestBody(contentType = "multipart/form-data", body = formData)
        assertEquals("multipart/form-data", body.contentType)
        assertNotNull(body.body)
    }

    // ==================== HoppCollectionVariable tests ====================

    @Test
    fun `HoppCollectionVariable default values`() {
        val variable = HoppCollectionVariable(key = "host")
        assertEquals("host", variable.key)
        assertEquals("", variable.initialValue)
        assertEquals("", variable.currentValue)
        assertFalse(variable.secret)
    }

    @Test
    fun `HoppCollectionVariable with all fields`() {
        val variable = HoppCollectionVariable(
            key = "apiKey",
            initialValue = "default-key",
            currentValue = "actual-key",
            secret = true
        )
        assertEquals("apiKey", variable.key)
        assertEquals("default-key", variable.initialValue)
        assertEquals("actual-key", variable.currentValue)
        assertTrue(variable.secret)
    }

    // ==================== HoppRequestVariable tests ====================

    @Test
    fun `HoppRequestVariable default values`() {
        val variable = HoppRequestVariable(key = "id")
        assertEquals("id", variable.key)
        assertEquals("", variable.value)
        assertTrue(variable.active)
    }

    @Test
    fun `HoppRequestVariable with all fields`() {
        val variable = HoppRequestVariable(key = "id", value = "123", active = false)
        assertEquals("id", variable.key)
        assertEquals("123", variable.value)
        assertFalse(variable.active)
    }

    // ==================== HoppFormDataEntry tests ====================

    @Test
    fun `HoppFormDataEntry default values`() {
        val entry = HoppFormDataEntry(key = "name")
        assertEquals("name", entry.key)
        assertEquals("", entry.value)
        assertTrue(entry.active)
        assertFalse(entry.isFile)
        assertNull(entry.contentType)
    }

    @Test
    fun `HoppFormDataEntry with all fields`() {
        val entry = HoppFormDataEntry(
            key = "avatar",
            value = "photo.jpg",
            active = false,
            isFile = true,
            contentType = "image/jpeg"
        )
        assertEquals("avatar", entry.key)
        assertEquals("photo.jpg", entry.value)
        assertFalse(entry.active)
        assertTrue(entry.isFile)
        assertEquals("image/jpeg", entry.contentType)
    }

    // ==================== hoppscotchGson tests ====================

    @Test
    fun `hoppscotchGson with pretty print`() {
        val gson = hoppscotchGson(prettyPrint = true)
        val json = gson.toJson(HoppAuth(authType = "bearer"))
        // Pretty print should include newlines
        assertTrue(json.contains("\n"))
        assertTrue(json.contains("bearer"))
    }

    @Test
    fun `hoppscotchGson without pretty print`() {
        val gson = hoppscotchGson(prettyPrint = false)
        val json = gson.toJson(HoppAuth(authType = "bearer"))
        // No pretty print, should be single line
        assertFalse(json.contains("\n"))
        assertTrue(json.contains("bearer"))
    }

    @Test
    fun `hoppscotchGson serializes nulls`() {
        val gson = hoppscotchGson()
        val body = HoppRequestBody(contentType = null, body = null)
        val json = gson.toJson(body)
        // serializeNulls() should include null fields
        assertTrue(json.contains("contentType"))
        assertTrue(json.contains("body"))
    }

    @Test
    fun `hoppscotchGson serializes collection correctly`() {
        val gson = hoppscotchGson()
        val collection = HoppCollection(
            name = "Test",
            requests = listOf(
                HoppRESTRequest(name = "GET /api", method = "GET", endpoint = "/api")
            )
        )
        val json = gson.toJson(collection)
        assertTrue(json.contains("Test"))
        assertTrue(json.contains("GET /api"))
        assertTrue(json.contains("12"))  // v=12
    }

    @Test
    fun `hoppscotchGson serializes and deserializes HoppAuth`() {
        val gson = hoppscotchGson(prettyPrint = false)
        val auth = HoppAuth(authType = "basic", authActive = false)
        val json = gson.toJson(auth)
        val deserialized = gson.fromJson(json, HoppAuth::class.java)
        assertEquals(auth, deserialized)
    }

    // ==================== generateUniqueRefId tests ====================

    @Test
    fun `generateUniqueRefId with prefix`() {
        val id = generateUniqueRefId("coll")
        assertTrue(id.startsWith("coll_"))
        assertTrue(id.length > 10) // Should contain timestamp + UUID
    }

    @Test
    fun `generateUniqueRefId without prefix`() {
        val id = generateUniqueRefId("")
        assertFalse(id.startsWith("_"))
        assertTrue(id.length > 10)
    }

    @Test
    fun `generateUniqueRefId generates unique values`() {
        val id1 = generateUniqueRefId("req")
        val id2 = generateUniqueRefId("req")
        assertNotEquals(id1, id2)
    }

    @Test
    fun `generateUniqueRefId default prefix`() {
        val id = generateUniqueRefId()
        // Default prefix is empty
        assertFalse(id.startsWith("_"))
    }

    // ==================== HoppRequestBody with various body types ====================

    @Test
    fun `HoppRequestBody with urlencoded body as string`() {
        val body = HoppRequestBody(
            contentType = "application/x-www-form-urlencoded",
            body = "name=John&age=30"
        )
        assertEquals("application/x-www-form-urlencoded", body.contentType)
        assertEquals("name=John&age=30", body.body)
    }

    @Test
    fun `HoppRequestBody with form data list`() {
        val formData = listOf(
            HoppFormDataEntry(key = "name", value = "John"),
            HoppFormDataEntry(key = "file", value = "doc.pdf", isFile = true)
        )
        val body = HoppRequestBody(contentType = "multipart/form-data", body = formData)
        assertEquals("multipart/form-data", body.contentType)
        @Suppress("UNCHECKED_CAST")
        val entries = body.body as List<HoppFormDataEntry>
        assertEquals(2, entries.size)
        assertEquals("name", entries[0].key)
        assertTrue(entries[1].isFile)
    }
}
