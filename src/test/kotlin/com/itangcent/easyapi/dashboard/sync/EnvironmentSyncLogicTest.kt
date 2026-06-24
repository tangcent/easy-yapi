package com.itangcent.easyapi.dashboard.sync

import com.itangcent.easyapi.exporter.postman.UploadResult
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentDetail
import com.itangcent.easyapi.exporter.postman.model.PostmanEnvironmentValue
import com.itangcent.easyapi.script.env.Environment
import com.itangcent.easyapi.script.env.EnvironmentScope
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [EnvironmentSyncLogic].
 *
 * Covers mapping functions (EasyAPI ↔ Postman), conflict resolution
 * (REPLACE/MERGE/SKIP), error message conversion, and token validation.
 *
 * These tests are pure — no IntelliJ Platform or network dependencies required.
 */
class EnvironmentSyncLogicTest {

    // ── environmentToPostman ─────────────────────────────────────────────────

    @Test
    fun `environmentToPostman maps name and variables`() {
        val env = Environment(
            name = "dev",
            scope = EnvironmentScope.PROJECT,
            variables = mapOf("URL" to "http://localhost:8080", "TOKEN" to "abc")
        )

        val detail = EnvironmentSyncLogic.environmentToPostman(env)

        assertEquals("dev", detail.name)
        assertEquals(2, detail.values.size)
        assertEquals("URL", detail.values[0].key)
        assertEquals("http://localhost:8080", detail.values[0].value)
        assertTrue(detail.values[0].enabled)
        assertEquals("text", detail.values[0].type)
    }

    @Test
    fun `environmentToPostman produces empty values for empty environment`() {
        val env = Environment(name = "empty", variables = emptyMap())

        val detail = EnvironmentSyncLogic.environmentToPostman(env)

        assertEquals("empty", detail.name)
        assertTrue(detail.values.isEmpty())
    }

    @Test
    fun `environmentToPostman default id is empty`() {
        val env = Environment(name = "x", variables = mapOf("k" to "v"))

        val detail = EnvironmentSyncLogic.environmentToPostman(env)

        assertEquals("", detail.id)
    }

    // ── variablesToPostmanValues ─────────────────────────────────────────────

    @Test
    fun `variablesToPostmanValues maps each entry`() {
        val vars = mapOf("A" to "1", "B" to "2")

        val values = EnvironmentSyncLogic.variablesToPostmanValues(vars)

        assertEquals(2, values.size)
        assertTrue(values.any { it.key == "A" && it.value == "1" })
        assertTrue(values.any { it.key == "B" && it.value == "2" })
        values.forEach {
            assertTrue(it.enabled)
            assertEquals("text", it.type)
        }
    }

    // ── postmanVariablesToMap ────────────────────────────────────────────────

    @Test
    fun `postmanVariablesToMap includes enabled variables only by default`() {
        val values = listOf(
            PostmanEnvironmentValue("A", "1", enabled = true),
            PostmanEnvironmentValue("B", "2", enabled = false)
        )

        val map = EnvironmentSyncLogic.postmanVariablesToMap(values, includeDisabled = false)

        assertEquals(1, map.size)
        assertEquals("1", map["A"])
        assertNull(map["B"])
    }

    @Test
    fun `postmanVariablesToMap includes disabled when flag set`() {
        val values = listOf(
            PostmanEnvironmentValue("A", "1", enabled = true),
            PostmanEnvironmentValue("B", "2", enabled = false)
        )

        val map = EnvironmentSyncLogic.postmanVariablesToMap(values, includeDisabled = true)

        assertEquals(2, map.size)
        assertEquals("1", map["A"])
        assertEquals("2", map["B"])
    }

    @Test
    fun `postmanVariablesToMap handles empty list`() {
        val map = EnvironmentSyncLogic.postmanVariablesToMap(emptyList(), includeDisabled = true)
        assertTrue(map.isEmpty())
    }

    // ── resolveConflict ──────────────────────────────────────────────────────

    @Test
    fun `resolveConflict creates new environment when no local exists`() {
        val variables = mapOf("URL" to "http://prod")

        val resolved = EnvironmentSyncLogic.resolveConflict(
            localExisting = null,
            postmanVariables = variables,
            postmanEnvName = "prod",
            strategy = ConflictStrategy.REPLACE
        )

        assertNotNull(resolved)
        assertEquals("prod", resolved!!.name)
        assertEquals(EnvironmentScope.PROJECT, resolved.scope)
        assertEquals(variables, resolved.variables)
    }

    @Test
    fun `resolveConflict REPLACE overwrites local variables`() {
        val local = Environment(
            name = "dev",
            scope = EnvironmentScope.GLOBAL,
            variables = mapOf("A" to "old", "B" to "keep")
        )
        val postmanVars = mapOf("A" to "new")

        val resolved = EnvironmentSyncLogic.resolveConflict(
            localExisting = local,
            postmanVariables = postmanVars,
            postmanEnvName = "dev",
            strategy = ConflictStrategy.REPLACE
        )

        assertNotNull(resolved)
        // REPLACE fully overwrites variables
        assertEquals(postmanVars, resolved!!.variables)
        assertEquals("dev", resolved.name)
        // Scope is preserved from local
        assertEquals(EnvironmentScope.GLOBAL, resolved.scope)
    }

