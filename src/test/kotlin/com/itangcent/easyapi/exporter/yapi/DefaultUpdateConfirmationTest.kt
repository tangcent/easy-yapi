package com.itangcent.easyapi.exporter.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.itangcent.easyapi.exporter.yapi.model.YapiApiDoc
import com.itangcent.easyapi.exporter.yapi.model.YapiFormParam
import com.itangcent.easyapi.exporter.yapi.model.YapiHeader
import com.itangcent.easyapi.exporter.yapi.model.YapiPathParam
import com.itangcent.easyapi.exporter.yapi.model.YapiQuery
import com.itangcent.easyapi.settings.YapiExportMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for [DefaultUpdateConfirmation].
 *
 * Tests all export modes:
 * - [YapiExportMode.ALWAYS_UPDATE]: Always returns true
 * - [YapiExportMode.NEVER_UPDATE]: Returns true only if no existing API
 * - [YapiExportMode.UPDATE_IF_CHANGED]: Compares important fields
 */
class DefaultUpdateConfirmationTest {

    private val apiClient: YapiApiClient = mock()

    @Test
    fun `ALWAYS_UPDATE returns true without checking existing API`() = runBlocking {
        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.ALWAYS_UPDATE,
            apiClient = apiClient
        )

        val doc = testDoc()
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `NEVER_UPDATE returns true when no existing API found`() = runBlocking {
        whenever(apiClient.findExistingApiInfo("cat1", "/api/users", "GET"))
            .thenReturn(null)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.NEVER_UPDATE,
            apiClient = apiClient
        )

        val doc = testDoc()
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `NEVER_UPDATE returns false when existing API found`() = runBlocking {
        whenever(apiClient.findExistingApiInfo("cat1", "/api/users", "GET"))
            .thenReturn(ExistingApiInfo("existing-id", "Existing API"))

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.NEVER_UPDATE,
            apiClient = apiClient
        )

        val doc = testDoc()
        val result = confirmation.confirm(doc, "cat1")

