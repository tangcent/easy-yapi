package com.itangcent.easyapi.channel.curl

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit coverage for [CurlBuilder] (pure format step, no IDE fixture).
 *
 * The script-capable [CurlBuilder.build] paths that hit [PreScriptApplier] are
 * covered by `PreScriptApplierTest` (IDE fixture). Here we assert:
 * - [CurlBuilder.format] delegates to [CurlFormatter.format] (pure)
 * - [CurlBuilder.DEFAULT_HOST] is used when host is blank
 * - [CurlBuilder.build] early-returns [CurlBuilder.format] when scripts are gated off
 *   (so no `Project` / no `PreScriptApplier` dependency is incurred)
 * - Output is deterministic for identical inputs
 */
class CurlBuilderTest {

    private fun sampleEndpoint() = ApiEndpoint(
        name = "Get User",
        metadata = httpMetadata(
            path = "/api/users/1",
            method = HttpMethod.GET,
        ),
    )

    @Test
    fun `format with defaults delegates to CurlFormatter`() {
        val endpoint = sampleEndpoint()
        val fromBuilder = CurlBuilder.format(endpoint, "http://localhost:8080")
        val fromFormatter = CurlFormatter.format(endpoint, "http://localhost:8080")
        assertEquals(fromFormatter, fromBuilder)
    }

    @Test
    fun `format with default host contains placeholder`() {
        val endpoint = sampleEndpoint()
        val result = CurlBuilder.format(endpoint)
        // DEFAULT_HOST is "{{host}}" — output should be non-empty and contain it
        assertTrue("output must contain the default host placeholder", result.contains(CurlBuilder.DEFAULT_HOST))
        assertTrue(result.contains("curl"))
        assertTrue(result.contains("-X GET"))
    }

    @Test
    fun `format with blank host falls back to DEFAULT_HOST`() {
        val endpoint = sampleEndpoint()
        val result = CurlBuilder.format(endpoint, host = "")
        assertTrue(result.contains(CurlBuilder.DEFAULT_HOST))
    }

    @Test
    fun `build with runPreScripts false equals format`() = runBlocking {
        val endpoint = sampleEndpoint()
        val host = "http://localhost:8080"
        val options = CurlBuildOptions(runPreScripts = false)
        val built = CurlBuilder.build(project = null, endpoint, host, options)
        val formatted = CurlBuilder.format(endpoint, host, options)
        assertEquals(formatted, built)
    }

    @Test
    fun `build with empty scopes equals format even when runPreScripts true`() = runBlocking {
        // Gate: runPreScripts && scopes.isNotEmpty() — empty scopes short-circuits
        val endpoint = sampleEndpoint()
        val host = "http://localhost:8080"
        val options = CurlBuildOptions(runPreScripts = true, scopes = emptyList())
        val built = CurlBuilder.build(project = null, endpoint, host, options)
        val formatted = CurlBuilder.format(endpoint, host, options)
        assertEquals(formatted, built)
    }

    @Test
    fun `build with null project equals format even when runPreScripts true and scopes non-empty`() = runBlocking {
        // No project → cannot run scripts → short-circuits to format
        val endpoint = sampleEndpoint()
        val host = "http://localhost:8080"
        val options = CurlBuildOptions(
            runPreScripts = true,
            scopes = listOf(com.itangcent.easyapi.core.script.ScriptScope.Module("dummy")),
        )
        val built = CurlBuilder.build(project = null, endpoint, host, options)
        val formatted = CurlBuilder.format(endpoint, host, options)
        assertEquals(formatted, built)
    }

    @Test
    fun `format is deterministic for identical inputs`() {
        val endpoint = sampleEndpoint()
        val a = CurlBuilder.format(endpoint, "http://x", CurlBuildOptions())
        val b = CurlBuilder.format(endpoint, "http://x", CurlBuildOptions())
        assertEquals(a, b)
    }

    @Test
    fun `buildSync with no scripts equals format`() {
        // buildSync wraps build in runBlocking; with scripts off it must equal format
        val endpoint = sampleEndpoint()
        val host = "http://localhost:8080"
        val options = CurlBuildOptions(runPreScripts = false)
        val built = CurlBuilder.buildSync(project = null, endpoint, host, options)
        val formatted = CurlBuilder.format(endpoint, host, options)
        assertEquals(formatted, built)
    }
}
