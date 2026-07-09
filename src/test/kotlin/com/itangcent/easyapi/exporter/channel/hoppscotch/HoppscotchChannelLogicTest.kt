package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.channel.hoppscotch.HoppscotchExportMetadata
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppCollection
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.HoppRESTRequest
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import java.io.File

/**
 * Tests for HoppscotchChannel business logic:
 * - Channel properties
 * - countRequests (via reflection)
 * - formatGraphQLError (via reflection)
 * - resolveTargetFile (via reflection)
 * - handleResult with file export
 * - export without token
 * - HoppscotchExportMetadata.formatDisplay
 */
class HoppscotchChannelLogicTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var channel: HoppscotchChannel

    override fun setUp() {
        super.setUp()
        channel = HoppscotchChannel()
    }

    // ── Channel property tests ───────────────────────────────────────────────

    fun testChannelId() {
        assertEquals("hoppscotch", channel.id)
    }

    fun testChannelDisplayName() {
        assertEquals("Hoppscotch (Beta)", channel.displayName)
    }

    fun testChannelDoesNotSupportGrpc() {
        assertFalse(channel.supportsGrpc)
    }

    fun testChannelExposesAsAction() {
        assertTrue(channel.exposeAsAction)
    }

    fun testChannelActionText() {
        assertEquals("Export to Hoppscotch (Beta)", channel.actionText)
    }

    // ── countRequests tests ──────────────────────────────────────────────────

    fun testCountRequestsEmptyCollection() {
        val collection = HoppCollection(name = "Empty")
        assertEquals(0, countRequests(collection))
    }

    fun testCountRequestsFlatRequests() {
        val collection = HoppCollection(
            name = "Root",
            requests = listOf(
                HoppRESTRequest(name = "R1", method = "GET", endpoint = "/r1"),
                HoppRESTRequest(name = "R2", method = "POST", endpoint = "/r2")
            )
        )
        assertEquals(2, countRequests(collection))
    }

    fun testCountRequestsNestedFolders() {
        val collection = HoppCollection(
            name = "Root",
            requests = listOf(
                HoppRESTRequest(name = "R1", method = "GET", endpoint = "/r1")
            ),
            folders = listOf(
                HoppCollection(
                    name = "Sub",
                    requests = listOf(
                        HoppRESTRequest(name = "R2", method = "POST", endpoint = "/r2")
                    )
                )
            )
        )
        assertEquals(2, countRequests(collection))
    }

    fun testCountRequestsDeeplyNested() {
        val collection = HoppCollection(
            name = "Root",
            folders = listOf(
                HoppCollection(
                    name = "Level1",
                    folders = listOf(
                        HoppCollection(
                            name = "Level2",
                            requests = listOf(
                                HoppRESTRequest(name = "Deep", method = "GET", endpoint = "/deep")
                            )
                        )
                    )
                )
            )
        )
        assertEquals(1, countRequests(collection))
    }

    fun testCountRequestsMixedStructure() {
        val collection = HoppCollection(
            name = "Root",
            requests = listOf(
                HoppRESTRequest(name = "R1", method = "GET", endpoint = "/r1"),
                HoppRESTRequest(name = "R2", method = "POST", endpoint = "/r2")
            ),
            folders = listOf(
                HoppCollection(
                    name = "Folder1",
                    requests = listOf(
                        HoppRESTRequest(name = "R3", method = "PUT", endpoint = "/r3")
                    ),
                    folders = listOf(
                        HoppCollection(
                            name = "Folder2",
                            requests = listOf(
                                HoppRESTRequest(name = "R4", method = "DELETE", endpoint = "/r4")
                            )
                        )
                    )
                ),
                HoppCollection(
                    name = "Folder3",
                    requests = listOf(
                        HoppRESTRequest(name = "R5", method = "PATCH", endpoint = "/r5")
                    )
                )
            )
        )
        assertEquals(5, countRequests(collection))
    }

    fun testCountRequestsEmptyFolders() {
        val collection = HoppCollection(
            name = "Root",
            requests = listOf(
                HoppRESTRequest(name = "R1", method = "GET", endpoint = "/r1")
            ),
            folders = listOf(
                HoppCollection(name = "EmptyFolder1"),
                HoppCollection(name = "EmptyFolder2")
            )
        )
        assertEquals(1, countRequests(collection))
    }

    // ── formatGraphQLError tests ─────────────────────────────────────────────

    fun testFormatGraphQLErrorNullMessage() {
        assertEquals("Upload failed with an unknown error", formatGraphQLError(null))
    }

    fun testFormatGraphQLErrorBlankMessage() {
        assertEquals("Upload failed with an unknown error", formatGraphQLError(""))
    }

    fun testFormatGraphQLErrorWhitespaceMessage() {
        assertEquals("Upload failed with an unknown error", formatGraphQLError("   "))
    }

    fun testFormatGraphQLErrorUnauthenticated() {
        val result = formatGraphQLError("UNAUTHENTICATED: Token expired")
        assertTrue("Should mention authentication failure", result.contains("Authentication failed"))
    }

    fun testFormatGraphQLErrorUnauthenticatedCaseInsensitive() {
        val result = formatGraphQLError("unauthenticated: bad token")
        assertTrue("Should be case-insensitive", result.contains("Authentication failed"))
    }

    fun testFormatGraphQLErrorForbidden() {
        val result = formatGraphQLError("FORBIDDEN: Access denied")
        assertTrue("Should mention permission denied", result.contains("Permission denied"))
    }

    fun testFormatGraphQLErrorNotFound() {
        val result = formatGraphQLError("NOT_FOUND: Collection not found")
        assertTrue("Should mention not found", result.contains("not found"))
    }

    fun testFormatGraphQLErrorAlreadyExists() {
        val result = formatGraphQLError("Collection already exists in workspace")
        assertTrue("Should mention already exists", result.contains("already exists"))
    }

    fun testFormatGraphQLErrorRateLimit() {
        val result = formatGraphQLError("Rate limit exceeded, try again later")
        assertTrue("Should mention rate limit", result.contains("Rate limit"))
    }

    fun testFormatGraphQLErrorGenericMessage() {
        val result = formatGraphQLError("Some unknown error occurred")
        assertEquals("Upload failed: Some unknown error occurred", result)
    }

    // ── resolveTargetFile tests ──────────────────────────────────────────────

    fun testResolveTargetFileWithOutputDir() = runBlocking {
        val tempDir = createTempDir("hoppscotch-test")
        try {
            val fileConfig = ChannelConfig.FileConfig(
                outputDir = tempDir.absolutePath,
                fileName = "my_collection"
            )
            val file = resolveTargetFile(fileConfig, "hoppscotch_collection.json")
            assertNotNull(file)
            assertEquals("my_collection.json", file!!.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testResolveTargetFileWithOutputDirNoFileName() = runBlocking {
        val tempDir = createTempDir("hoppscotch-test")
        try {
            val fileConfig = ChannelConfig.FileConfig(
                outputDir = tempDir.absolutePath
            )
            val file = resolveTargetFile(fileConfig, "hoppscotch_collection.json")
            assertNotNull(file)
            assertEquals("hoppscotch_collection.json", file!!.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testResolveTargetFileCreatesDirectory() = runBlocking {
        val tempDir = createTempDir("hoppscotch-test")
        try {
            val newDir = File(tempDir, "subdir/nested")
            assertFalse("Dir should not exist yet", newDir.exists())
            val fileConfig = ChannelConfig.FileConfig(
                outputDir = newDir.absolutePath,
                fileName = "output"
            )
            val file = resolveTargetFile(fileConfig, "hoppscotch_collection.json")
            assertNotNull(file)
            assertTrue("Directory should be created", newDir.exists())
            assertEquals("output.json", file!!.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testResolveTargetFileWithNullFileConfig() = runBlocking {
        // When fileConfig is null, falls back to file chooser which throws in headless test
        try {
            val file = resolveTargetFile(null, "hoppscotch_collection.json")
            // If it doesn't throw, it should return null
            assertNull("Should return null when no fileConfig and no file chooser", file)
        } catch (e: Exception) {
            // Expected: file chooser throws in headless test
            // - ArrayIndexOutOfBoundsException: when file chooser dialog has no items
            // - HeadlessException: when running on Linux CI without a display
            val cause = e.cause ?: e
            assertTrue(
                "Expected file chooser error, got: ${cause.javaClass.simpleName}",
                cause is ArrayIndexOutOfBoundsException || cause is java.awt.HeadlessException
            )
        }
    }

    // ── handleResult tests ───────────────────────────────────────────────────

    fun testHandleResultWithNonHoppscotchMetadataReturnsFalse() = runBlocking {
        val result = ExportResult.Success(count = 5, target = "Test", metadata = null)
        val handled = channel.handleResult(project, result, ChannelConfig.Empty)
        assertFalse("Should return false for non-Hoppscotch metadata", handled)
    }

    fun testHandleResultWithCollectionDataWritesFile() = runBlocking {
        val tempDir = createTempDir("hoppscotch-test")
        try {
            val collectionData = HoppCollection(
                name = "Test",
                requests = listOf(
                    HoppRESTRequest(name = "GET /users", method = "GET", endpoint = "/users")
                )
            )
            val metadata = HoppscotchExportMetadata(
                collectionName = "Test",
                collectionData = collectionData
            )
            val result = ExportResult.Success(count = 1, target = "Hoppscotch Collection", metadata = metadata)
            val config = ChannelConfig.FileConfig(
                outputDir = tempDir.absolutePath,
                fileName = "test_collection"
            )

            // Messages.showInfoMessage throws in headless test — catch it
            try {
                channel.handleResult(project, result, config)
            } catch (_: RuntimeException) {
                // Expected in headless test environment — file is written before the dialog
            }

            val outputFile = File(tempDir, "test_collection.json")
            assertTrue("Output file should exist", outputFile.exists())
            val content = outputFile.readText()
            assertTrue("Output should contain collection name", content.contains("Test"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testHandleResultWithCollectionDataDefaultFileName() = runBlocking {
        val tempDir = createTempDir("hoppscotch-test")
        try {
            val collectionData = HoppCollection(name = "Test")
            val metadata = HoppscotchExportMetadata(
                collectionName = "Test",
                collectionData = collectionData
            )
            val result = ExportResult.Success(count = 0, target = "Hoppscotch Collection", metadata = metadata)
            val config = ChannelConfig.FileConfig(
                outputDir = tempDir.absolutePath
            )

            try {
                channel.handleResult(project, result, config)
            } catch (_: RuntimeException) {
                // Expected in headless test environment
            }

            val outputFile = File(tempDir, "hoppscotch_collection.json")
            assertTrue("Default filename should be used", outputFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testHandleResultWithCollectionDataCreatesDirectory() = runBlocking {
        val tempDir = createTempDir("hoppscotch-test")
        try {
            val newDir = File(tempDir, "output")
            val collectionData = HoppCollection(name = "Test")
            val metadata = HoppscotchExportMetadata(
                collectionName = "Test",
                collectionData = collectionData
            )
            val result = ExportResult.Success(count = 0, target = "Hoppscotch Collection", metadata = metadata)
            val config = ChannelConfig.FileConfig(
                outputDir = newDir.absolutePath,
                fileName = "result"
            )

            try {
                channel.handleResult(project, result, config)
            } catch (_: RuntimeException) {
                // Expected
            }

            assertTrue("Directory should be created", newDir.exists())
            val outputFile = File(newDir, "result.json")
            assertTrue("Output file should exist", outputFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── HoppscotchExportMetadata formatDisplay tests ─────────────────────────

    fun testMetadataFormatDisplayWithCollectionDataReturnsNull() {
        val metadata = HoppscotchExportMetadata(
            collectionName = "Test",
            collectionData = HoppCollection(name = "Test")
        )
        assertNull("Should return null when collectionData is present", metadata.formatDisplay())
    }

    fun testMetadataFormatDisplayWithoutCollectionDataShowsName() {
        val metadata = HoppscotchExportMetadata(
            collectionName = "My Collection"
        )
        assertEquals("My Collection", metadata.formatDisplay())
    }

    fun testMetadataFormatDisplayWithWorkspaceAndCollection() {
        val metadata = HoppscotchExportMetadata(
            collectionName = "My Collection",
            workspaceName = "My Team"
        )
        assertEquals("My Team/My Collection", metadata.formatDisplay())
    }

    fun testMetadataFormatDisplayWithOnlyCollectionId() {
        val metadata = HoppscotchExportMetadata(
            collectionId = "abc-123"
        )
        assertEquals("abc-123", metadata.formatDisplay())
    }

    fun testMetadataFormatDisplayWithWorkspaceAndCollectionId() {
        val metadata = HoppscotchExportMetadata(
            workspaceName = "Team",
            collectionId = "abc-123"
        )
        assertEquals("Team/abc-123", metadata.formatDisplay())
    }

    // ── Reflection helpers ───────────────────────────────────────────────────

    private fun countRequests(collection: HoppCollection): Int {
        val method = HoppscotchChannel::class.java.getDeclaredMethod("countRequests", HoppCollection::class.java)
        method.isAccessible = true
        return method.invoke(channel, collection) as Int
    }

    private fun formatGraphQLError(message: String?): String {
        val method = HoppscotchChannel::class.java.getDeclaredMethod("formatGraphQLError", String::class.java)
        method.isAccessible = true
        return method.invoke(channel, message) as String
    }

    private suspend fun resolveTargetFile(
        fileConfig: ChannelConfig.FileConfig?,
        defaultFileName: String
    ): File? {
        val method = HoppscotchChannel::class.java.getDeclaredMethod(
            "resolveTargetFile",
            com.intellij.openapi.project.Project::class.java,
            ChannelConfig.FileConfig::class.java,
            String::class.java,
            kotlin.coroutines.Continuation::class.java
        )
        method.isAccessible = true
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val result = method.invoke(channel, project, fileConfig, defaultFileName, cont)
            if (result !== kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) {
                @Suppress("UNCHECKED_CAST")
                cont.resumeWith(Result.success(result as File?))
            }
        }
    }
}
