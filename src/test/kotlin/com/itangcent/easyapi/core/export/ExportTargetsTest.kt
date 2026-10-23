package com.itangcent.easyapi.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure target types in `ExportTargets.kt`, for [SkippedTarget], and for the failure
 * texts of [EndpointScanResult] and the JSON of a [ScanReport] — what an MCP caller reads when a target
 * is skipped or a call fails.
 */
class ExportTargetsTest {

    @Test
    fun testEmptyOnlyWhenAllListsAreEmpty() {
        assertTrue("no list given means no target", ExportTargets().isEmpty())
        assertFalse("a class is a target", ExportTargets(classes = listOf("UserCtrl")).isEmpty())
        assertFalse("a method is a target", ExportTargets(methods = listOf("UserCtrl#get")).isEmpty())
        assertFalse("a file is a target", ExportTargets(files = listOf("UserCtrl.java")).isEmpty())
    }

    @Test
    fun testBlankEntryIsNotEmpty() {
        assertFalse(
            "a list holding only a blank entry is a malformed target, not a missing one",
            ExportTargets(classes = listOf("  ")).isEmpty()
        )
    }

    @Test
    fun testWireNames() {
        assertEquals(
            "wire names are part of the tool output",
            listOf("class", "method", "file"),
            TargetKind.entries.map { it.wireName }
        )
    }

    @Test
    fun testCandidateCap() {
        assertEquals("same cap as the built-in AI tools", 8, TargetFailure.MAX_CANDIDATES)
    }

    @Test
    fun testAmbiguousReasonListsCandidatesAndCountsTheRest() {
        val candidates = (1..8).map { "com.example.m$it.Many" }
        val failure = TargetFailure.Ambiguous(
            TargetKind.CLASS, "Many", candidates, total = 9, hint = "Use the fully qualified name."
        )

        val reason = failure.reason
        assertTrue("states the total: $reason", reason.startsWith("9 classes match: "))
        candidates.forEach { assertTrue("lists $it: $reason", reason.contains(it)) }
        assertTrue("says how many are not listed: $reason", reason.contains("and 1 more"))
        assertTrue("ends with the hint: $reason", reason.endsWith("Use the fully qualified name."))
    }

    @Test
    fun testAmbiguousReasonWithEveryCandidateListed() {
        val failure = TargetFailure.Ambiguous(
            TargetKind.CLASS, "Dup", listOf("com.a.Dup", "com.b.Dup"), total = 2, hint = "Use the fully qualified name."
        )
        assertEquals(
            "no 'more' suffix when nothing is hidden",
            "2 classes match: com.a.Dup, com.b.Dup. Use the fully qualified name.",
            failure.reason
        )
    }

    @Test
    fun testDescribeNamesKindAndInput() {
        val failure = TargetFailure.NotFound(TargetKind.METHOD, "com.x.Foo#bar", "no method 'bar' is declared in com.x.Foo")
        assertEquals(
            "one line per failure: kind, quoted input, reason",
            "- method 'com.x.Foo#bar': no method 'bar' is declared in com.x.Foo",
            failure.describe()
        )
    }

    @Test
    fun testNoClassIsAFileFailure() {
        val failure = TargetFailure.NoClass("docs/README.md")
        assertEquals("NoClass only applies to files", TargetKind.FILE, failure.kind)
        assertEquals(
            "fixed reason",
            "- file 'docs/README.md': the file declares no class",
            failure.describe()
        )
    }

    @Test
    fun testTargetsUnresolvedMessageCountsAndListsEveryFailure() {
        val result = EndpointScanResult.TargetsUnresolved(
            listOf(
                TargetFailure.Ambiguous(
                    TargetKind.CLASS, "UserController",
                    listOf("com.a.UserController", "com.b.UserController"), total = 2,
                    hint = "Use the fully qualified name."
                ),
                TargetFailure.NotFound(TargetKind.METHOD, "com.x.Foo#bar", "no method 'bar' is declared in com.x.Foo")
            ),
            targetCount = 3
        )

        val message = result.message
        assertTrue(
            "says how many of how many failed: $message",
            message.startsWith("2 of 3 target(s) could not be resolved, so nothing was scanned:\n")
        )
        assertTrue(
            "lists the class failure: $message",
            message.contains("\n- class 'UserController': 2 classes match: com.a.UserController, com.b.UserController.")
        )
        assertTrue(
            "lists the method failure: $message",
            message.contains("\n- method 'com.x.Foo#bar': no method 'bar' is declared in com.x.Foo")
        )
    }

