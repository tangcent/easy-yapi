package com.itangcent.easyapi.core.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itangcent.easyapi.core.export.EndpointScanResult
import com.itangcent.easyapi.core.export.ProjectSummary
import com.itangcent.easyapi.core.export.SkippedTarget
import com.itangcent.easyapi.core.export.TargetFailure
import com.itangcent.easyapi.core.export.TargetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for [McpToolError]: the `{"error":{…}}` text an MCP tool returns instead of throwing, and how
 * the expected scan failures map onto it.
 */
class McpToolErrorTest {

    private val project = ProjectSummary("demo", "/work/demo")

    @Test
    fun testErrorIsWrappedInAnErrorObject() {
        val json = JsonParser.parseString(McpToolError(McpToolError.INTERNAL_ERROR, "boom").toJson()).asJsonObject

        assertEquals("one top-level key", setOf("error"), json.keySet())
        val error = json.getAsJsonObject("error")
        assertEquals("code", "INTERNAL_ERROR", error.get("code").asString)
        assertEquals("message", "boom", error.get("message").asString)
        assertFalse(
            "absent fields are omitted: $error",
            error.has("project") || error.has("failures") || error.has("skipped") || error.has("openProjects")
        )
    }

    /** The JSON is read as text by the caller: the quotes around a name stay quotes, not Unicode escapes. */
    @Test
    fun testQuotesInTheMessageStayQuotes() {
        val json = McpToolError(McpToolError.TARGETS_UNRESOLVED, "- class 'NoSuchCtrl': not found").toJson()

        assertEquals(
            "as written",
            """{"error":{"code":"TARGETS_UNRESOLVED","message":"- class 'NoSuchCtrl': not found"}}""",
            json
        )
    }

    @Test
    fun testUnresolvedTargetsListEveryFailure() {
        val failure = EndpointScanResult.TargetsUnresolved(
            listOf(
                TargetFailure.Ambiguous(
                    TargetKind.CLASS, "Dup", listOf("com.a.Dup", "com.b.Dup"), total = 2,
                    hint = "Use the fully qualified name."
                ),
                TargetFailure.NotFound(TargetKind.METHOD, "com.x.Foo#bar", "no method 'bar' is declared in com.x.Foo")
            ),
            targetCount = 3
        )

        val error = errorOf(McpToolError.of(failure, project).toJson())

        assertEquals("code", "TARGETS_UNRESOLVED", error.get("code").asString)
        assertEquals("message", failure.message, error.get("message").asString)
        assertEquals("project", "demo", error.getAsJsonObject("project").get("name").asString)
        val failures = error.getAsJsonArray("failures").map { it.asJsonObject }
        assertEquals("kinds", listOf("class", "method"), failures.map { it.get("kind").asString })
        assertEquals("inputs", listOf("Dup", "com.x.Foo#bar"), failures.map { it.get("input").asString })
        assertEquals(
            "candidates of the ambiguous name",
            listOf("com.a.Dup", "com.b.Dup"),
            failures[0].getAsJsonArray("candidates").map { it.asString }
        )
        assertFalse("candidates only for an ambiguous name", failures[1].has("candidates"))
    }

    @Test
    fun testReadinessFailuresMapToTheirCodes() {
        assertEquals(
            "changed targets",
            "TARGETS_CHANGED",
            errorOf(McpToolError.of(EndpointScanResult.TargetsChanged, project).toJson()).get("code").asString
        )
        assertEquals(
            "index",
            "INDEX_NOT_READY",
            errorOf(McpToolError.of(EndpointScanResult.IndexNotReady, project).toJson()).get("code").asString
        )
    }

    @Test
    fun testMissingTargetsMapToTargetsRequired() {
        val error = errorOf(McpToolError.of(EndpointScanResult.TargetsRequired, project).toJson())

        assertEquals("code", "TARGETS_REQUIRED", error.get("code").asString)
        assertEquals("message", EndpointScanResult.TargetsRequired.message, error.get("message").asString)
        assertEquals("project", "demo", error.getAsJsonObject("project").get("name").asString)
    }

    @Test
    fun testNoEndpointsListsEverySkippedTarget() {
        val failure = EndpointScanResult.NoEndpoints(
            listOf(SkippedTarget.of(TargetKind.CLASS, "UserDto"), SkippedTarget.of(TargetKind.FILE, "web/Dto.java"))
        )

        val error = errorOf(McpToolError.of(failure, project).toJson())

        assertEquals("code", "NO_ENDPOINTS", error.get("code").asString)
        assertEquals("message", failure.message, error.get("message").asString)
        assertEquals("project", "demo", error.getAsJsonObject("project").get("name").asString)
        val skipped = error.getAsJsonArray("skipped").map { it.asJsonObject }
        assertEquals("kinds", listOf("class", "file"), skipped.map { it.get("kind").asString })
        assertEquals("inputs", listOf("UserDto", "web/Dto.java"), skipped.map { it.get("input").asString })
        assertEquals("reasons", failure.skipped.map { it.reason }, skipped.map { it.get("reason").asString })
        assertFalse("no failures: every target resolved", error.has("failures"))
    }

    private fun errorOf(json: String): JsonObject = JsonParser.parseString(json).asJsonObject.getAsJsonObject("error")
}
