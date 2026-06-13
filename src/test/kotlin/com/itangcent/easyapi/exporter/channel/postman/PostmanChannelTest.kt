package com.itangcent.easyapi.exporter.channel.postman

import com.itangcent.easyapi.exporter.channel.ChannelConfig
import com.itangcent.easyapi.exporter.model.ExportContext
import com.itangcent.easyapi.exporter.model.ExportResult
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.postman.PostmanExportMetadata
import com.itangcent.easyapi.exporter.postman.model.CollectionInfo
import com.itangcent.easyapi.exporter.postman.model.PostmanCollection
import com.itangcent.easyapi.exporter.postman.model.PostmanItem
import com.itangcent.easyapi.exporter.postman.model.PostmanRequest
import com.itangcent.easyapi.exporter.postman.model.PostmanUrl
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import java.io.File
import kotlin.coroutines.Continuation

/**
 * Tests for PostmanChannel's export logic, handleResult, resolveTargetFile, and countApiItems.
 */
class PostmanChannelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var channel: PostmanChannel

    override fun setUp() {
        super.setUp()
        channel = PostmanChannel()
    }

    private fun url(raw: String) = PostmanUrl(raw = raw)
    private fun request(method: String, rawUrl: String) = PostmanRequest(method = method, url = url(rawUrl))

    // ── Channel metadata tests ───────────────────────────────────────────────

    fun testChannelProperties() {
        assertEquals("postman", channel.id)
        assertEquals("Postman", channel.displayName)
        assertFalse(channel.supportsGrpc)
        assertTrue(channel.exposeAsAction)
        assertEquals("Export to Postman", channel.actionText)
    }

    // ── countApiItems tests ──────────────────────────────────────────────────

    fun testCountApiItemsEmptyCollection() {
        val collection = PostmanCollection(
            info = CollectionInfo(name = "Test", schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"),
            item = emptyList()
        )
        assertEquals(0, countApiItems(collection))
    }

    fun testCountApiItemsFlatItems() {
        val collection = PostmanCollection(
            info = CollectionInfo(name = "Test", schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"),
            item = listOf(
                PostmanItem(name = "GET /users", request = request("GET", "https://api.example.com/users")),
                PostmanItem(name = "POST /users", request = request("POST", "https://api.example.com/users")),
                PostmanItem(name = "GET /users/{id}", request = request("GET", "https://api.example.com/users/1"))
            )
        )
        assertEquals(3, countApiItems(collection))
    }

    fun testCountApiItemsNestedFolders() {
        val collection = PostmanCollection(
            info = CollectionInfo(name = "Test", schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"),
            item = listOf(
                PostmanItem(
                    name = "User Folder",
                    item = listOf(
                        PostmanItem(name = "GET /users", request = request("GET", "https://api.example.com/users")),
                        PostmanItem(name = "POST /users", request = request("POST", "https://api.example.com/users"))
                    )
                ),
                PostmanItem(name = "GET /health", request = request("GET", "https://api.example.com/health"))
            )
        )
        assertEquals(3, countApiItems(collection))
    }

    fun testCountApiItemsDeeplyNested() {
        val collection = PostmanCollection(
            info = CollectionInfo(name = "Test", schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"),
            item = listOf(
                PostmanItem(
                    name = "Level 1",
                    item = listOf(
                        PostmanItem(
                            name = "Level 2",
                            item = listOf(
                                PostmanItem(name = "Deep API", request = request("GET", "https://api.example.com/deep"))
                            )
                        )
                    )
                )
            )
        )
        assertEquals(1, countApiItems(collection))
    }

    fun testCountApiItemsFolderWithoutRequest() {
        val collection = PostmanCollection(
            info = CollectionInfo(name = "Test", schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"),
            item = listOf(
                PostmanItem(name = "Empty Folder", item = emptyList()),
                PostmanItem(name = "GET /users", request = request("GET", "https://api.example.com/users"))
            )
        )
        assertEquals(1, countApiItems(collection))
    }

    // ── export without token tests ───────────────────────────────────────────

    fun testExportWithoutTokenReturnsCollectionData() = runBlocking {
        val endpoint = ApiEndpoint(
            name = "Get Users",
            metadata = HttpMetadata(method = HttpMethod.GET, path = "/users")
        )
        val context = ExportContext(
            project = project,
            endpoints = listOf(endpoint),
            channelId = "postman"
        )
        val result = channel.export(context)
        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        assertEquals("Postman Collection", success.target)
        val metadata = success.metadata as PostmanExportMetadata
        assertNotNull(metadata.collectionData)
        assertNull(metadata.collectionId)
    }

    fun testExportWithoutTokenWithCollectionName() = runBlocking {
        val endpoint = ApiEndpoint(
            name = "Get Users",
            metadata = HttpMetadata(method = HttpMethod.GET, path = "/users")
        )
        val context = ExportContext(
            project = project,
            endpoints = listOf(endpoint),
            channelId = "postman",
            channelConfig = ChannelConfig.PostmanConfig(collectionName = "My Collection")
        )
        val result = channel.export(context)
        assertTrue(result is ExportResult.Success)
        val success = result as ExportResult.Success
        val metadata = success.metadata as PostmanExportMetadata
        assertNotNull(metadata.collectionData)
        assertEquals("My Collection", metadata.collectionName)
    }

    // ── handleResult tests ───────────────────────────────────────────────────

    fun testHandleResultWithNonPostmanMetadataReturnsFalse() = runBlocking {
        val result = ExportResult.Success(count = 5, target = "Test", metadata = null)
        val handled = channel.handleResult(project, result, ChannelConfig.Empty)
        assertFalse(handled)
    }

    fun testHandleResultWithCollectionDataWritesFile() = runBlocking {
        val tempDir = createTempDir("postman-test")
        try {
            val collectionData = PostmanCollection(
                info = CollectionInfo(name = "Test", schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"),
                item = listOf(
                    PostmanItem(name = "GET /users", request = request("GET", "https://api.example.com/users"))
                )
            )
            val metadata = PostmanExportMetadata(
                collectionName = "Test",
                collectionData = collectionData
            )
            val result = ExportResult.Success(count = 1, target = "Postman Collection", metadata = metadata)
            val config = ChannelConfig.FileConfig(
                outputDir = tempDir.absolutePath,
                fileName = "test_collection"
            )
            // Messages.showInfoMessage throws RuntimeException in test mode (TestDialog)
            try {
                channel.handleResult(project, result, config)
            } catch (_: RuntimeException) {
                // Expected in test environment — file is written before the dialog
            }

            val outputFile = File(tempDir, "test_collection.json")
            assertTrue(outputFile.exists())
            assertTrue(outputFile.readText().contains("Test"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testHandleResultWithCollectionDataDefaultFileName() = runBlocking {
        val tempDir = createTempDir("postman-test")
        try {
            val collectionData = PostmanCollection(
                info = CollectionInfo(name = "Test", schema = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"),
                item = emptyList()
            )
            val metadata = PostmanExportMetadata(
                collectionName = "Test",
                collectionData = collectionData
            )
            val result = ExportResult.Success(count = 0, target = "Postman Collection", metadata = metadata)
            val config = ChannelConfig.FileConfig(
                outputDir = tempDir.absolutePath
            )
            try {
                channel.handleResult(project, result, config)
            } catch (_: RuntimeException) {
                // Expected in test environment
            }

            val outputFile = File(tempDir, "postman_collection.json")
            assertTrue(outputFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── resolveTargetFile tests ──────────────────────────────────────────────

    fun testResolveTargetFileWithOutputDir() = runBlocking {
        val tempDir = createTempDir("postman-test")
        try {
            val fileConfig = ChannelConfig.FileConfig(
                outputDir = tempDir.absolutePath,
                fileName = "my_collection"
            )
            val file = resolveTargetFile(project, fileConfig, "postman_collection.json")
            assertNotNull(file)
            assertEquals("my_collection.json", file!!.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testResolveTargetFileWithOutputDirNoFileName() = runBlocking {
        val tempDir = createTempDir("postman-test")
        try {
            val fileConfig = ChannelConfig.FileConfig(
                outputDir = tempDir.absolutePath
            )
            val file = resolveTargetFile(project, fileConfig, "postman_collection.json")
            assertNotNull(file)
            assertEquals("postman_collection.json", file!!.name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun testResolveTargetFileCreatesDirectory() = runBlocking {
        val tempDir = createTempDir("postman-test")
        try {
            val newDir = File(tempDir, "subdir/nested")
            val fileConfig = ChannelConfig.FileConfig(
                outputDir = newDir.absolutePath,
                fileName = "output"
            )
            val file = resolveTargetFile(project, fileConfig, "postman_collection.json")
            assertNotNull(file)
            assertTrue(newDir.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── createOptionsPanel test ──────────────────────────────────────────────

    fun testCreateOptionsPanel() {
        val panel = channel.createOptionsPanel(project)
        assertNotNull(panel)
        assertNotNull(panel.component)
    }

    // ── Reflection helpers for private methods ───────────────────────────────

    private fun countApiItems(collection: PostmanCollection): Int {
        val method = PostmanChannel::class.java.getDeclaredMethod("countApiItems", PostmanCollection::class.java)
        method.isAccessible = true
        return method.invoke(channel, collection) as Int
    }

    private suspend fun resolveTargetFile(
        project: com.intellij.openapi.project.Project,
        fileConfig: ChannelConfig.FileConfig?,
        defaultFileName: String
    ): File? {
        val method = PostmanChannel::class.java.getDeclaredMethod(
            "resolveTargetFile",
            com.intellij.openapi.project.Project::class.java,
            ChannelConfig.FileConfig::class.java,
            String::class.java,
            Continuation::class.java
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
