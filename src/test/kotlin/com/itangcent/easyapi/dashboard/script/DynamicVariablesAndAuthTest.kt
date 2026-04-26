package com.itangcent.easyapi.dashboard.script

import org.junit.Assert.*
import org.junit.Test

class DynamicVariablesTest {

    private val DYNAMIC_VARIABLE_PATTERN = Regex("\\{\\{\\$(\\w+)}}")

    @Test
    fun testResolveTimestamp() {
        val result = DynamicVariables.resolve("timestamp")
        assertNotNull(result)
        assertTrue(result!!.toLong() > 0)
    }

    @Test
    fun testResolveRandomInt() {
        val result = DynamicVariables.resolve("randomInt")
        assertNotNull(result)
        val value = result!!.toInt()
        assertTrue(value in 0..999)
    }

    @Test
    fun testResolveGuid() {
        val result = DynamicVariables.resolve("guid")
        assertNotNull(result)
        assertTrue(result!!.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun testResolveRandomUuid() {
        val result = DynamicVariables.resolve("randomUuid")
        assertNotNull(result)
        assertTrue(result!!.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    @Test
    fun testResolveRandomAlphaNumeric() {
        val result = DynamicVariables.resolve("randomAlphaNumeric")
        assertNotNull(result)
        assertEquals(8, result!!.length)
        assertTrue(result.all { it.isLetterOrDigit() })
    }

    @Test
    fun testResolveRandomFirstName() {
        val result = DynamicVariables.resolve("randomFirstName")
        assertNotNull(result)
        assertTrue(result!! in listOf("Alice", "Bob", "Charlie", "Diana", "Eve", "Frank", "Grace", "Henry"))
    }

    @Test
    fun testResolveRandomLastName() {
        val result = DynamicVariables.resolve("randomLastName")
        assertNotNull(result)
        assertTrue(result!! in listOf("Smith", "Johnson", "Williams", "Brown", "Jones", "Davis", "Miller", "Wilson"))
    }

    @Test
    fun testResolveRandomEmail() {
        val result = DynamicVariables.resolve("randomEmail")
        assertNotNull(result)
        assertTrue(result!!.contains("@"))
        assertTrue(result.contains("."))
    }

    @Test
    fun testResolveRandomUrl() {
        val result = DynamicVariables.resolve("randomUrl")
        assertNotNull(result)
        assertTrue(result!!.startsWith("https://example.com/"))
    }

    @Test
    fun testResolveRandomIP() {
        val result = DynamicVariables.resolve("randomIP")
        assertNotNull(result)
        val parts = result!!.split(".")
        assertEquals(4, parts.size)
        parts.forEach { part ->
            val value = part.toInt()
            assertTrue(value in 0..255)
        }
    }

    @Test
    fun testResolveUnknownVariable() {
        assertNull(DynamicVariables.resolve("nonexistent"))
    }

    @Test
    fun testRegisterCustomResolver() {
        DynamicVariables.register("myCustomVar") { "custom-value" }
        assertEquals("custom-value", DynamicVariables.resolve("myCustomVar"))
    }

    @Test
    fun testRegisterOverridesExisting() {
        DynamicVariables.register("timestamp") { "fixed-timestamp" }
        assertEquals("fixed-timestamp", DynamicVariables.resolve("timestamp"))
        DynamicVariables.register("timestamp") { (System.currentTimeMillis() / 1000).toString() }
    }

    @Test
    fun testGuidAndRandomUuidAreDifferent() {
        val guid = DynamicVariables.resolve("guid")
        val uuid = DynamicVariables.resolve("randomUuid")
        assertNotNull(guid)
        assertNotNull(uuid)
    }

    @Test
    fun testAutoResolveTimestampInUrl() {
        val input = "https://api.example.com?ts={{\$timestamp}}"
        val result = DYNAMIC_VARIABLE_PATTERN.replace(input) { match ->
            val varName = match.groupValues[1]
            DynamicVariables.resolve(varName) ?: match.value
        }
        assertFalse("Should not contain unresolved placeholder", result.contains("{{\$timestamp}}"))
        assertTrue("Should start with original URL prefix", result.startsWith("https://api.example.com?ts="))
        assertTrue("Timestamp should be numeric", result.substringAfter("ts=").toLong() > 0)
    }

    @Test
    fun testAutoResolveMultipleDynamicVariables() {
        val input = "id={{\$guid}}&ts={{\$timestamp}}&rnd={{\$randomInt}}"
        val result = DYNAMIC_VARIABLE_PATTERN.replace(input) { match ->
            val varName = match.groupValues[1]
            DynamicVariables.resolve(varName) ?: match.value
        }
        assertFalse("Should not contain any unresolved placeholders", result.contains("{{\$"))
        assertTrue("Should contain id=", result.startsWith("id="))
        assertTrue("Should contain &ts=", result.contains("&ts="))
        assertTrue("Should contain &rnd=", result.contains("&rnd="))
    }

    @Test
    fun testAutoResolveLeavesUnknownPlaceholders() {
        val input = "val={{\$unknownVar}}"
        val result = DYNAMIC_VARIABLE_PATTERN.replace(input) { match ->
            val varName = match.groupValues[1]
            DynamicVariables.resolve(varName) ?: match.value
        }
        assertEquals("Unknown dynamic variables should remain unchanged", input, result)
    }

    @Test
    fun testAutoResolveMixedEnvAndDynamicVariables() {
        val envMap = mapOf("base_url" to "https://api.example.com")
        val input = "{{base_url}}/users?ts={{\$timestamp}}"
        var result = Regex("\\{\\{([^}]+)}}").replace(input) { match ->
            val key = match.groupValues[1].trim()
            envMap[key] ?: match.value
        }
        result = DYNAMIC_VARIABLE_PATTERN.replace(result) { match ->
            val varName = match.groupValues[1]
            DynamicVariables.resolve(varName) ?: match.value
        }
        assertTrue("Should resolve env var", result.startsWith("https://api.example.com/users?ts="))
        assertFalse("Should resolve dynamic var", result.contains("{{\$timestamp}}"))
    }

    @Test
    fun testAutoResolveInHeader() {
        val input = "Bearer-{{\$randomAlphaNumeric}}"
        val result = DYNAMIC_VARIABLE_PATTERN.replace(input) { match ->
            val varName = match.groupValues[1]
            DynamicVariables.resolve(varName) ?: match.value
        }
        assertTrue("Should start with Bearer-", result.startsWith("Bearer-"))
        assertEquals("Alpha-numeric suffix should be 8 chars", 8, result.substringAfter("Bearer-").length)
    }

    @Test
    fun testAutoResolveNoPlaceholders() {
        val input = "plain text with no variables"
        val result = DYNAMIC_VARIABLE_PATTERN.replace(input) { match ->
            val varName = match.groupValues[1]
            DynamicVariables.resolve(varName) ?: match.value
        }
        assertEquals("Plain text should remain unchanged", input, result)
    }

    @Test
    fun testAutoResolveRandomIntRange() {
        for (i in 1..100) {
            val input = "{{\$randomInt}}"
            val result = DYNAMIC_VARIABLE_PATTERN.replace(input) { match ->
                val varName = match.groupValues[1]
                DynamicVariables.resolve(varName) ?: match.value
            }
            val value = result.toInt()
            assertTrue("randomInt should be 0-999, got $value", value in 0..999)
        }
    }
}

class PmAuthConfigTest {

    @Test
    fun testDefaultType() {
        val auth = PmAuthConfig()
        assertEquals("noauth", auth.type())
        assertTrue(auth.data().isEmpty())
    }

    @Test
    fun testBearerAuth() {
        val auth = PmAuthConfig()
        auth.bearer("my-jwt-token")
        assertEquals("bearer", auth.type())
        assertEquals("my-jwt-token", auth.data()["token"])
    }

    @Test
    fun testBasicAuth() {
        val auth = PmAuthConfig()
        auth.basic("admin", "password123")
        assertEquals("basic", auth.type())
        assertEquals("admin", auth.data()["username"])
        assertEquals("password123", auth.data()["password"])
    }

    @Test
    fun testApiKeyAuth() {
        val auth = PmAuthConfig()
        auth.apiKey("X-API-Key", "abc123", "header")
        assertEquals("apikey", auth.type())
        assertEquals("X-API-Key", auth.data()["key"])
        assertEquals("abc123", auth.data()["value"])
        assertEquals("header", auth.data()["in"])
    }

    @Test
    fun testApiKeyAuthDefaultLocation() {
        val auth = PmAuthConfig()
        auth.apiKey("X-API-Key", "abc123")
        assertEquals("header", auth.data()["in"])
    }

    @Test
    fun testApplyBearerToHeaders() {
        val auth = PmAuthConfig()
        auth.bearer("my-token")
        val headers = PmHeaderList()
        auth.applyToHeaders(headers)
        assertEquals("Bearer my-token", headers.get("Authorization"))
    }

    @Test
    fun testApplyBasicToHeaders() {
        val auth = PmAuthConfig()
        auth.basic("admin", "secret")
        val headers = PmHeaderList()
        auth.applyToHeaders(headers)
        val expected = java.util.Base64.getEncoder()
            .encodeToString("admin:secret".toByteArray())
        assertEquals("Basic $expected", headers.get("Authorization"))
    }

    @Test
    fun testApplyApiKeyToHeaders() {
        val auth = PmAuthConfig()
        auth.apiKey("X-API-Key", "abc123", "header")
        val headers = PmHeaderList()
        auth.applyToHeaders(headers)
        assertEquals("abc123", headers.get("X-API-Key"))
    }

    @Test
    fun testApplyApiKeyQueryLocationNoHeaderAdded() {
        val auth = PmAuthConfig()
        auth.apiKey("api_key", "abc123", "query")
        val headers = PmHeaderList()
        auth.applyToHeaders(headers)
        assertFalse(headers.has("api_key"))
    }

    @Test
    fun testApplyNoAuthDoesNothing() {
        val auth = PmAuthConfig()
        val headers = PmHeaderList()
        auth.applyToHeaders(headers)
        assertTrue(headers.all().isEmpty())
    }

    @Test
    fun testDataIsImmutableSnapshot() {
        val auth = PmAuthConfig()
        auth.bearer("token")
        val data = auth.data()
        data.toMutableMap()["extra"] = "value"
        assertFalse(auth.data().containsKey("extra"))
    }
}

class PmCookiesTest {

    @Test
    fun testDefaultConstruction() {
        val cookies = PmCookies()
        assertFalse(cookies.has("session"))
        assertNull(cookies.get("session"))
        assertTrue(cookies.toObject().isEmpty())
    }

    @Test
    fun testConstructionWithCookies() {
        val cookies = PmCookies(mapOf("session_id" to "abc123", "user" to "alice"))
        assertTrue(cookies.has("session_id"))
        assertEquals("abc123", cookies.get("session_id"))
        assertEquals("alice", cookies.get("user"))
    }

    @Test
    fun testHasNonexistentCookie() {
        val cookies = PmCookies(mapOf("session" to "abc"))
        assertFalse(cookies.has("missing"))
    }

    @Test
    fun testToObject() {
        val cookies = PmCookies(mapOf("a" to "1", "b" to "2"))
        val obj = cookies.toObject()
        assertEquals(2, obj.size)
        assertEquals("1", obj["a"])
        assertEquals("2", obj["b"])
    }
}

class PmInfoTest {

    @Test
    fun testConstruction() {
        val info = PmInfo("prerequest", "Get Users", "req-001")
        assertEquals("prerequest", info.eventName)
        assertEquals("Get Users", info.requestName)
        assertEquals("req-001", info.requestId)
    }

    @Test
    fun testPostResponseInfo() {
        val info = PmInfo("test", "Create User", "req-002")
        assertEquals("test", info.eventName)
    }
}

class PmSendRequestTest {

    @Test
    fun testNullHttpClientIsNoOp() {
        val sendRequest = PmSendRequest(null)
        val callback = PmSendRequestCallback()
        var called = false
        callback.setHandler { called = true }
        sendRequest("https://api.example.com", callback)
        assertFalse(called)
    }

    @Test
    fun testNullHttpClientWithOptionsIsNoOp() {
        val sendRequest = PmSendRequest(null)
        val callback = PmSendRequestCallback()
        var called = false
        callback.setHandler { called = true }
        sendRequest(mapOf("url" to "https://api.example.com", "method" to "GET"), callback)
        assertFalse(called)
    }

    @Test
    fun testCallbackHandlerIsInvoked() {
        val callback = PmSendRequestCallback()
        var receivedResponse: PmResponse? = null
        callback.setHandler { response -> receivedResponse = response }
        val testResponse = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = """{"ok":true}"""
        )
        callback.call(testResponse)
        assertNotNull(receivedResponse)
        assertEquals(200, receivedResponse!!.code)
        assertEquals("""{"ok":true}""", receivedResponse!!.text())
    }

    @Test
    fun testCallbackHandlerNotSetDoesNotThrow() {
        val callback = PmSendRequestCallback()
        val testResponse = PmResponse(
            code = 200, status = "OK", headers = PmHeaderList(),
            responseTime = 0, responseSize = 0, rawBody = ""
        )
        callback.call(testResponse)
    }
}

class PmResponseXmlTest {

    @Test
    fun testXmlParsing() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/xml")),
            responseTime = 50,
            responseSize = 100,
            rawBody = "<root><name>Alice</name></root>"
        )
        val xml = response.xml()
        assertNotNull(xml)
    }

    @Test
    fun testXmlParsingInvalidBody() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = "not xml"
        )
        assertNull(response.xml())
    }

    @Test
    fun testXmlParsingEmptyBody() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = ""
        )
        assertNull(response.xml())
    }
}

