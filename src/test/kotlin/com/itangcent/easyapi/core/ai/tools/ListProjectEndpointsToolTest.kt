package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.cache.api.ApiIndex
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.GrpcStreamingType
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.core.util.json.GsonUtils
import com.intellij.testFramework.registerServiceInstance
import kotlinx.coroutines.runBlocking
import org.junit.Assert

/**
 * Tests for [ListProjectEndpointsTool].
 *
 * Verifies the three documented behaviors:
 * - Returns `"cache not ready"` when [ApiIndex.isReady] is `false`.
 * - Returns a JSON array of `{className, name, httpMethod, path}` summaries
 *   for HTTP endpoints.
 * - Filters out gRPC endpoints (only HTTP metadata is exposed in v1).
 *
 * Each test registers a fresh [ApiIndex] via `registerServiceInstance` so
 * the cache state is fully controlled — no real scan is performed.
 */
class ListProjectEndpointsToolTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun ctx(): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiRuntimeConfig(
            provider = AiProvider.OPENAI,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30, maxRequests = 8
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = AgentMemory(),
        approvals = NoOpApprovalGate()
    )

    override fun setUp() {
        super.setUp()
        // Default: register an empty (not-yet-ready) ApiIndex. Individual
        // tests override with a populated index when needed.
        project.registerServiceInstance(
            serviceInterface = ApiIndex::class.java,
            instance = ApiIndex()
        )
    }

    // ------------------------------------------------------------------
    // cache-not-ready path
    // ------------------------------------------------------------------

    fun testReturnsCacheNotReadyWhenInitialScanPending() {
        // Fresh ApiIndex: cacheReady has not been completed, so isReady() == false.
        val result = runBlocking { ListProjectEndpointsTool().execute(emptyMap(), ctx()) }
        Assert.assertTrue("expected Text result", result is ToolResult.Text)
        Assert.assertEquals(
            "cache not ready",
            (result as ToolResult.Text).value
        )
    }

    // ------------------------------------------------------------------
    // HTTP endpoints path
    // ------------------------------------------------------------------

    fun testReturnsHttpEndpointSummaries() {
        registerPopulatedIndex(
            ApiEndpoint(
                name = "getUser",
                className = "com.example.UserController",
                metadata = HttpMetadata(path = "/users/{id}", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "createUser",
                className = "com.example.UserController",
                metadata = HttpMetadata(path = "/users", method = HttpMethod.POST)
            )
        )

        val result = runBlocking { ListProjectEndpointsTool().execute(emptyMap(), ctx()) }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        @Suppress("UNCHECKED_CAST")
        val summaries = GsonUtils.fromJson<List<Map<String, Any?>>>(
            (result as ToolResult.Text).value
        )
        Assert.assertEquals("should return both HTTP endpoints", 2, summaries.size)

        val first = summaries[0]
        Assert.assertEquals("com.example.UserController", first["className"])
        Assert.assertEquals("getUser", first["name"])
        Assert.assertEquals("GET", first["httpMethod"])
        Assert.assertEquals("/users/{id}", first["path"])

        val second = summaries[1]
        Assert.assertEquals("com.example.UserController", second["className"])
        Assert.assertEquals("createUser", second["name"])
        Assert.assertEquals("POST", second["httpMethod"])
        Assert.assertEquals("/users", second["path"])
    }

    fun testEmptyCacheReturnsEmptyArray() {
        // A ready but empty cache yields an empty JSON array, not "cache not ready".
        registerPopulatedIndex()

        val result = runBlocking { ListProjectEndpointsTool().execute(emptyMap(), ctx()) }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        val text = (result as ToolResult.Text).value
        Assert.assertEquals("[]", text)
    }

    // ------------------------------------------------------------------
    // gRPC filtering
    // ------------------------------------------------------------------

    fun testGrpcEndpointsAreFilteredOut() {
        registerPopulatedIndex(
            ApiEndpoint(
                name = "getUser",
                className = "com.example.UserController",
                metadata = HttpMetadata(path = "/users/{id}", method = HttpMethod.GET)
            ),
            ApiEndpoint(
                name = "SayHello",
                className = "com.example.GreeterService",
                metadata = GrpcMetadata(
                    path = "/com.example.Greeter/SayHello",
                    serviceName = "Greeter",
                    methodName = "SayHello",
                    packageName = "com.example",
                    streamingType = GrpcStreamingType.UNARY
                )
            )
        )

        val result = runBlocking { ListProjectEndpointsTool().execute(emptyMap(), ctx()) }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        @Suppress("UNCHECKED_CAST")
        val summaries = GsonUtils.fromJson<List<Map<String, Any?>>>(
            (result as ToolResult.Text).value
        )
        Assert.assertEquals(
            "gRPC endpoint should be filtered out, only HTTP remains",
            1,
            summaries.size
        )
        Assert.assertEquals("getUser", summaries[0]["name"])
        Assert.assertEquals("/users/{id}", summaries[0]["path"])
    }

    fun testOnlyGrpcEndpointsYieldsEmptyArray() {
        // A cache containing only gRPC endpoints should yield an empty
        // array, not "cache not ready" (the cache IS ready, just empty of HTTP).
        registerPopulatedIndex(
            ApiEndpoint(
                name = "SayHello",
                className = "com.example.GreeterService",
                metadata = GrpcMetadata(
                    path = "/com.example.Greeter/SayHello",
                    serviceName = "Greeter",
                    methodName = "SayHello",
                    packageName = "com.example",
                    streamingType = GrpcStreamingType.UNARY
                )
            )
        )

        val result = runBlocking { ListProjectEndpointsTool().execute(emptyMap(), ctx()) }
        Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
        Assert.assertEquals("[]", (result as ToolResult.Text).value)
    }

    // ------------------------------------------------------------------
    // Tool metadata
    // ------------------------------------------------------------------

    fun testToolKindIsPerception() {
        Assert.assertEquals(ToolKind.PERCEPTION, ListProjectEndpointsTool().kind)
    }

    fun testToolDoesNotRequireApproval() {
        Assert.assertFalse(ListProjectEndpointsTool().requiresApproval)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Replaces the registered [ApiIndex] with one pre-populated via
     * [ApiIndex.updateEndpoints] (which completes `cacheReady`, flipping
     * `isReady()` to `true`).
     */
    private fun registerPopulatedIndex(vararg endpoints: ApiEndpoint) {
        val index = ApiIndex()
        runBlocking { index.updateEndpoints(endpoints.toList()) }
        project.registerServiceInstance(
            serviceInterface = ApiIndex::class.java,
            instance = index
        )
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}
