package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.channel.yapi.model.MutableYapiApiDoc
import com.itangcent.easyapi.channel.yapi.model.YapiApiDoc
import com.itangcent.easyapi.channel.yapi.model.YapiResponse
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportItemResult
import com.itangcent.easyapi.core.export.ExportItemStatus
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.logging.LogLevel
import com.itangcent.easyapi.core.logging.LoggerCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Unit tests for [YapiExportItems] and [ProjectApiLookup], against a mock [YapiApiClient]: what is reported for
 * one endpoint. The export as a whole is covered by [YapiExporterTest].
 */
class YapiExportItemsTest {

    private val console = LoggerCollector()
    private val reported = mutableListOf<ExportItemResult>()
    private val endpoint = ApiEndpoint(name = "Scanned", metadata = httpMetadata(path = "/api/scanned", method = HttpMethod.GET))
    private val doc = MutableYapiApiDoc(title = "Formatted", path = "/api/formatted", method = "post")

    @Test
    fun `a failure before the upload leaves the fields to the endpoint`() {
        val item = items().item(endpoint)
        item.destination = "key-a"

        item.failed("No YApi token for 'key-a'")

        val result = reported.single()
        assertSame(endpoint, result.endpoint)
        assertEquals(ExportItemStatus.FAILED, result.status)
        assertNull("the endpoint's own name", result.name)
        assertNull(result.path)
        assertNull(result.httpMethod)
        assertNull(result.folder)
        assertEquals("key-a", result.destination)
        assertEquals("No YApi token for 'key-a'", result.reason)
    }

    @Test
    fun `a failure once the upload is prepared reports the endpoint as it was to be uploaded`() = runBlocking {
        val item = items().item(endpoint)
        item.destination = "key-a"
        item.beforeUpload(client(), doc, "folder-a", "34", answering(true))

        item.failed("boom")

        val result = reported.single()
        assertSame(endpoint, result.endpoint)
        assertEquals(ExportItemStatus.FAILED, result.status)
        assertEquals("Formatted", result.name)
        assertEquals("/api/formatted", result.path)
        assertEquals("upper case, as uploaded", "POST", result.httpMethod)
        assertEquals("folder-a", result.folder)
        assertEquals("key-a", result.destination)
        assertEquals("boom", result.reason)
    }

    @Test
    fun `an API the export mode keeps names the mode, on the item and on the console`() = runBlocking {
        val kept = mapOf(
            YapiExportMode.UPDATE_IF_CHANGED to "no change found; export mode UPDATE_IF_CHANGED keeps the existing API",
            YapiExportMode.ALWAYS_ASK to "the existing API was kept (export mode ALWAYS_ASK)"
        )
        for ((mode, reason) in kept) {
            val item = items(mode).item(endpoint)
            item.beforeUpload(client(existingId = "101"), doc, "folder-a", "34", answering(false)).confirm(doc, "34")

            item.uploaded(YapiResponse.success(ApiUploadResult()))

            val result = reported.removeAt(0)
            assertEquals("$mode", ExportItemStatus.NOT_WRITTEN, result.status)
            assertEquals("$mode: the API that was kept", "101", result.id)
            assertEquals("$mode", reason, result.reason)
        }
        val infos = console.getMessages().filter { it.level == LogLevel.INFO }.map { it.message }
        assertEquals(kept.values.map { "Scanned: not written: $it" }, infos)
    }

    @Test
    fun `a project that cannot be listed is warned about once`() = runBlocking {
        val client = client(projectApis = YapiResponse.failure("HTTP 502"))
        val lookup = ProjectApiLookup(console)

        val first = lookup.find(client, "/api/a", "GET")
        val second = lookup.find(client, "/api/b", "GET")

        assertNull(first)
        assertNull(second)
        verify(client, times(1)).listProjectApis()
        val warnings = console.getMessages().filter { it.level == LogLevel.WARN }
        assertEquals("one warning for the project: $warnings", 1, warnings.size)
        assertTrue("gives the reason: $warnings", warnings.single().message.contains("HTTP 502"))
        assertTrue("says what the export falls back to: $warnings", warnings.single().message.contains("target folder only"))
    }

    @Test
    fun `the project lookup matches the path and the method in any case`() = runBlocking {
        val listed = ProjectApiInfo("101", "/api/a", "GET", "7", "users")
        val client = client(projectApis = YapiResponse.success(listOf(listed)))
        val lookup = ProjectApiLookup(console)

        assertSame(listed, lookup.find(client, "/api/a", "get"))
        assertNull("another path", lookup.find(client, "/api/A", "GET"))
    }

    private fun items(mode: YapiExportMode = YapiExportMode.ALWAYS_UPDATE) =
        YapiExportItems({ reported += it }, console, SERVER, mode)

    /** A client of project 11 whose target folder holds [existingId], if any, and whose project lists [projectApis]. */
    private fun client(
        existingId: String? = null,
        projectApis: YapiResponse<List<ProjectApiInfo>> = YapiResponse.success(emptyList())
    ): YapiApiClient = mock {
        onBlocking { getProjectId() } doReturn YapiResponse.success("11")
        onBlocking { findExistingApi(any(), any(), any()) } doReturn existingId
        onBlocking { listProjectApis() } doReturn projectApis
    }

    /** A confirmation that always answers [answer]. */
    private fun answering(answer: Boolean) = object : UpdateConfirmation {
        override suspend fun confirm(doc: YapiApiDoc, catId: String): Boolean = answer
    }

    private companion object {
        const val SERVER = "http://localhost:3000"
    }
}
