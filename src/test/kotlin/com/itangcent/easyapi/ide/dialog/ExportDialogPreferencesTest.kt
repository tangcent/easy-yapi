package com.itangcent.easyapi.ide.dialog

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class ExportDialogPreferencesPersistenceTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var persistence: ExportDialogPreferencesPersistence

    override fun setUp() {
        super.setUp()
        persistence = ExportDialogPreferencesPersistence(project)
        persistence.reset()
    }

    override fun tearDown() {
        persistence.reset()
        super.tearDown()
    }

    fun testLoadEmptyPreferences() {
        val prefs = persistence.load()
        assertNull("Last export format should be null", prefs.lastExportFormat)
        assertNull("Last output dir should be null", prefs.lastOutputDir)
        assertNull("Last file name should be null", prefs.lastFileName)
        assertNull("Last postman workspace ID should be null", prefs.lastPostmanWorkspaceId)
        assertNull("Last postman workspace name should be null", prefs.lastPostmanWorkspaceName)
        assertNull("Last postman collection ID should be null", prefs.lastPostmanCollectionId)
        assertNull("Last postman collection name should be null", prefs.lastPostmanCollectionName)
        assertNull("Last yapi token should be null", prefs.lastYapiToken)
    }

    fun testSaveAndLoadPreferences() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = "/tmp/output",
            lastFileName = "api_doc"
        )
        
        persistence.save(prefs)
        val loaded = persistence.load()
        
        assertEquals("MARKDOWN", loaded.lastExportFormat)
        assertEquals("/tmp/output", loaded.lastOutputDir)
        assertEquals("api_doc", loaded.lastFileName)
    }

    fun testSaveAndLoadPostmanPreferences() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "POSTMAN",
            lastPostmanWorkspaceId = "ws-123",
            lastPostmanWorkspaceName = "My Workspace",
            lastPostmanCollectionId = "col-456",
            lastPostmanCollectionName = "My Collection"
        )
        
        persistence.save(prefs)
        val loaded = persistence.load()
        
        assertEquals("POSTMAN", loaded.lastExportFormat)
        assertEquals("ws-123", loaded.lastPostmanWorkspaceId)
        assertEquals("My Workspace", loaded.lastPostmanWorkspaceName)
        assertEquals("col-456", loaded.lastPostmanCollectionId)
        assertEquals("My Collection", loaded.lastPostmanCollectionName)
    }

    fun testSaveAndLoadYapiToken() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "YAPI",
            lastYapiToken = "abc123def456"
        )

        persistence.save(prefs)
        val loaded = persistence.load()

        assertEquals("YAPI", loaded.lastExportFormat)
        assertEquals("abc123def456", loaded.lastYapiToken)
    }

    fun testOverwritePreferences() {
        val prefs1 = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = "/tmp/output1"
        )
        persistence.save(prefs1)
        
        val prefs2 = ExportDialogPreferences(
            lastExportFormat = "POSTMAN",
            lastPostmanWorkspaceId = "ws-789"
        )
        persistence.save(prefs2)
        
        val loaded = persistence.load()
        assertEquals("POSTMAN", loaded.lastExportFormat)
        assertNull("Output dir should be null after overwrite", loaded.lastOutputDir)
        assertEquals("ws-789", loaded.lastPostmanWorkspaceId)
    }

    fun testReset() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "CURL",
            lastOutputDir = "/tmp/test"
        )
        persistence.save(prefs)
        
        persistence.reset()
        
        val loaded = persistence.load()
        assertNull("Last export format should be null after reset", loaded.lastExportFormat)
        assertNull("Last output dir should be null after reset", loaded.lastOutputDir)
    }

    fun testPartialPreferences() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "HTTP_CLIENT"
        )
        
        persistence.save(prefs)
        val loaded = persistence.load()
        
        assertEquals("HTTP_CLIENT", loaded.lastExportFormat)
        assertNull("Other fields should be null", loaded.lastOutputDir)
        assertNull("Other fields should be null", loaded.lastFileName)
        assertNull("Other fields should be null", loaded.lastPostmanWorkspaceId)
    }

    fun testEmptyStrings() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "",
            lastOutputDir = "",
            lastFileName = ""
        )
        
        persistence.save(prefs)
        val loaded = persistence.load()
        
        assertEquals("Empty strings should be preserved", "", loaded.lastExportFormat)
        assertEquals("Empty strings should be preserved", "", loaded.lastOutputDir)
        assertEquals("Empty strings should be preserved", "", loaded.lastFileName)
    }

    fun testSpecialCharacters() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = "/tmp/测试目录/папка",
            lastFileName = "api-文档_v1.0",
            lastPostmanWorkspaceName = "Workspace 🚀 Test"
        )
        
        persistence.save(prefs)
        val loaded = persistence.load()
        
        assertEquals("/tmp/测试目录/папка", loaded.lastOutputDir)
        assertEquals("api-文档_v1.0", loaded.lastFileName)
        assertEquals("Workspace 🚀 Test", loaded.lastPostmanWorkspaceName)
    }

    fun testLongValues() {
        val longPath = "/tmp/" + "a".repeat(500)
        val longName = "b".repeat(500)
        
        val prefs = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = longPath,
            lastFileName = longName
        )
        
        persistence.save(prefs)
        val loaded = persistence.load()
        
        assertEquals(longPath, loaded.lastOutputDir)
        assertEquals(longName, loaded.lastFileName)
    }

    fun testAllFields() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "POSTMAN",
            lastOutputDir = "/output",
            lastFileName = "export",
            lastPostmanWorkspaceId = "workspace-id",
            lastPostmanWorkspaceName = "Workspace Name",
            lastPostmanCollectionId = "collection-id",
            lastPostmanCollectionName = "Collection Name",
            lastYapiToken = "yapi-token-abc"
        )
        
        persistence.save(prefs)
        val loaded = persistence.load()
        
        assertEquals("POSTMAN", loaded.lastExportFormat)
        assertEquals("/output", loaded.lastOutputDir)
        assertEquals("export", loaded.lastFileName)
        assertEquals("workspace-id", loaded.lastPostmanWorkspaceId)
        assertEquals("Workspace Name", loaded.lastPostmanWorkspaceName)
        assertEquals("collection-id", loaded.lastPostmanCollectionId)
        assertEquals("Collection Name", loaded.lastPostmanCollectionName)
        assertEquals("yapi-token-abc", loaded.lastYapiToken)
    }

    fun testMultipleSaveLoadCycles() {
        for (i in 1..5) {
            val prefs = ExportDialogPreferences(
                lastExportFormat = "FORMAT_$i",
                lastOutputDir = "/dir_$i"
            )
            persistence.save(prefs)
            val loaded = persistence.load()
            assertEquals("FORMAT_$i", loaded.lastExportFormat)
            assertEquals("/dir_$i", loaded.lastOutputDir)
        }
    }

    fun testPreferencesDataClassDefaultValues() {
        val prefs = ExportDialogPreferences()
        assertNull(prefs.lastExportFormat)
        assertNull(prefs.lastOutputDir)
        assertNull(prefs.lastFileName)
        assertNull(prefs.lastPostmanWorkspaceId)
        assertNull(prefs.lastPostmanWorkspaceName)
        assertNull(prefs.lastPostmanCollectionId)
        assertNull(prefs.lastPostmanCollectionName)
        assertNull(prefs.lastYapiToken)
    }

    fun testPreferencesDataClassCopy() {
        val original = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = "/tmp",
            lastFileName = "test",
            lastPostmanWorkspaceId = "ws-1",
            lastPostmanWorkspaceName = "WS",
            lastPostmanCollectionId = "col-1",
            lastPostmanCollectionName = "Col",
            lastYapiToken = "yapi-tok"
        )
        
        val copy = original.copy()
        
        assertEquals(original.lastExportFormat, copy.lastExportFormat)
        assertEquals(original.lastOutputDir, copy.lastOutputDir)
        assertEquals(original.lastFileName, copy.lastFileName)
        assertEquals(original.lastPostmanWorkspaceId, copy.lastPostmanWorkspaceId)
        assertEquals(original.lastPostmanWorkspaceName, copy.lastPostmanWorkspaceName)
        assertEquals(original.lastPostmanCollectionId, copy.lastPostmanCollectionId)
        assertEquals(original.lastPostmanCollectionName, copy.lastPostmanCollectionName)
        assertEquals(original.lastYapiToken, copy.lastYapiToken)
    }

    fun testPreferencesDataClassEquality() {
        val prefs1 = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = "/tmp"
        )
        val prefs2 = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = "/tmp"
        )
        val prefs3 = ExportDialogPreferences(
            lastExportFormat = "POSTMAN"
        )
        
        assertEquals(prefs1, prefs2)
        assertNotEquals(prefs1, prefs3)
    }

    fun testPreferencesDataClassHashCode() {
        val prefs1 = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = "/tmp"
        )
        val prefs2 = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = "/tmp"
        )
        
        assertEquals(prefs1.hashCode(), prefs2.hashCode())
    }

    fun testPreferencesDataClassToString() {
        val prefs = ExportDialogPreferences(
            lastExportFormat = "MARKDOWN",
            lastOutputDir = "/tmp"
        )
        
        val str = prefs.toString()
        assertTrue("toString should contain lastExportFormat", str.contains("MARKDOWN"))
        assertTrue("toString should contain lastOutputDir", str.contains("/tmp"))
    }
}
