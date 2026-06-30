package com.itangcent.easyapi.config.source

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.config.parser.DirectiveSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProjectFileConfigSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var project: Project
    private lateinit var configTextParser: ConfigTextParser
    private lateinit var projectFileConfigSource: ProjectFileConfigSource

    @Before
    fun setUp() {
        configTextParser = mock()
        project = mock()
        whenever(project.getService(ConfigTextParser::class.java)).thenReturn(configTextParser)
    }

    @Test
    fun testPriority() {
        projectFileConfigSource = ProjectFileConfigSource(project, "/tmp")
        assertEquals(4, projectFileConfigSource.priority)
    }

    @Test
    fun testSourceId() {
        projectFileConfigSource = ProjectFileConfigSource(project, "/tmp")
        assertEquals("project", projectFileConfigSource.sourceId)
    }

    @Test
    fun testCollectWithNoConfigFiles() {
        projectFileConfigSource = ProjectFileConfigSource(project, tempFolder.root.absolutePath)

        runBlocking {
            val entries = projectFileConfigSource.collect().toList()
            assertTrue(entries.isEmpty())
        }
    }

    @Test
    fun testCollectWithEasyApiConfigFile() {
        val configFile = tempFolder.newFile(".easy.api.config")
        configFile.writeText("server=https://api.example.com")

        val parsedEntries = sequenceOf(
            ConfigEntry("server", "https://api.example.com", "project", DirectiveSnapshot())
        )

        projectFileConfigSource = ProjectFileConfigSource(project, tempFolder.root.absolutePath)

        runBlocking {
            whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)

            val entries = projectFileConfigSource.collect().toList()
            assertEquals(1, entries.size)
            assertEquals("server", entries[0].key)
            assertEquals("https://api.example.com", entries[0].value)
        }
    }

    @Test
    fun testCollectWithPropertiesFile() {
        val configFile = tempFolder.newFile(".easy.api.config.properties")
        configFile.writeText("timeout=30000")

        val parsedEntries = sequenceOf(
            ConfigEntry("timeout", "30000", "project", DirectiveSnapshot())
        )

        projectFileConfigSource = ProjectFileConfigSource(project, tempFolder.root.absolutePath)

        runBlocking {
            whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)

            val entries = projectFileConfigSource.collect().toList()
            assertEquals(1, entries.size)
            assertEquals("timeout", entries[0].key)
        }
    }

    @Test
    fun testCollectWithYamlFile() {
        val configFile = tempFolder.newFile(".easy.api.config.yml")
        configFile.writeText("server: https://api.example.com")

        val parsedEntries = sequenceOf(
            ConfigEntry("server", "https://api.example.com", "project", DirectiveSnapshot())
        )

        projectFileConfigSource = ProjectFileConfigSource(project, tempFolder.root.absolutePath)

        runBlocking {
            whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)

            val entries = projectFileConfigSource.collect().toList()
            assertEquals(1, entries.size)
        }
    }

    @Test
    fun testSetProjectBasePath() {
        projectFileConfigSource = ProjectFileConfigSource(project, "/tmp")
        assertEquals("project", projectFileConfigSource.sourceId)

        projectFileConfigSource.setProjectBasePath("/new/path")
        assertEquals("project", projectFileConfigSource.sourceId)
    }

    @Test
    fun testCollectWithMultipleConfigFiles() {
        val configFile1 = tempFolder.newFile(".easy.api.config")
        configFile1.writeText("server=https://api.example.com")

        val configFile2 = tempFolder.newFile(".easy.api.config.properties")
        configFile2.writeText("timeout=30000")

        val parsedEntries1 = sequenceOf(
            ConfigEntry("server", "https://api.example.com", "project", DirectiveSnapshot())
        )
        val parsedEntries2 = sequenceOf(
            ConfigEntry("timeout", "30000", "project", DirectiveSnapshot())
        )

        projectFileConfigSource = ProjectFileConfigSource(project, tempFolder.root.absolutePath)

        runBlocking {
            whenever(configTextParser.parse(any(), any(), anyOrNull()))
                .thenReturn(parsedEntries1)
                .thenReturn(parsedEntries2)

            val entries = projectFileConfigSource.collect().toList()
            assertEquals(2, entries.size)
        }
    }

    // ---- easyapiFolderFiles + legacyFiles + disabledFiles ----

    @Test
    fun testLegacyFilesFindsFilesAtMultipleDirLevels() {
        val subDir = tempFolder.newFolder("subpkg")
        val rootConfig = tempFolder.newFile(".easy.api.config")
        val subConfig = subDir.resolve(".easy.api.config").apply {
            createNewFile()
        }

        val detected = ProjectFileConfigSource.legacyFiles(subDir.absolutePath)
        // Walks up from subDir → tempFolder root; both should be found.
        assertTrue(detected.any { it == subConfig.toPath().toAbsolutePath() })
        assertTrue(detected.any { it == rootConfig.toPath().toAbsolutePath() })
    }

    @Test
    fun testEasyapiFolderFilesListsRegularFiles() {
        val easyapiDir = tempFolder.newFolder(".easyapi")
        val a = java.io.File(easyapiDir, "a.rules").apply { writeText("api.name=A") }
        val b = java.io.File(easyapiDir, "b.rules").apply { writeText("api.name=B") }
        java.io.File(easyapiDir, "subdir").apply { mkdirs() } // not a regular file

        val files = ProjectFileConfigSource.easyapiFolderFiles(tempFolder.root.absolutePath)
        val names = files.map { it.fileName.toString() }
        assertEquals(listOf("a.rules", "b.rules"), names)
    }

    @Test
    fun testEasyapiFolderFilesEmptyWhenFolderMissing() {
        val files = ProjectFileConfigSource.easyapiFolderFiles(tempFolder.root.absolutePath)
        assertTrue(files.isEmpty())
    }

    @Test
    fun testDisabledFileIsSkipped() {
        val configFile = tempFolder.newFile(".easy.api.config")
        configFile.writeText("api.name=SkipMe")

        projectFileConfigSource = ProjectFileConfigSource(
            project,
            tempFolder.root.absolutePath,
            disabledFiles = setOf(configFile.absolutePath)
        )

        runBlocking {
            whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(
                sequenceOf(ConfigEntry("api.name", "SkipMe", "project", DirectiveSnapshot()))
            )

            val entries = projectFileConfigSource.collect().toList()
            assertTrue("disabled file should be skipped", entries.isEmpty())
        }
    }

    @Test
    fun testEasyapiFolderFileIsLoaded() {
        val easyapiDir = tempFolder.newFolder(".easyapi")
        val ruleFile = java.io.File(easyapiDir, "rule.properties").apply { writeText("api.name=FromFolder") }

        projectFileConfigSource = ProjectFileConfigSource(
            project,
            tempFolder.root.absolutePath
        )

        runBlocking {
            whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(
                sequenceOf(ConfigEntry("api.name", "FromFolder", "project", DirectiveSnapshot()))
            )

            val entries = projectFileConfigSource.collect().toList()
            assertEquals(1, entries.size)
            assertEquals("FromFolder", entries[0].value)
        }
    }

    @Test
    fun testEasyapiFolderAndLegacyBothLoaded() {
        val easyapiDir = tempFolder.newFolder(".easyapi")
        java.io.File(easyapiDir, "folder.rules").apply { writeText("api.name=Folder") }
        val legacyFile = tempFolder.newFile(".easy.api.config")
        legacyFile.writeText("api.name=Legacy")

        projectFileConfigSource = ProjectFileConfigSource(
            project,
            tempFolder.root.absolutePath
        )

        runBlocking {
            whenever(configTextParser.parse(any(), any(), anyOrNull()))
                .thenReturn(sequenceOf(ConfigEntry("api.name", "Folder", "project", DirectiveSnapshot())))
                .thenReturn(sequenceOf(ConfigEntry("api.name", "Legacy", "project", DirectiveSnapshot())))

            val entries = projectFileConfigSource.collect().toList()
            assertEquals(2, entries.size)
        }
    }

    @Test
    fun testDisabledFolderFileSkipped() {
        val easyapiDir = tempFolder.newFolder(".easyapi")
        val ruleFile = java.io.File(easyapiDir, "rule.properties").apply { writeText("api.name=Skip") }

        projectFileConfigSource = ProjectFileConfigSource(
            project,
            tempFolder.root.absolutePath,
            disabledFiles = setOf(ruleFile.absolutePath)
        )

        runBlocking {
            whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(
                sequenceOf(ConfigEntry("api.name", "Skip", "project", DirectiveSnapshot()))
            )

            val entries = projectFileConfigSource.collect().toList()
            assertTrue("disabled folder file should be skipped", entries.isEmpty())
        }
    }
}