    @Test
    fun `resolveConflict MERGE combines variables with Postman precedence`() {
        val local = Environment(
            name = "dev",
            variables = mapOf("A" to "local-a", "B" to "local-b")
        )
        val postmanVars = mapOf("A" to "postman-a", "C" to "postman-c")

        val resolved = EnvironmentSyncLogic.resolveConflict(
            localExisting = local,
            postmanVariables = postmanVars,
            postmanEnvName = "dev",
            strategy = ConflictStrategy.MERGE
        )

        assertNotNull(resolved)
        val vars = resolved!!.variables
        assertEquals(3, vars.size)
        // Postman takes precedence on conflict
        assertEquals("postman-a", vars["A"])
        // Local-only keys are preserved
        assertEquals("local-b", vars["B"])
        // Postman-only keys are added
        assertEquals("postman-c", vars["C"])
    }

    @Test
    fun `resolveConflict SKIP returns null`() {
        val local = Environment(name = "dev", variables = mapOf("A" to "1"))

        val resolved = EnvironmentSyncLogic.resolveConflict(
            localExisting = local,
            postmanVariables = mapOf("A" to "2"),
            postmanEnvName = "dev",
            strategy = ConflictStrategy.SKIP
        )

        assertNull(resolved)
    }

    @Test
    fun `resolveConflict SKIP with no local still creates new environment`() {
        // SKIP only applies when there is a conflict; no local means no conflict
        val resolved = EnvironmentSyncLogic.resolveConflict(
            localExisting = null,
            postmanVariables = mapOf("A" to "1"),
            postmanEnvName = "new",
            strategy = ConflictStrategy.SKIP
        )

        assertNotNull(resolved)
        assertEquals("new", resolved!!.name)
    }

    // ── handleApiError ───────────────────────────────────────────────────────

    @Test
    fun `handleApiError returns friendly message for 401`() {
        val msg = EnvironmentSyncLogic.handleApiError(RuntimeException("HTTP 401: Unauthorized"))
        assertEquals("API token is invalid or expired. Please reconfigure in settings.", msg)
    }

    @Test
    fun `handleApiError returns friendly message for 429`() {
        val msg = EnvironmentSyncLogic.handleApiError(RuntimeException("HTTP 429: Too Many Requests"))
        assertEquals("Postman API rate limit exceeded. Please retry later.", msg)
    }

    @Test
    fun `handleApiError returns original message for other errors`() {
        val msg = EnvironmentSyncLogic.handleApiError(RuntimeException("Connection refused"))
        assertEquals("Connection refused", msg)
    }

    @Test
    fun `handleApiError returns fallback when message is null`() {
        val msg = EnvironmentSyncLogic.handleApiError(RuntimeException())
        assertEquals("Unknown error", msg)
    }

    // ── uploadResultErrorMessage ─────────────────────────────────────────────

    @Test
    fun `uploadResultErrorMessage returns friendly message for 401`() {
        val result = UploadResult(success = false, message = "HTTP 401: Unauthorized")
        val msg = EnvironmentSyncLogic.uploadResultErrorMessage(result)
        assertEquals("API token is invalid or expired. Please reconfigure in settings.", msg)
    }

    @Test
    fun `uploadResultErrorMessage returns friendly message for 429`() {
        val result = UploadResult(success = false, message = "HTTP 429: rate limit")
        val msg = EnvironmentSyncLogic.uploadResultErrorMessage(result)
        assertEquals("Postman API rate limit exceeded. Please retry later.", msg)
    }

    @Test
    fun `uploadResultErrorMessage returns original message for other errors`() {
        val result = UploadResult(success = false, message = "HTTP 500: Server Error")
        val msg = EnvironmentSyncLogic.uploadResultErrorMessage(result)
        assertEquals("HTTP 500: Server Error", msg)
    }

    @Test
    fun `uploadResultErrorMessage returns fallback when message is null`() {
        val result = UploadResult(success = false, message = null)
        val msg = EnvironmentSyncLogic.uploadResultErrorMessage(result)
        assertEquals("Unknown error", msg)
    }

    // ── isTokenConfigured ────────────────────────────────────────────────────

    @Test
    fun `isTokenConfigured returns false for null`() {
        assertFalse(EnvironmentSyncLogic.isTokenConfigured(null))
    }

    @Test
    fun `isTokenConfigured returns false for blank string`() {
        assertFalse(EnvironmentSyncLogic.isTokenConfigured(""))
        assertFalse(EnvironmentSyncLogic.isTokenConfigured("   "))
    }

    @Test
    fun `isTokenConfigured returns true for non-blank string`() {
        assertTrue(EnvironmentSyncLogic.isTokenConfigured("abc-123"))
    }
}
