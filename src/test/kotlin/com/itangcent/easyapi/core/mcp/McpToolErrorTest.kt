package com.itangcent.easyapi.core.mcp

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.itangcent.easyapi.channel.spi.ChannelOption
import com.itangcent.easyapi.core.export.EndpointScanResult
import com.itangcent.easyapi.core.export.ExportItemSummary
import com.itangcent.easyapi.core.export.ExportOptions
import com.itangcent.easyapi.core.export.ExportRejection
import com.itangcent.easyapi.core.export.ProjectSummary
import com.itangcent.easyapi.core.export.SkippedTarget
import com.itangcent.easyapi.core.export.TargetFailure
import com.itangcent.easyapi.core.export.TargetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for [McpToolError]: the `{"error":{…}}` text an MCP tool returns instead of throwing, and how
 * the expected scan failures and the export rejections map onto it.
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

    @Test
    fun testUnsavedChangesMapToUnsavedInIdeWithTheFiles() {
        val failure = EndpointScanResult.UnsavedInIde(
            listOf("src/main/java/com/x/UserCtrl.java", "C:/work/lib/Dto.java")
        )

        val error = errorOf(McpToolError.of(failure, project).toJson())

        assertEquals("code", "UNSAVED_IN_IDE", error.get("code").asString)
        assertEquals("message", failure.message, error.get("message").asString)
        assertEquals("project", "demo", error.getAsJsonObject("project").get("name").asString)
        assertEquals("files, as given", failure.files, error.getAsJsonArray("files").map { it.asString })
    }

    @Test
    fun testSyncTimeoutMapsToSyncTimeout() {
        val error = errorOf(McpToolError.of(EndpointScanResult.SyncTimeout, project).toJson())

        assertEquals("code", "SYNC_TIMEOUT", error.get("code").asString)
        assertEquals("message", EndpointScanResult.SyncTimeout.message, error.get("message").asString)
        assertEquals("project", "demo", error.getAsJsonObject("project").get("name").asString)
        assertFalse("files only for unsaved changes: $error", error.has("files"))
    }

    // ---- export rejections ----

    @Test
    fun testChannelRejectionsMapToTheirCodes() {
        val notFound = ExportRejection.ChannelNotFound("nope", listOf("yapi"))
        val notSupported = ExportRejection.ChannelNotSupported("markdown", listOf("yapi"))
        val disabled = ExportRejection.ChannelDisabled("yapi")

        for ((rejection, code) in listOf(
            notFound to "CHANNEL_NOT_FOUND", notSupported to "CHANNEL_NOT_SUPPORTED", disabled to "CHANNEL_DISABLED"
        )) {
            val error = errorOf(McpToolError.of(rejection, project).toJson())
            assertEquals("code", code, error.get("code").asString)
            assertEquals("message", rejection.message, error.get("message").asString)
            assertEquals("project", "demo", error.getAsJsonObject("project").get("name").asString)
        }
        assertEquals(
            "a missing channel lists the channels that can export headless",
            listOf("yapi"),
            errorOf(McpToolError.of(notFound, project).toJson()).getAsJsonArray("channels").map { it.asString }
        )
        assertEquals(
            "the agent-facing text",
            "Channel 'markdown' cannot export without user interaction. Channels that can: yapi.",
            notSupported.message
        )
        assertEquals(
            "so does a channel that cannot",
            listOf("yapi"),
            errorOf(McpToolError.of(notSupported, project).toJson()).getAsJsonArray("channels").map { it.asString }
        )
        assertFalse("a disabled channel needs no list", errorOf(McpToolError.of(disabled, project).toJson()).has("channels"))
        assertEquals(
            "the agent-facing text names the settings page the channel switches are on",
            "Channel 'yapi' is disabled. Ask the user to enable it under Settings → EasyYapi → Features → " +
                "Export Channels, then retry.",
            disabled.message
        )
    }

    /** Option values may be secrets: the error names the options and lists what is accepted, never a value. */
    @Test
    fun testInvalidOptionsListWhatIsAcceptedButNeverAValue() {
        val accepted = listOf(ChannelOption("token", "A YApi project token."))
        val rejection = ExportRejection.OptionsInvalid(
            "yapi",
            ExportOptions.problems(mapOf("tokn" to "secret-123", "token" to "  "), accepted),
            accepted
        )

        val json = McpToolError.of(rejection, project).toJson()

        assertFalse("no option value anywhere: $json", json.contains("secret-123"))
        val error = errorOf(json)
        assertEquals("code", "OPTIONS_INVALID", error.get("code").asString)
        assertEquals("message", rejection.message, error.get("message").asString)
        assertEquals("project", "demo", error.getAsJsonObject("project").get("name").asString)
        val option = error.getAsJsonArray("acceptedOptions").single().asJsonObject
        assertEquals("an accepted option is a name and a description", setOf("name", "description"), option.keySet())
        assertEquals("accepted name", "token", option.get("name").asString)
        assertEquals("accepted description", "A YApi project token.", option.get("description").asString)
        assertEquals(
            "the agent-facing text, one problem per option",
            "Invalid options for channel 'yapi': unknown option 'tokn'; option 'token' has an empty value. " +
                "Accepted: token.",
            rejection.message
        )
    }

    @Test
    fun testExportFailureCarriesTheItemsWhenTheChannelReportedAny() {
        val item = ExportItemSummary(
            status = "failed", name = "get user", httpMethod = "GET", path = "/user/{id}", folder = "user",
            destination = "key-b", id = null, url = null, folderUrl = null,
            reason = "No YApi token for 'key-b'.", className = "com.x.UserCtrl", methodName = "get"
        )
        val failed = ExportRejection.ExportFailed("All exports failed", listOf(item))

        val error = errorOf(McpToolError.of(failed, project).toJson())

        assertEquals("code", "EXPORT_FAILED", error.get("code").asString)
        assertEquals("message", failed.message, error.get("message").asString)
        assertEquals("project", "demo", error.getAsJsonObject("project").get("name").asString)
        val items = error.getAsJsonArray("items").map { it.asJsonObject }
        assertEquals("the reported item", listOf("failed" to "key-b"), items.map { it.get("status").asString to it.get("destination").asString })
        assertFalse(
            "items only when the channel reported any",
            errorOf(McpToolError.of(ExportRejection.ExportFailed("not configured", emptyList()), project).toJson()).has("items")
        )
    }

    private fun errorOf(json: String): JsonObject = JsonParser.parseString(json).asJsonObject.getAsJsonObject("error")
}