class PmResponseBddAdditionalTest {

    @Test
    fun testBeXml() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/xml")),
            responseTime = 0,
            responseSize = 0,
            rawBody = "<root/>"
        )
        response.to.be.xml
    }

    @Test(expected = IllegalStateException::class)
    fun testBeXmlFailsForJson() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/json")),
            responseTime = 0,
            responseSize = 0,
            rawBody = "{}"
        )
        response.to.be.xml
    }

    @Test
    fun testNotBeXml() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/json")),
            responseTime = 0,
            responseSize = 0,
            rawBody = "{}"
        )
        response.to.not().be.xml
    }

    @Test
    fun testNotBeHtml() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/json")),
            responseTime = 0,
            responseSize = 0,
            rawBody = "{}"
        )
        response.to.not().be.html
    }

    @Test
    fun testNotHaveBody() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = "actual body"
        )
        response.to.not().have.body("wrong body")
    }

    @Test(expected = IllegalStateException::class)
    fun testNotHaveBodyFails() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = "actual body"
        )
        response.to.not().have.body("actual body")
    }

    @Test
    fun testNotHaveJsonBody() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = """{"name":"Alice"}"""
        )
        response.to.not().have.jsonBody("nonexistent")
    }

    @Test(expected = IllegalStateException::class)
    fun testNotHaveJsonBodyFails() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = """{"name":"Alice"}"""
        )
        response.to.not().have.jsonBody("name")
    }

    @Test
    fun testNotHaveStatus() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = ""
        )
        response.to.not().have.status(404)
    }

    @Test(expected = IllegalStateException::class)
    fun testNotHaveStatusFails() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = ""
        )
        response.to.not().have.status(200)
    }

    @Test
    fun testNotHaveHeader() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = ""
        )
        response.to.not().have.header("X-Missing")
    }

    @Test(expected = IllegalStateException::class)
    fun testNotHaveHeaderFails() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/json")),
            responseTime = 0,
            responseSize = 0,
            rawBody = ""
        )
        response.to.not().have.header("Content-Type")
    }
}
