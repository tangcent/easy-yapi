package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppKeyValue
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.hoppscotchGson
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResultLoader
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Formatter-level test for unresolved `${...}` placeholder conversion in Hoppscotch
 * header values (`${x}` → `<<x>>`).
 *
 * Symmetric to `PostmanHeaderVarConversionTest` (Task 4.1) but targets the
 * Hoppscotch `<<...>>` variable syntax via [HoppscotchFormatter.buildHeaders].
 *
 * - _Requirements: Req 5.2, 5.3, 5.4; NFR-3_
 */
class HoppHeaderVarConversionTest : EasyApiLightCodeInsightFixtureTestCase() {

    /**
     * `baseUrl` is resolvable via the config layer; `order-service-token` is NOT
     * (unresolved → converts to `<<order-service-token>>`).
     */
    override fun createConfigReader(): ConfigReader? {
        return TestConfigReader.fromRules(project, "baseUrl" to "https://api.example.com")
    }

    private fun formatter(): HoppscotchFormatter = HoppscotchFormatter(
        project = project,
        options = HoppscotchFormatOptions(appendTimestamp = false)
    )

    /** Invokes the private `buildHeaders` via reflection (same pattern as HoppscotchFormatterPrivateMethodsTest). */
    private fun invokeBuildHeaders(meta: HttpMetadata): List<HoppKeyValue> {
        val method = HoppscotchFormatter::class.java.getDeclaredMethod(
            "buildHeaders", HttpMetadata::class.java
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(formatter(), meta) as List<HoppKeyValue>
    }

    // ==================== Req 5.2: unresolved ${x} → <<x>> ====================

    @Test
    fun testUnresolvedPlaceholderConvertsToAngleBrackets() {
        val meta = httpMetadata(
            path = "/api/orders",
            method = HttpMethod.GET,
            headers = listOf(
                ApiHeader(name = "Authorization", value = "Bearer \${order-service-token}")
            )
        )
        val headers = invokeBuildHeaders(meta)
        assertEquals(1, headers.size)
        assertEquals("Authorization", headers[0].key)
        assertEquals("Bearer <<order-service-token>>", headers[0].value)
    }

    // ==================== Req 5.3: resolved ${x} left untouched ====================

    @Test
    fun testResolvedPlaceholderLeftUntouched() {
        val meta = httpMetadata(
            path = "/api/orders",
            method = HttpMethod.GET,
            headers = listOf(
                ApiHeader(name = "X-Base", value = "\${baseUrl}")
            )
        )
        val headers = invokeBuildHeaders(meta)
        assertEquals(1, headers.size)
        assertEquals("\${baseUrl}", headers[0].value)
    }

    // ==================== Req 5.4: regex-capture ${1} left untouched ====================

    @Test
    fun testRegexCaptureReferenceLeftUntouched() {
        val meta = httpMetadata(
            path = "/api/orders",
            method = HttpMethod.GET,
            headers = listOf(
                ApiHeader(name = "X-Capture", value = "Basic \${1}")
            )
        )
        val headers = invokeBuildHeaders(meta)
        assertEquals(1, headers.size)
        assertEquals("Basic \${1}", headers[0].value)
    }

    // ==================== NFR-3: byte-parity golden (placeholder-free) ====================

    /**
     * A placeholder-free header set must serialize byte-identically to the
     * pre-feature golden — the converter's fast path returns the value
     * unchanged when no `${` is present, so existing single-app exports are
     * not altered.
     */
    @Test
    fun testPlaceholderFreeHeadersByteParityGolden() {
        val meta = httpMetadata(
            path = "/api/orders",
            method = HttpMethod.GET,
            headers = listOf(
                ApiHeader(name = "Authorization", value = "Bearer static-token"),
                ApiHeader(name = "Accept", value = "application/json")
            )
        )
        val headers = invokeBuildHeaders(meta)
        val actual = hoppscotchGson(prettyPrint = true).toJson(headers)
        val expected = ResultLoader.load()
        assertEquals(expected, actual)
    }
}
