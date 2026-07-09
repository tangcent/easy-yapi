package com.itangcent.easyapi.exporter.channel.curl

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * IDE-fixture tests for [CurlExportResolver].
 *
 * [CurlExportResolver] is a [com.intellij.openapi.components.Service] scoped to
 * the project, so it needs a real [com.intellij.openapi.project.Project] to
 * construct (via [CurlExportResolver.getInstance]). It also reads [CurlSettings]
 * internally via [com.itangcent.easyapi.settings.settings], so we use
 * [SettingBinder.update] to set the render mode before each test.
 *
 * The ALWAYS_ASK paths that show a dialog are NOT covered here (they require
 * UI interaction). NEVER_RENDER and empty-list short-circuits are covered.
 */
class CurlExportResolverTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun setRenderMode(mode: CurlRenderMode) {
        SettingBinder.getInstance(project).update(CurlSettings::class) {
            renderMode = mode.name
        }
    }

    private fun updateSettings(block: CurlSettings.() -> Unit) {
        SettingBinder.getInstance(project).update(CurlSettings::class) { block() }
    }

    private fun simpleEndpoint(name: String = "Test", path: String = "/api/test") = ApiEndpoint(
        name = name,
        metadata = httpMetadata(path = path, method = HttpMethod.GET),
    )

    fun `test resolve with NEVER_RENDER returns endpoint unchanged`() = runBlocking {
        setRenderMode(CurlRenderMode.NEVER_RENDER)
        val endpoint = simpleEndpoint()
        val result = CurlExportResolver.getInstance(project).resolve(endpoint, "http://localhost:8080")
        assertNotNull(result)
        assertEquals(endpoint, result!!.first)
        assertEquals("http://localhost:8080", result.second)
    }

    fun `test resolve with NEVER_RENDER preserves placeholders in path`() = runBlocking {
        setRenderMode(CurlRenderMode.NEVER_RENDER)
        val endpoint = simpleEndpoint(path = "/api/{{userId}}")
        val result = CurlExportResolver.getInstance(project).resolve(endpoint, "http://localhost:8080")
        assertNotNull(result)
        assertEquals("/api/{{userId}}", result!!.first.httpMetadata?.path)
    }

    fun `test resolveAll with NEVER_RENDER returns endpoints unchanged`() = runBlocking {
        setRenderMode(CurlRenderMode.NEVER_RENDER)
        val endpoints = listOf(
            simpleEndpoint("A", "/api/a"),
            simpleEndpoint("B", "/api/b"),
        )
        val result = CurlExportResolver.getInstance(project).resolveAll(endpoints, "http://localhost:8080")
        assertNotNull(result)
        assertEquals(2, result!!.first.size)
        assertEquals("A", result.first[0].name)
        assertEquals("B", result.first[1].name)
    }

    fun `test resolveAll with empty endpoint list returns empty list regardless of renderMode`() = runBlocking {
        // Empty list short-circuits before touching services, so ALWAYS_ASK won't prompt.
        setRenderMode(CurlRenderMode.ALWAYS_ASK)
        val result = CurlExportResolver.getInstance(project).resolveAll(emptyList(), "http://localhost:8080")
        assertNotNull(result)
        assertEquals(0, result!!.first.size)
    }

    fun `test resolveAll with NEVER_RENDER preserves placeholders`() = runBlocking {
        setRenderMode(CurlRenderMode.NEVER_RENDER)
        val endpoints = listOf(simpleEndpoint(path = "/api/{{userId}}"))
        val result = CurlExportResolver.getInstance(project).resolveAll(endpoints, "http://localhost:8080")
        assertNotNull(result)
        assertEquals("/api/{{userId}}", result!!.first[0].httpMetadata?.path)
    }

    // ===== formatForCopy tests =====
    //
    // formatForCopy is the single-endpoint "copy as cURL" pipeline
    // (resolve → format with persisted settings options). The ALWAYS_ASK prompt
    // path needs a UI dialog and is NOT covered here; NEVER_RENDER short-circuits
    // deterministically and lets us assert the full pipeline end-to-end.

    fun `test formatForCopy with NEVER_RENDER returns formatted curl`() = runBlocking {
        setRenderMode(CurlRenderMode.NEVER_RENDER)
        val endpoint = simpleEndpoint(name = "Get User", path = "/api/users/1")
        val curl = CurlExportResolver.getInstance(project).formatForCopy(endpoint, "http://localhost:8080")
        assertNotNull("NEVER_RENDER never cancels, so result must be non-null", curl)
        assertTrue("should contain curl: $curl", curl!!.contains("curl"))
        assertTrue("should contain method: $curl", curl.contains("-X GET"))
        assertTrue("should contain url: $curl", curl.contains("http://localhost:8080/api/users/1"))
    }

    fun `test formatForCopy preserves placeholders when NEVER_RENDER`() = runBlocking {
        setRenderMode(CurlRenderMode.NEVER_RENDER)
        val endpoint = simpleEndpoint(path = "/api/{{userId}}")
        val curl = CurlExportResolver.getInstance(project).formatForCopy(endpoint, "http://localhost:8080")
        assertNotNull(curl)
        assertTrue("placeholder should survive: $curl", curl!!.contains("{{userId}}"))
    }

    fun `test formatForCopy reflects CurlSettings longFlags in output`() = runBlocking {
        // Pin the central contract of the refactor: format options flow from
        // CurlSettings through toFormatOptions into the formatted output. If a
        // future change decouples formatForCopy from settings, this catches it.
        setRenderMode(CurlRenderMode.NEVER_RENDER)
        updateSettings { longFlags = true }
        val endpoint = ApiEndpoint(
            name = "Create",
            metadata = httpMetadata(
                path = "/api/create",
                method = HttpMethod.POST,
                contentType = "application/json",
                parameters = listOf(
                    ApiParameter(name = "name", binding = ParameterBinding.Body, example = "John")
                ),
            ),
        )
        val curl = CurlExportResolver.getInstance(project).formatForCopy(endpoint, "http://localhost:8080")
        assertNotNull(curl)
        assertTrue("longFlags from settings should produce --request: $curl", curl!!.contains("--request"))
        assertTrue("longFlags from settings should produce --data: $curl", curl.contains("--data"))
        assertFalse("should not use short -X: $curl", curl.contains("-X "))
    }

    fun `test formatForCopy respects includeComments off for single endpoint`() = runBlocking {
        // Single-endpoint format() never emits ## comments regardless of option,
        // but flipping includeComments=false should at least not break the pipeline
        // and still produce a valid curl. Guards against option-wiring regressions.
        setRenderMode(CurlRenderMode.NEVER_RENDER)
        updateSettings { includeComments = false }
        val endpoint = simpleEndpoint(name = "Get", path = "/api/test")
        val curl = CurlExportResolver.getInstance(project).formatForCopy(endpoint, "http://localhost:8080")
        assertNotNull(curl)
        assertTrue("should still contain curl: $curl", curl!!.contains("curl"))
    }
}
