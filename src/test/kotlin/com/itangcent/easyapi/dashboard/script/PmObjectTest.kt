package com.itangcent.easyapi.dashboard.script

import org.junit.Assert.*
import org.junit.Test

class PmObjectTest {

    private fun createPreRequestPm(): PmObject {
        val envVars = PmVariableScope().apply { set("host", "https://api.example.com") }
        val globalVars = PmVariableScope().apply { set("apiKey", "global-key") }
        val collectionVars = PmVariableScope().apply { set("projectId", "proj-1") }
        val request = PmRequest(url = "https://api.example.com/users", method = "GET")
        val testCollector = PmTestCollector()
        val info = PmInfo("prerequest", "Get Users", "req-001")

        return PmObject.forPreRequest(
            request = request,
            environment = envVars,
            globals = globalVars,
            collectionVariables = collectionVars,
            testCollector = testCollector,
            info = info
        )
    }

    private fun createPostResponsePm(): PmObject {
        val envVars = PmVariableScope().apply { set("host", "https://api.example.com") }
        val globalVars = PmVariableScope().apply { set("apiKey", "global-key") }
        val collectionVars = PmVariableScope().apply { set("projectId", "proj-1") }
        val request = PmRequest(url = "https://api.example.com/users", method = "GET")
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/json")),
            responseTime = 100,
            responseSize = 42,
            rawBody = """{"name":"Alice"}"""
        )
        val testCollector = PmTestCollector()
        val cookies = PmCookies(mapOf("sessionId" to "abc123"))
        val info = PmInfo("test", "Get Users", "req-001")

