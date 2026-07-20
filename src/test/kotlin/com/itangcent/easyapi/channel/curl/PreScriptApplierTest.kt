package com.itangcent.easyapi.channel.curl

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiHeader
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.GrpcStreamingType
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.script.ScriptCache
import com.itangcent.easyapi.core.script.ScriptCacheService
import com.itangcent.easyapi.core.script.ScriptScope
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.EnvironmentSettings
import com.itangcent.easyapi.core.settings.module.ParsingOutputSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * IDE-fixture tests for [PreScriptApplier].
 *
 * [PreScriptApplier] is a `@Service(PROJECT)` that needs a real [com.intellij.openapi.project.Project]
 * (for [ScriptCacheService], [com.itangcent.easyapi.core.script.env.EnvironmentService],
 * [com.itangcent.easyapi.core.script.pm.PmScriptExecutor]). The light fixture provides all of these.
 *
 * Coverage:
 *  - No script cached → returned deep copy has no mutation effect.
 *  - gRPC endpoint → returned copy unchanged (HTTP-only gate).
 *  - Header-upserting script → returned copy contains the header; **original endpoint unchanged**.
 *  - Throwing script → returns un-mutated copy; no exception escapes.
 *  - Body-setting script → returned copy formats with the scripted body.
 *
 * Each test uses a distinct [ScriptScope.Class] key so cached scripts cannot leak across
 * tests; [tearDown] deletes them for good measure.
 *
 * NOTE: [EasyApiLightCodeInsightFixtureTestCase] extends the JUnit 3-style
 * [com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase], so setup/teardown
 * must override [setUp] / [tearDown] (JUnit 4 `@Before` is NOT honored).
 */
class PreScriptApplierTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var applier: PreScriptApplier
    private lateinit var scriptCache: ScriptCacheService

    /** Scopes seeded per-test; tracked for tearDown cleanup. */
    private val seededScopes = mutableListOf<ScriptScope>()

    override fun setUp() {
        super.setUp()
        // EnvironmentService reads EnvironmentSettings; seed empty so LivePmVariableScope
        // (used inside PreScriptApplier) has an empty variable map.
        val settingBinder = SettingBinder.getInstance(project)
        settingBinder.save(EnvironmentSettings())
        settingBinder.save(ParsingOutputSettings())
        applier = PreScriptApplier.getInstance(project)
        scriptCache = ScriptCacheService.getInstance(project)
    }

    override fun tearDown() {
        try {
            seededScopes.forEach { runCatching { scriptCache.delete(it) } }
        } finally {
            super.tearDown()
        }
    }

    private fun httpEndpoint(
        name: String = "Test",
        path: String = "/api/test",
        method: HttpMethod = HttpMethod.GET,
        headers: List<ApiHeader> = emptyList(),
    ): ApiEndpoint = ApiEndpoint(
        name = name,
        metadata = httpMetadata(path = path, method = method, headers = headers),
    )

    private fun grpcEndpoint(): ApiEndpoint = ApiEndpoint(
        name = "GrpcTest",
        metadata = GrpcMetadata(
            path = "/test.Service/Method",
            serviceName = "Service",
            methodName = "Method",
            packageName = "test",
            streamingType = GrpcStreamingType.UNARY,
        ),
    )

    /** Seeds a pre-request script for [scope] and tracks it for tearDown. */
    private fun seedScript(scope: ScriptScope, script: String) {
        scriptCache.save(scope, ScriptCache(preRequestScript = script))
        seededScopes.add(scope)
    }

    // ------------------------------------------------------------------
    // Test 1: no script cached → deep copy with no mutation effect
    // ------------------------------------------------------------------

    fun `test no script cached returns deep copy with no mutation effect`() = runTest {
        val endpoint = httpEndpoint(headers = listOf(ApiHeader("Accept", "application/json")))
        val originalHeaders = endpoint.metadata.let { (it as HttpMetadata).headers.toList() }

        val result = applier.applyScripts(endpoint, "http://localhost:8080", listOf(ScriptScope.Class("test.none")))

        val resultHttp = result.metadata as HttpMetadata
        assertEquals("path preserved", "/api/test", resultHttp.path)
        assertEquals("method preserved", HttpMethod.GET, resultHttp.method)
        assertEquals("header count preserved", 1, resultHttp.headers.size)
        assertEquals("Accept", resultHttp.headers[0].name)
        // original endpoint untouched
        assertEquals(originalHeaders, endpoint.metadata.let { (it as HttpMetadata).headers.toList() })
    }

    // ------------------------------------------------------------------
    // Test 2: gRPC endpoint → returned copy unchanged (HTTP-only gate)
    // ------------------------------------------------------------------

    fun `test grpc endpoint returns deep copy unchanged`() = runTest {
        val endpoint = grpcEndpoint()
        // Seed a script that would mutate headers if it ran — proves the HTTP-only gate holds.
        seedScript(ScriptScope.Class("test.grpc"), "pm.request.headers.upsert('X-Foo','bar')")

        val result = applier.applyScripts(endpoint, "http://localhost:8080", listOf(ScriptScope.Class("test.grpc")))

        assertTrue("gRPC metadata preserved", result.metadata is GrpcMetadata)
        val resultGrpc = result.metadata as GrpcMetadata
        assertEquals("/test.Service/Method", resultGrpc.path)
        assertEquals("Service", resultGrpc.serviceName)
        assertEquals("Method", resultGrpc.methodName)
    }

    // ------------------------------------------------------------------
    // Test 3: header-upserting script → returned copy has header; original unchanged
    // ------------------------------------------------------------------

    fun `test script upserts header on returned copy and leaves original unchanged`() = runTest {
        val scope = ScriptScope.Class("test.header-upsert")
        seedScript(scope, "pm.request.headers.upsert('X-Foo', 'bar')")
        val endpoint = httpEndpoint(headers = listOf(ApiHeader("Accept", "application/json")))

        // Capture original header state BEFORE the call (deep-copy contract).
        val originalHeaders = (endpoint.metadata as HttpMetadata).headers.toList()

        val result = applier.applyScripts(endpoint, "http://localhost:8080", listOf(scope))

        val resultHttp = result.metadata as HttpMetadata
        val headerNames = resultHttp.headers.map { it.name }
        assertTrue("returned copy should contain scripted header X-Foo", headerNames.contains("X-Foo"))
        assertEquals("bar", resultHttp.headers.first { it.name == "X-Foo" }.value)

        // Original endpoint MUST NOT be mutated.
        val originalAfter = (endpoint.metadata as HttpMetadata).headers.toList()
        assertEquals("original header count unchanged", 1, originalAfter.size)
        assertEquals("Accept", originalAfter[0].name)
        assertFalse("original should NOT contain X-Foo", originalAfter.any { it.name == "X-Foo" })
        assertEquals("captured-before equals current-after", originalHeaders, originalAfter)
    }

    // ------------------------------------------------------------------
    // Test 4: throwing script → returns un-mutated copy; no exception escapes
    // ------------------------------------------------------------------

    fun `test throwing script returns unmutated copy and no exception escapes`() = runTest {
        val scope = ScriptScope.Class("test.throwing")
        seedScript(scope, "throw new RuntimeException('boom')")
        val endpoint = httpEndpoint(headers = listOf(ApiHeader("Accept", "application/json")))

        // The call should NOT throw — best-effort.
        val result = applier.applyScripts(endpoint, "http://localhost:8080", listOf(scope))

        val resultHttp = result.metadata as HttpMetadata
        assertEquals("path unchanged on script failure", "/api/test", resultHttp.path)
        assertEquals("method unchanged on script failure", HttpMethod.GET, resultHttp.method)
        assertEquals("header count unchanged on script failure", 1, resultHttp.headers.size)
        assertEquals("Accept", resultHttp.headers[0].name)
        // Original untouched too.
        val originalAfter = (endpoint.metadata as HttpMetadata).headers.toList()
        assertEquals(1, originalAfter.size)
    }

    // ------------------------------------------------------------------
    // Test 5: body-setting script → returned copy formats with scripted body
    // ------------------------------------------------------------------

    fun `test script sets body raw and formatter picks it up`() = runTest {
        val scope = ScriptScope.Class("test.body-raw")
        seedScript(scope, "pm.request.body.raw = '{\"x\":1}'")
        // buildBody only emits the JSON branch when contentType contains "json";
        // without a contentType (and no meta.body), the formatter returns "".
        val endpoint = ApiEndpoint(
            name = "Test",
            metadata = httpMetadata(
                path = "/api/test",
                method = HttpMethod.POST,
                contentType = "application/json",
            ),
        )

        val result = applier.applyScripts(endpoint, "http://localhost:8080", listOf(scope))

        // The scripted body is stashed in extensions[RESOLVED_BODY_JSON_KEY]; CurlFormatter
        // checks that key first (carrier reuse).
        val curl = CurlFormatter.format(result, "http://localhost:8080", CurlFormatOptions())
        assertTrue("curl should contain scripted body", curl.contains("\"x\":1"))
    }

    // ------------------------------------------------------------------
    // Test 6: empty scopes → no-op deep copy (defensive)
    // ------------------------------------------------------------------

    fun `test empty scopes returns deep copy`() = runTest {
        val endpoint = httpEndpoint()
        val result = applier.applyScripts(endpoint, "http://localhost:8080", emptyList())
        val resultHttp = result.metadata as HttpMetadata
        assertEquals("/api/test", resultHttp.path)
        assertEquals(HttpMethod.GET, resultHttp.method)
    }
}