    @Test
    fun testReadinessMessagesTellTheCallerToRetry() {
        assertTrue(
            "index message",
            EndpointScanResult.IndexNotReady.message.contains("retry when indexing finishes")
        )
        assertTrue(
            "changed-targets message",
            EndpointScanResult.TargetsChanged.message.contains("retry the call")
        )
        val timeout = EndpointScanResult.SyncTimeout.message
        val retry = timeout.indexOf("Retry the call")
        val closeDialog = timeout.indexOf("ask the user to close any modal dialog")
        assertTrue("sync-timeout message retries: $timeout", retry >= 0)
        assertTrue("then has the user close a dialog: $timeout", closeDialog > retry)
    }

    @Test
    fun testTargetsRequiredMessageNamesTheTargetParameters() {
        assertEquals(
            "says what to pass, and that the whole project is never the fallback",
            "No target was given: pass at least one entry in classes, methods or files. " +
                "The whole project is never scanned.",
            EndpointScanResult.TargetsRequired.message
        )
    }

    // ---- targets without endpoints ----

    @Test
    fun testSkippedTargetReasonsCarryTheHint() {
        for (kind in TargetKind.entries) {
            val reason = SkippedTarget.of(kind, "X").reason
            assertTrue("$kind starts with the cause: $reason", reason.startsWith("no endpoint "))
            assertTrue(
                "$kind ends with the hint: $reason",
                reason.endsWith(". If you changed a model or a service, pass the controllers or methods that use it instead.")
            )
        }
    }

    @Test
    fun testSkippedTargetReasonsSayWhatWasScanned() {
        assertTrue("class", SkippedTarget.of(TargetKind.CLASS, "UserDto").reason.startsWith("no endpoint in this class: "))
        assertTrue("method", SkippedTarget.of(TargetKind.METHOD, "A#b").reason.startsWith("no endpoint for this method: "))
        assertTrue(
            "file",
            SkippedTarget.of(TargetKind.FILE, "A.java").reason.startsWith("no endpoint in the classes this file declares: ")
        )
    }

    /** The scanner only logs an exporter error or timeout, so a failed class looks like one without endpoints. */
    @Test
    fun testSkippedTargetReasonsAllowForAFailedExport() {
        for (kind in TargetKind.entries) {
            val reason = SkippedTarget.of(kind, "X").reason
            assertTrue(
                "$kind names a failed export and where its error is, right before the hint: $reason",
                reason.contains(", or exporting it failed (see idea.log). If you changed ")
            )
        }
    }

    @Test
    fun testSkippedTargetKeepsKindAndInput() {
        val skipped = SkippedTarget.of(TargetKind.METHOD, "com.x.UserService#find")

        assertEquals("kind on the wire", "method", skipped.kind)
        assertEquals("input as passed", "com.x.UserService#find", skipped.input)
        assertEquals(
            "one line per skipped target: kind, quoted input, reason",
            "- method 'com.x.UserService#find': ${skipped.reason}",
            skipped.describe()
        )
    }

    /** The JSON is read as text by the caller: a quote stays a quote, not a Unicode escape. */
    @Test
    fun testScanResultJsonKeepsQuotes() {
        val listing = ScanReport(
            targets = emptyList(),
            skipped = listOf(SkippedTarget("class", "UserDto", "it's a model")),
            count = 0,
            endpoints = emptyList()
        )

        assertTrue("as written: ${listing.toJson()}", listing.toJson().contains("\"reason\":\"it's a model\""))
    }

    @Test
    fun testNoEndpointsMessageListsEverySkippedTarget() {
        val dto = SkippedTarget.of(TargetKind.CLASS, "UserDto")
        val service = SkippedTarget.of(TargetKind.METHOD, "UserService#find")

        val message = EndpointScanResult.NoEndpoints(listOf(dto, service)).message

        assertEquals(
            "a header line, then one line per target, in order",
            "No target has an endpoint, so nothing was listed:\n${dto.describe()}\n${service.describe()}",
            message
        )
    }
}
