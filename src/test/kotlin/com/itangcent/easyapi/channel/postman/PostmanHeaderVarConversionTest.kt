package com.itangcent.easyapi.channel.postman

import com.itangcent.easyapi.channel.postman.model.PostmanGson
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiHeader
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResultLoader
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests [PostmanFormatter] header-value `${...}` → `{{...}}` conversion for
 * unresolved placeholders, while leaving resolved placeholders and regex-capture
 * references untouched.
 *
 * Backs Req 5.1, 5.3, 5.4 and the NFR-3 byte-parity guarantee for placeholder-free
 * single-app exports.
 *
 * The test project's rule config defines `baseUrl` so `${baseUrl}` is treated as
 * resolvable by the config layer (Decision 2 — `ConfigReader.getFirst` is the
 * resolvability oracle). Namespaced vars like `${order-service-token}` and the
 * regex-capture `${1}` are exercised against the same config.
 */
class PostmanHeaderVarConversionTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.fromRules(
        project,
        "baseUrl" to "https://api.example.com"
    )

    @Test
    fun testUnresolvedNamespacedVarConvertedToDoubleBrace(): Unit = runBlocking {
        val endpoint = endpointWithHeaders(
            "Get Orders",
            "Authorization" to "Bearer \${order-service-token}"
        )
        val item = formatter().toItem(endpoint)
        val authHeader = item.request?.header?.firstOrNull { it.key == "Authorization" }
        assertNotNull(authHeader)
        assertEquals("Bearer {{order-service-token}}", authHeader?.value)
    }

    @Test
    fun testResolvedVarLeftAsDollarBrace(): Unit = runBlocking {
        val endpoint = endpointWithHeaders(
            "Get Config",
            "X-Base" to "\${baseUrl}"
        )
        val item = formatter().toItem(endpoint)
        val header = item.request?.header?.firstOrNull { it.key == "X-Base" }
        assertNotNull(header)
        // baseUrl is resolvable via the config layer — left as ${baseUrl} (Req 5.3).
        assertEquals("\${baseUrl}", header?.value)
    }

    @Test
    fun testRegexCaptureReferenceLeftUntouched(): Unit = runBlocking {
        val endpoint = endpointWithHeaders(
            "Capture",
            "X-Capture" to "Basic \${1}"
        )
        val item = formatter().toItem(endpoint)
        val header = item.request?.header?.firstOrNull { it.key == "X-Capture" }
        assertNotNull(header)
        // ${1} is a regex-capture reference — left untouched (Req 5.4).
        assertEquals("Basic \${1}", header?.value)
    }

    @Test
    fun testPlaceholderFreeExportByteParity(): Unit = runBlocking {
        val endpoint = endpointWithHeaders(
            "Get User",
            "Authorization" to "Bearer abc123",
            "Content-Type" to "application/json"
        )
        val item = formatter().toItem(endpoint)
        val actual = PostmanGson.pretty.toJson(item)
        // NFR-3: a placeholder-free single-app endpoint exports byte-identically
        // to the pre-feature golden. The converter fast-path returns the value
        // unchanged when no `${` is present, so the output is unaffected.
        // Explicit caller class avoids the coroutine-continuation name that
        // runBlocking injects into the stack trace.
        val expected = ResultLoader.load(
            PostmanHeaderVarConversionTest::class.java,
            "placeholder_free_export"
        )
        assertEquals(expected, actual)
    }

    private fun formatter() = PostmanFormatter(project = project)

    private fun endpointWithHeaders(
        name: String,
        vararg headers: Pair<String, String>
    ): ApiEndpoint = ApiEndpoint(
        name = name,
        metadata = httpMetadata(
            path = "/api/users",
            method = HttpMethod.GET,
            headers = headers.map { ApiHeader(name = it.first, value = it.second) }
        )
    )
}