        assertFalse(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when no existing API`() = runBlocking {
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(null)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc()
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns false when no important changes`() = runBlocking {
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            addProperty("desc", "Returns user list")
            addProperty("req_body_other", """{"type":"object"}""")
            addProperty("res_body", """{"type":"array"}""")
            addProperty("req_body_type", "json")
            addProperty("res_body_type", "json")
            addProperty("req_body_is_json_schema", true)
            addProperty("res_body_is_json_schema", true)
            add("req_headers", JsonArray())
            add("req_query", JsonArray())
            add("req_params", JsonArray())
            add("req_body_form", JsonArray())
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            title = "Get Users",
            desc = "Returns user list",
            reqBodyOther = """{"type":"object"}""",
            resBody = """{"type":"array"}""",
            reqBodyType = "json",
            resBodyType = "json",
            reqBodyIsJsonSchema = true,
            resBodyIsJsonSchema = true
        )
        val result = confirmation.confirm(doc, "cat1")

        assertFalse(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when title changed`() = runBlocking {
        val existingData = JsonObject().apply {
            addProperty("title", "Old Title")
            addProperty("desc", "Description")
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(title = "New Title", desc = "Description")
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when description changed`() = runBlocking {
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            addProperty("desc", "Old description")
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(title = "Get Users", desc = "New description")
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when request body changed`() = runBlocking {
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            addProperty("req_body_other", """{"type":"object","properties":{"old":{}}}""")
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            title = "Get Users",
            reqBodyOther = """{"type":"object","properties":{"new":{}}}"""
        )
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when response body changed`() = runBlocking {
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            addProperty("res_body", """{"type":"object","properties":{"old":{}}}""")
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            title = "Get Users",
            resBody = """{"type":"object","properties":{"new":{}}}"""
        )
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when headers changed`() = runBlocking {
        val existingHeaders = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", "Authorization")
                addProperty("desc", "Auth header")
                addProperty("required", 1)
            })
        }
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            add("req_headers", existingHeaders)
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            title = "Get Users",
            reqHeaders = listOf(
                YapiHeader(name = "Authorization", desc = "Auth header updated", required = 1)
            )
        )
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when query params count changed`() = runBlocking {
        val existingQuery = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", "page")
                addProperty("required", 0)
            })
        }
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            add("req_query", existingQuery)
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            title = "Get Users",
            reqQuery = listOf(
                YapiQuery(name = "page", required = 0),
                YapiQuery(name = "size", required = 0)
            )
        )
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when query param name changed`() = runBlocking {
        val existingQuery = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", "page")
                addProperty("required", 0)
            })
        }
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            add("req_query", existingQuery)
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            title = "Get Users",
            reqQuery = listOf(
                YapiQuery(name = "pageNumber", required = 0)
            )
        )
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when path params changed`() = runBlocking {
        val existingParams = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", "id")
                addProperty("desc", "User ID")
            })
        }
        val existingData = JsonObject().apply {
            addProperty("title", "Get User")
            add("req_params", existingParams)
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users/{id}", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            path = "/api/users/{id}",
            title = "Get User",
            reqParams = listOf(
                YapiPathParam(name = "id", desc = "User ID updated")
            )
        )
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when form params changed`() = runBlocking {
        val existingForm = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", "file")
                addProperty("type", "file")
                addProperty("required", 1)
            })
        }
        val existingData = JsonObject().apply {
            addProperty("title", "Upload File")
            add("req_body_form", existingForm)
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/upload", "POST"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            path = "/api/upload",
            method = "POST",
            title = "Upload File",
            reqBodyForm = listOf(
                YapiFormParam(name = "file", type = "file", required = 0)
            )
        )
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns true when json schema flag changed`() = runBlocking {
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            addProperty("req_body_is_json_schema", false)
            addProperty("res_body_is_json_schema", false)
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            title = "Get Users",
            reqBodyIsJsonSchema = true,
            resBodyIsJsonSchema = true
        )
        val result = confirmation.confirm(doc, "cat1")

        assertTrue(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED handles null vs empty string correctly`() = runBlocking {
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            addProperty("desc", "")
            addProperty("res_body_type", "json")
            addProperty("res_body_is_json_schema", true)
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(title = "Get Users", desc = null)
        val result = confirmation.confirm(doc, "cat1")

        assertFalse(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED handles whitespace in strings`() = runBlocking {
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            addProperty("desc", "  Description with spaces  ")
            addProperty("res_body_type", "json")
            addProperty("res_body_is_json_schema", true)
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(title = "Get Users", desc = "Description with spaces")
        val result = confirmation.confirm(doc, "cat1")

        assertFalse(result)
    }

    @Test
    fun `UPDATE_IF_CHANGED returns false when params match exactly`() = runBlocking {
        val existingQuery = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("name", "page")
                addProperty("desc", "Page number")
                addProperty("example", "1")
                addProperty("required", 0)
            })
        }
        val existingData = JsonObject().apply {
            addProperty("title", "Get Users")
            addProperty("res_body_type", "json")
            addProperty("res_body_is_json_schema", true)
            add("req_query", existingQuery)
        }
        whenever(apiClient.findExistingApiData("cat1", "/api/users", "GET"))
            .thenReturn(existingData)

        val confirmation = DefaultUpdateConfirmation(
            project = null,
            exportMode = YapiExportMode.UPDATE_IF_CHANGED,
            apiClient = apiClient
        )

        val doc = testDoc(
            title = "Get Users",
            reqQuery = listOf(
                YapiQuery(name = "page", desc = "Page number", example = "1", required = 0)
            )
        )
        val result = confirmation.confirm(doc, "cat1")

        assertFalse(result)
    }

    private fun testDoc(
        path: String = "/api/users",
        method: String = "GET",
        title: String = "Get Users",
        desc: String? = null,
        reqBodyOther: String? = null,
        resBody: String? = null,
        reqBodyType: String? = null,
        resBodyType: String? = "json",
        reqBodyIsJsonSchema: Boolean = false,
        resBodyIsJsonSchema: Boolean = true,
        reqHeaders: List<YapiHeader>? = null,
        reqQuery: List<YapiQuery>? = null,
        reqParams: List<YapiPathParam>? = null,
        reqBodyForm: List<YapiFormParam>? = null
    ): YapiApiDoc {
        return YapiApiDoc(
            title = title,
            path = path,
            method = method,
            desc = desc,
            reqBodyOther = reqBodyOther,
            resBody = resBody,
            reqBodyType = reqBodyType,
            resBodyType = resBodyType,
            reqBodyIsJsonSchema = reqBodyIsJsonSchema,
            resBodyIsJsonSchema = resBodyIsJsonSchema,
            reqHeaders = reqHeaders,
            reqQuery = reqQuery,
            reqParams = reqParams,
            reqBodyForm = reqBodyForm
        )
    }
}
