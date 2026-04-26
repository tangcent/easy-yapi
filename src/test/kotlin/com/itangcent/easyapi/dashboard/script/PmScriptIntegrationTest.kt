package com.itangcent.easyapi.dashboard.script

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import javax.script.ScriptEngineManager

class PmScriptIntegrationTest {

    private lateinit var engine: javax.script.ScriptEngine

    @Before
    fun setUp() {
        engine = ScriptEngineManager().getEngineByName("groovy")
            ?: throw IllegalStateException("Groovy engine not available")
    }

    private fun createPostResponsePm(): PmObject {
        val envVars = PmVariableScope()
        val globalVars = PmVariableScope()
        val collectionVars = PmVariableScope()
        val request = PmRequest(url = "https://api.example.com/users", method = "POST")
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/json")),
            responseTime = 50,
            responseSize = 100,
            rawBody = """{"name":"Alice","token":"abc123"}"""
        )
        val testCollector = PmTestCollector()
        val info = PmInfo("test", "Create User", "req-001")

        return PmObject.forPostResponse(
            request = request,
            response = response,
            environment = envVars,
            globals = globalVars,
            collectionVariables = collectionVars,
            testCollector = testCollector,
            cookies = PmCookies(),
            info = info
        )
    }

    private fun executeScript(script: String, pm: PmObject): List<TestResult> {
        val bindings = engine.createBindings()
        bindings["pm"] = pm
        bindings["environment"] = pm.environment
        bindings["globals"] = pm.globals
        bindings["collectionVariables"] = pm.collectionVariables
        bindings["request"] = pm.request
        bindings["response"] = pm.response
        bindings["test"] = pm.test
        bindings["cookies"] = pm.cookies
        bindings["info"] = pm.info
        engine.eval(script, bindings)
        return pm.testCollector.results
    }

    @Test
    fun testPmTestFromGroovy() {
        val pm = createPostResponsePm()
        val script = """
            pm.test("Status is 200") {
                pm.expect(pm.response.code).to.eql(200)
            }
        """.trimIndent()

        val results = executeScript(script, pm)
        assertEquals(1, results.size)
        assertEquals("Status is 200", results[0].name)
        assertTrue(results[0].passed)
    }

    @Test
    fun testMultiplePmTestFromGroovy() {
        val pm = createPostResponsePm()
        val script = """
            def json = pm.response.json()
            
            pm.test("Status is 200") {
                pm.expect(pm.response.code).to.eql(200)
            }
            
            pm.test("Response has user name") {
                pm.expect(json.name).to.eql("Alice")
            }
            
            pm.test("Response has token") {
                pm.expect(json.token).to.eql("abc123")
            }
        """.trimIndent()

        val results = executeScript(script, pm)
        assertEquals(3, results.size)
        assertTrue(results[0].passed)
        assertTrue(results[1].passed)
        assertTrue(results[2].passed)
    }

    @Test
    fun testFailedPmTestFromGroovy() {
        val pm = createPostResponsePm()
        val script = """
            pm.test("Status is 404") {
                pm.expect(pm.response.code).to.eql(404)
            }
        """.trimIndent()

        val results = executeScript(script, pm)
        assertEquals(1, results.size)
        assertFalse(results[0].passed)
        assertNotNull(results[0].error)
    }

    @Test
    fun testEnvironmentSetFromGroovy() {
        val pm = createPostResponsePm()
        val script = """
            def json = pm.response.json()
            pm.environment.set("token", json.token)
        """.trimIndent()

        executeScript(script, pm)
        assertEquals("abc123", pm.environment.get("token"))
    }

    @Test
    fun testPmResponseJsonFromGroovy() {
        val pm = createPostResponsePm()
        val script = """
            def json = pm.response.json()
            assert json.name == "Alice"
            assert json.token == "abc123"
        """.trimIndent()

        executeScript(script, pm)
    }

    @Test
    fun testPmResponseBddFromGroovy() {
        val pm = createPostResponsePm()
        val script = """
            pm.response.to.be.ok
            pm.response.to.have.status(200)
            pm.response.to.have.header("Content-Type")
        """.trimIndent()

        executeScript(script, pm)
    }

    @Test
    fun testPmSkipFromGroovy() {
        val pm = createPostResponsePm()
        val script = """
            pm.test("Running test") {
                pm.expect(1).to.eql(1)
            }
            pm.test.skip("Skipped test") {
                // should not run
            }
        """.trimIndent()

        val results = executeScript(script, pm)
        assertEquals(2, results.size)
        assertTrue(results[0].passed)
        assertTrue(results[1].passed)
        assertEquals("[SKIPPED]", results[1].error)
    }

    @Test
    fun testLoggerFromGroovy() {
        val pm = createPostResponsePm()
        val script = """
            logger.info("test post----")
        """.trimIndent()

        val bindings = engine.createBindings()
        bindings["pm"] = pm
        bindings["logger"] = TestLogger()
        engine.eval(script, bindings)
    }

    @Test
    fun testUserScriptFromBugReport() {
        val pm = createPostResponsePm()
        val script = """
            logger.info("test post----")
            
            def json = pm.response.json()
            
            pm.test("Status is 200") {
                pm.expect(pm.response.code).to.eql(200)
            }
            
            pm.test("Response has user name") {
                pm.expect(json.name).to.eql("Alice")
            }
            
            pm.environment.set("token", json.token)
        """.trimIndent()

        val bindings = engine.createBindings()
        bindings["pm"] = pm
        bindings["environment"] = pm.environment
        bindings["globals"] = pm.globals
        bindings["collectionVariables"] = pm.collectionVariables
        bindings["request"] = pm.request
        bindings["response"] = pm.response
        bindings["test"] = pm.test
        bindings["cookies"] = pm.cookies
        bindings["info"] = pm.info
        bindings["logger"] = TestLogger()
        engine.eval(script, bindings)

        val results = pm.testCollector.results
        assertEquals(2, results.size)
        assertTrue("First test should pass", results[0].passed)
        assertEquals("Status is 200", results[0].name)
        assertTrue("Second test should pass", results[1].passed)
        assertEquals("Response has user name", results[1].name)
        assertEquals("abc123", pm.environment.get("token"))
    }

    @Test
    fun testEnvironmentSetPersistsViaLiveScope() {
        val persistedVars = mutableMapOf("base_url" to "http://localhost")
        val envVars = LivePmVariableScope(
            variables = persistedVars.toMap(),
            onSet = { name, value -> persistedVars[name] = value },
            onUnset = { name -> persistedVars.remove(name) }
        )
        val globalVars = PmVariableScope()
        val collectionVars = PmVariableScope()
        val request = PmRequest(url = "https://api.example.com/users", method = "POST")
        val response = PmResponse(
            code = 200, status = "OK",
            headers = PmHeaderList(listOf("Content-Type" to "application/json")),
            responseTime = 50, responseSize = 100,
            rawBody = """{"token":"xyz789"}"""
        )
        val testCollector = PmTestCollector()
        val info = PmInfo("test", "Login", "req-002")
        val pm = PmObject.forPostResponse(
            request = request, response = response,
            environment = envVars, globals = globalVars,
            collectionVariables = collectionVars,
            testCollector = testCollector, cookies = PmCookies(),
            info = info
        )

        val script = """
            def json = pm.response.json()
            pm.environment.set("token", json.token)
            pm.environment.set("token-test", "123")
        """.trimIndent()

        val bindings = engine.createBindings()
        bindings["pm"] = pm
        bindings["environment"] = pm.environment
        bindings["logger"] = TestLogger()
        engine.eval(script, bindings)

        assertEquals("xyz789", pm.environment.get("token"))
        assertEquals("123", pm.environment.get("token-test"))
        assertEquals("xyz789", persistedVars["token"])
        assertEquals("123", persistedVars["token-test"])
        assertEquals("http://localhost", persistedVars["base_url"])
    }

    class TestLogger {
        fun info(msg: String) {}
        fun warn(msg: String) {}
        fun error(msg: String) {}
    }
}