        return PmObject.forPostResponse(
            request = request,
            response = response,
            environment = envVars,
            globals = globalVars,
            collectionVariables = collectionVars,
            testCollector = testCollector,
            cookies = cookies,
            info = info
        )
    }

    @Test
    fun testForPreRequestHasNullResponse() {
        val pm = createPreRequestPm()
        assertNull(pm.response)
    }

    @Test
    fun testForPreRequestHasRequest() {
        val pm = createPreRequestPm()
        assertNotNull(pm.request)
        assertEquals("https://api.example.com/users", pm.request.url)
        assertEquals("GET", pm.request.method)
    }

    @Test
    fun testForPostResponseHasResponse() {
        val pm = createPostResponsePm()
        assertNotNull(pm.response)
        assertEquals(200, pm.response!!.code)
    }

    @Test
    fun testForPreRequestInfo() {
        val pm = createPreRequestPm()
        assertEquals("prerequest", pm.info.eventName)
        assertEquals("Get Users", pm.info.requestName)
        assertEquals("req-001", pm.info.requestId)
    }

    @Test
    fun testForPostResponseInfo() {
        val pm = createPostResponsePm()
        assertEquals("test", pm.info.eventName)
    }

    @Test
    fun testEnvironmentScope() {
        val pm = createPreRequestPm()
        assertTrue(pm.environment.has("host"))
        assertEquals("https://api.example.com", pm.environment.get("host"))
    }

    @Test
    fun testGlobalsScope() {
        val pm = createPreRequestPm()
        assertTrue(pm.globals.has("apiKey"))
        assertEquals("global-key", pm.globals.get("apiKey"))
    }

    @Test
    fun testCollectionVariablesScope() {
        val pm = createPreRequestPm()
        assertTrue(pm.collectionVariables.has("projectId"))
        assertEquals("proj-1", pm.collectionVariables.get("projectId"))
    }

    @Test
    fun testVariablesIsCompositeScope() {
        val pm = createPreRequestPm()
        assertTrue(pm.variables is CompositeVariableScope)
    }

    @Test
    fun testVariablesResolvesFromEnvironment() {
        val pm = createPreRequestPm()
        assertEquals("https://api.example.com", pm.variables.get("host"))
    }

    @Test
    fun testVariablesResolvesFromGlobals() {
        val pm = createPreRequestPm()
        assertEquals("global-key", pm.variables.get("apiKey"))
    }

    @Test
    fun testVariablesResolvesFromCollection() {
        val pm = createPreRequestPm()
        assertEquals("proj-1", pm.variables.get("projectId"))
    }

    @Test
    fun testVariablesSetWritesToLocal() {
        val pm = createPreRequestPm()
        pm.variables.set("temp", "local-value")
        assertEquals("local-value", pm.variables.get("temp"))
        assertFalse(pm.environment.has("temp"))
        assertFalse(pm.globals.has("temp"))
        assertFalse(pm.collectionVariables.has("temp"))
    }

    @Test
    fun testVariablesLocalOverridesEnvironment() {
        val pm = createPreRequestPm()
        pm.variables.set("host", "https://override.example.com")
        assertEquals("https://override.example.com", pm.variables.get("host"))
        assertEquals("https://api.example.com", pm.environment.get("host"))
    }

    @Test
    fun testExpectReturnsPmExpectation() {
        val pm = createPreRequestPm()
        val expectation = pm.expect(42)
        assertNotNull(expectation)
        expectation.to.equal(42)
    }

    @Test
    fun testExpectNull() {
        val pm = createPreRequestPm()
        pm.expect(null).to.be.isNull()
    }

    @Test
    fun testTestCollector() {
        val pm = createPreRequestPm()
        pm.test("should pass") {}
        assertEquals(1, pm.testCollector.results.size)
        assertTrue(pm.testCollector.results[0].passed)
    }

    @Test
    fun testTestCollectorFailingTest() {
        val pm = createPreRequestPm()
        pm.test("should fail") { throw AssertionError("fail") }
        assertEquals(1, pm.testCollector.results.size)
        assertFalse(pm.testCollector.results[0].passed)
    }

    @Test
    fun testCookiesInPostResponse() {
        val pm = createPostResponsePm()
        assertTrue(pm.cookies.has("sessionId"))
        assertEquals("abc123", pm.cookies.get("sessionId"))
    }

    @Test
    fun testCookiesInPreRequest() {
        val pm = createPreRequestPm()
        assertFalse(pm.cookies.has("sessionId"))
    }

    @Test
    fun testSendRequestWithNullHttpClient() {
        val pm = createPreRequestPm()
        assertNotNull(pm.sendRequest)
    }

    @Test
    fun testRequestMutationInPreRequest() {
        val pm = createPreRequestPm()
        pm.request.url = "https://modified.example.com"
        pm.request.method = "POST"
        pm.request.headers.add("X-Custom", "value")
        pm.request.body.raw = """{"name":"Bob"}"""

        assertEquals("https://modified.example.com", pm.request.url)
        assertEquals("POST", pm.request.method)
        assertEquals("value", pm.request.headers.get("X-Custom"))
        assertEquals("""{"name":"Bob"}""", pm.request.body.raw)
    }

    @Test
    fun testAuthConfiguration() {
        val pm = createPreRequestPm()
        pm.request.auth.bearer("my-token")
        assertEquals("bearer", pm.request.auth.type())
        assertEquals("my-token", pm.request.auth.data()["token"])
    }

    @Test
    fun testEnvironmentSetFromScript() {
        val pm = createPreRequestPm()
        pm.environment.set("token", "new-token")
        assertEquals("new-token", pm.environment.get("token"))
    }

    @Test
    fun testEnvironmentUnsetFromScript() {
        val pm = createPreRequestPm()
        pm.environment.unset("host")
        assertFalse(pm.environment.has("host"))
    }

    @Test
    fun testEnvironmentClearFromScript() {
        val pm = createPreRequestPm()
        pm.environment.clear()
        assertFalse(pm.environment.has("host"))
    }

    @Test
    fun testEnvironmentReplaceIn() {
        val pm = createPreRequestPm()
        val input = "User-{{\$randomInt}}"
        val result = pm.environment.replaceIn(input)
        assertNotEquals(input, result)
        assertFalse(result.contains("{{\$randomInt}}"))
    }

    @Test
    fun testEnvironmentToObject() {
        val pm = createPreRequestPm()
        val obj = pm.environment.toObject()
        assertEquals("https://api.example.com", obj["host"])
    }

    @Test
    fun testGlobalsToObject() {
        val pm = createPreRequestPm()
        val obj = pm.globals.toObject()
        assertEquals("global-key", obj["apiKey"])
    }

    @Test
    fun testResponseJsonInPostResponse() {
        val pm = createPostResponsePm()
        val json = pm.response!!.json() as? Map<*, *>
        assertNotNull(json)
        assertEquals("Alice", json!!["name"])
    }

    @Test
    fun testResponseTextInPostResponse() {
        val pm = createPostResponsePm()
        assertEquals("""{"name":"Alice"}""", pm.response!!.text())
    }

    @Test
    fun testResponseBddAssertions() {
        val pm = createPostResponsePm()
        pm.response!!.to.have.status(200)
        pm.response!!.to.have.header("Content-Type")
        pm.response!!.to.be.ok
        pm.response!!.to.be.json
    }

    @Test
    fun testResponseHeadersInPostResponse() {
        val pm = createPostResponsePm()
        assertEquals("application/json", pm.response!!.headers.get("Content-Type"))
    }

    @Test
    fun testExpectChainingInScript() {
        val pm = createPreRequestPm()
        pm.expect("hello").to.be.a("string")
        pm.expect(42).to.be.above(0)
        pm.expect(true).to.be.isTrue()
        pm.expect(false).to.be.isFalse()
        pm.expect(null).to.be.isNull()
    }

    @Test
    fun testExpectNegation() {
        val pm = createPreRequestPm()
        pm.expect(200).not.to.equal(404)
        pm.expect("hello").not.to.include("world")
        pm.expect(null).not.to.exist
    }

    @Test
    fun testMultipleTestsInSequence() {
        val pm = createPreRequestPm()
        pm.test("test 1") { pm.expect(1).to.equal(1) }
        pm.test("test 2") { pm.expect("hello").to.include("ell") }
        pm.test("test 3") { pm.expect(true).to.be.isTrue() }
        assertEquals(3, pm.testCollector.results.size)
        assertTrue(pm.testCollector.results.all { it.passed })
    }

    @Test
    fun testTestSkip() {
        val pm = createPreRequestPm()
        pm.test.skip("not ready") { pm.expect(true).to.be.isTrue() }
        assertEquals(1, pm.testCollector.results.size)
        assertTrue(pm.testCollector.results[0].passed)
        assertEquals("[SKIPPED]", pm.testCollector.results[0].error)
    }

    @Test
    fun testVariablesCompositeToObject() {
        val pm = createPreRequestPm()
        val obj = pm.variables.toObject()
        assertEquals("https://api.example.com", obj["host"])
        assertEquals("global-key", obj["apiKey"])
        assertEquals("proj-1", obj["projectId"])
    }

    @Test
    fun testVariablesCompositePrecedence() {
        val envVars = PmVariableScope().apply { set("key", "env-val") }
        val globalVars = PmVariableScope().apply { set("key", "global-val") }
        val collectionVars = PmVariableScope()
        val request = PmRequest()
        val testCollector = PmTestCollector()
        val info = PmInfo("prerequest", "Test", "req-1")

        val pm = PmObject.forPreRequest(
            request = request,
            environment = envVars,
            globals = globalVars,
            collectionVariables = collectionVars,
            testCollector = testCollector,
            info = info
        )

        assertEquals("env-val", pm.variables.get("key"))

        pm.variables.set("key", "local-val")
        assertEquals("local-val", pm.variables.get("key"))
        assertEquals("env-val", pm.environment.get("key"))
    }
}
