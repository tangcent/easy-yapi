package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.yapi.YapiConfig
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.http.HttpClient
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.http.HttpResponse
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.wrap
import org.junit.Assert.*
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class YapiExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporterProject: com.intellij.openapi.project.Project
    private lateinit var exporter: YapiExporter

    private companion object {
        const val SERVER_REJECTION = "token is invalid, please copy a new token from project settings"
    }

    override fun setUp() {
        super.setUp()
        exporterProject = createExporterProject()
        exporter = YapiExporter(exporterProject)
    }

    @org.junit.Test
    fun `test export without server configuration returns error`() {
        exporterProject = createExporterProject(serverUrl = null)
        exporter = YapiExporter(exporterProject)
        val endpoint = createTestEndpoint()
        val context = createTestContext(listOf(endpoint))

        val result = kotlinx.coroutines.runBlocking { exporter.export(context) }

        assertTrue("Expected Error but got $result", result is ExportResult.Error)
        val error = result as ExportResult.Error
        assertTrue(error.message.contains("YAPI server URL"))
    }

    @org.junit.Test
    fun `test export without token returns error`() {
        val endpoint = createTestEndpoint()
        val context = createTestContext(listOf(endpoint), selectedToken = null)

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = null) }

        assertTrue("Expected Error but got $result", result is ExportResult.Error)
        val error = result as ExportResult.Error
        assertTrue(
            "Error message should mention token: ${error.message}",
            error.message.contains("token", ignoreCase = true)
        )
    }

    @org.junit.Test
    fun `test export with valid configuration attempts upload`() {
        val endpoint = createTestEndpoint(
            name = "Get User",
            path = "/api/users/{id}",
            method = HttpMethod.GET
        )

        val context = createTestContext(listOf(endpoint), selectedToken = "test-token")

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "test-token") }

        assertTrue(
            "Expected Error (network) or Success, got $result",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    @org.junit.Test
    fun `test export with multiple endpoints`() {
        val endpoints = listOf(
            createTestEndpoint("Get Users", "/api/users", HttpMethod.GET),
            createTestEndpoint("Create User", "/api/users", HttpMethod.POST),
            createTestEndpoint("Update User", "/api/users/{id}", HttpMethod.PUT)
        )

        val context = createTestContext(endpoints, selectedToken = "test-token")
        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "test-token") }

        assertTrue(
            "Expected Error (network) or Success, got $result",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    @org.junit.Test
    fun `test export handles different http methods`() {
        val endpoints = listOf(
            createTestEndpoint("GET Test", "/test", HttpMethod.GET),
            createTestEndpoint("POST Test", "/test", HttpMethod.POST),
            createTestEndpoint("PUT Test", "/test", HttpMethod.PUT),
            createTestEndpoint("DELETE Test", "/test", HttpMethod.DELETE)
        )

        val context = createTestContext(endpoints, selectedToken = "test-token")
        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "test-token") }

        assertTrue(
            "Expected Error (network) or Success, got $result",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    @org.junit.Test
    fun `test export context validation`() {
        val endpoint = createTestEndpoint()
        val context = createTestContext(listOf(endpoint))

        assertEquals(exporterProject, context.project)
        assertEquals("yapi", context.channelId)
        assertTrue(context.endpoints.isNotEmpty())
    }

    @org.junit.Test
    fun `test export with empty endpoints list`() {
        val endpoints = emptyList<com.itangcent.easyapi.core.export.ApiEndpoint>()

        val context = createTestContext(endpoints, selectedToken = "test-token")
        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "test-token") }

        assertTrue(
            "Expected Error (no endpoints) or Success (empty upload), got $result",
            result is ExportResult.Error || result is ExportResult.Success
        )
    }

    @org.junit.Test
    fun `test export surfaces the server rejection reason for a rejected token`() {
        // Reproduces issue #1470: a YApi server (e.g. Yapix after migrating from YApi)
        // rejects the legacy project token with an actionable message. The error keeps the
        // folder for context, but must carry the server's own words — not the bare
        // 'Failed to resolve cart' line that points at the folder structure.
        val httpClient = mock<HttpClient> {
            onBlocking { execute(any()) } doReturn HttpResponse(
                code = 200,
                body = """{"errcode":42014,"errmsg":"$SERVER_REJECTION","data":null}"""
            )
        }
        exporterProject = createExporterProject(httpClient = httpClient)
        exporter = YapiExporter(exporterProject)

        val endpoint = createTestEndpoint(name = "Get User", path = "/api/users/{id}")
        val context = createTestContext(listOf(endpoint), selectedToken = "legacy-token")

        val result = kotlinx.coroutines.runBlocking { exporter.export(context, selectedToken = "legacy-token") }

        assertTrue("Expected Error, got $result", result is ExportResult.Error)
        val error = result as ExportResult.Error
        assertTrue(
            "Export error should keep the cart context, got: ${error.message}",
            error.message.contains("Failed to resolve cart 'anonymous'")
        )
        assertTrue(
            "Export error should carry the server's reason, got: ${error.message}",
            error.message.contains(SERVER_REJECTION)
        )
    }

    private fun createTestEndpoint(
        name: String = "Test API",
        path: String = "/api/test",
        method: HttpMethod = HttpMethod.GET,
        description: String? = null
    ): com.itangcent.easyapi.core.export.ApiEndpoint {
        return com.itangcent.easyapi.core.export.ApiEndpoint(
            name = name,
            description = description,
            metadata = httpMetadata(
                path = path,
                method = method
            )
        )
    }

    private fun createTestContext(
        endpoints: List<com.itangcent.easyapi.core.export.ApiEndpoint>,
        selectedToken: String? = null
    ): com.itangcent.easyapi.core.export.ExportContext {
        val channelConfig = if (selectedToken != null) {
            YapiConfig(selectedToken = selectedToken)
        } else {
            ChannelConfig.Empty
        }
        return com.itangcent.easyapi.core.export.ExportContext(
            project = exporterProject,
            endpoints = endpoints,
            channelId = "yapi",
            channelConfig = channelConfig
        )
    }

    private fun createExporterProject(
        serverUrl: String? = "http://localhost:3000",
        tokenForModule: String? = null,
        httpClient: HttpClient? = null
    ): com.intellij.openapi.project.Project {
        val helper = mock<YapiSettingsHelper> {
            onBlocking { resolveServerUrl(any()) } doReturn serverUrl
            onBlocking { resolveToken(any(), any()) } doReturn tokenForModule
        }

        return wrap(project) {
            replaceService(YapiSettingsHelper::class, helper)
            if (httpClient != null) {
                val provider = mock<HttpClientProvider> {
                    on { getClient() } doReturn httpClient
                }
                replaceService(HttpClientProvider::class, provider)
            }
        }
    }
}
