package com.itangcent.easyapi.core.config.source

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.config.model.ConfigEntry
import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.config.parser.DirectiveSnapshot
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.testFramework.ConstantSettingBinder
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
import java.nio.file.Path

class GlobalFileConfigSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var realParserProject: Project
    private lateinit var mockParserProject: Project
    private lateinit var mockParser: ConfigTextParser

    @Before
    fun setUp() {
        realParserProject = newProjectWithRealParser()
        mockParser = mock()
        mockParserProject = mock()
        whenever(mockParserProject.getService(ConfigTextParser::class.java)).thenReturn(mockParser)
    }

    private fun newProjectWithRealParser(): Project {
        val project = mock<Project>()
        val settingBinder = ConstantSettingBinder()
        whenever(project.getService(SettingBinder::class.java)).thenReturn(settingBinder)
        val realParser = ConfigTextParser(project)
        whenever(project.getService(ConfigTextParser::class.java)).thenReturn(realParser)
        return project
    }

    @Test
    fun testPriority() {
        val source = GlobalFileConfigSource(realParserProject, tempFolder.root.toPath(), emptySet())
        assertEquals(2, source.priority)
    }

    @Test
    fun testSourceId() {
        val source = GlobalFileConfigSource(realParserProject, tempFolder.root.toPath(), emptySet())
        assertEquals("global-file", source.sourceId)
    }

    @Test
    fun testEmptyFileList() = runBlocking {
        val source = GlobalFileConfigSource(realParserProject, tempFolder.root.toPath(), emptySet())
        val entries = source.collect().toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testCollectFromTempConfigFile() = runBlocking {
        val configFile = tempFolder.newFile("global.easy.api.config")
        configFile.writeText("api.name=Test API\napi.version=1.0.0")

        val source = GlobalFileConfigSource(realParserProject, tempFolder.root.toPath(), emptySet())
        val entries = source.collect().toList()

        assertEquals(2, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("Test API", entries[0].value)
        assertEquals("global-file", entries[0].sourceId)
        assertEquals("api.version", entries[1].key)
        assertEquals("1.0.0", entries[1].value)
    }

    @Test
    fun testCollectFromMultipleFiles() = runBlocking {
        val file1 = tempFolder.newFile("rules1.config")
        file1.writeText("api.name=First")
        val file2 = tempFolder.newFile("rules2.config")
        file2.writeText("api.name=Second")

        val parsedEntries1 = sequenceOf(ConfigEntry("api.name", "First", "global-file", DirectiveSnapshot()))
        val parsedEntries2 = sequenceOf(ConfigEntry("api.name", "Second", "global-file", DirectiveSnapshot()))

        whenever(mockParser.parse(any(), any(), anyOrNull()))
            .thenReturn(parsedEntries1)
            .thenReturn(parsedEntries2)

        val source = GlobalFileConfigSource(mockParserProject, tempFolder.root.toPath(), emptySet())
        val entries = source.collect().toList()

        assertEquals(2, entries.size)
        assertEquals("First", entries[0].value)
        assertEquals("Second", entries[1].value)
    }

    @Test
    fun testDisabledFileSkipped() = runBlocking {
        val enabled = tempFolder.newFile("enabled.config")
        enabled.writeText("api.name=Present")
        val disabled = tempFolder.newFile("disabled.config")
        disabled.writeText("api.name=Hidden")

        val source = GlobalFileConfigSource(
            realParserProject,
            tempFolder.root.toPath(),
            disabledFiles = setOf(disabled.absolutePath)
        )
        val entries = source.collect().toList()

        assertEquals(1, entries.size)
        assertEquals("Present", entries[0].value)
    }

    @Test
    fun testNonRegularFileSkipped() = runBlocking {
        tempFolder.newFolder("subdir")
        val realFile = tempFolder.newFile("real.config")
        realFile.writeText("api.name=Real")

        val source = GlobalFileConfigSource(realParserProject, tempFolder.root.toPath(), emptySet())
        val entries = source.collect().toList()

        assertEquals(1, entries.size)
        assertEquals("Real", entries[0].value)
    }

    @Test
    fun testSourceIdPropagatedToEntries() = runBlocking {
        val configFile = tempFolder.newFile("check.config")
        configFile.writeText("api.name=Check")

        val source = GlobalFileConfigSource(realParserProject, tempFolder.root.toPath(), emptySet())
        val entries = source.collect().toList()

        assertTrue(entries.isNotEmpty())
        entries.forEach { assertEquals("global-file", it.sourceId) }
    }

    @Test
    fun testYamlStyleConfigFile() = runBlocking {
        val configFile = tempFolder.newFile("rules.yml")
        configFile.writeText("api.name:\n  value: YamlTest")

        whenever(mockParser.parse(any(), any(), anyOrNull())).thenReturn(
            sequenceOf(ConfigEntry("api.name", "YamlTest", "global-file", DirectiveSnapshot()))
        )

        val source = GlobalFileConfigSource(mockParserProject, tempFolder.root.toPath(), emptySet())
        val entries = source.collect().toList()

        assertEquals(1, entries.size)
        assertEquals("YamlTest", entries[0].value)
    }

    @Test
    fun testListFilesReturnsEmptyForMissingDir() {
        val missing = Path.of("/does/not/exist/easyapi-test")
        assertTrue(GlobalFileConfigSource.listFiles(missing).isEmpty())
    }

    @Test
    fun testListFilesReturnsRegularFilesSorted() {
        val dir = tempFolder.root.toPath()
        tempFolder.newFile("z.rules")
        tempFolder.newFile("a.rules")
        tempFolder.newFolder("subdir") // not a regular file

        val files = GlobalFileConfigSource.listFiles(dir)
        val names = files.map { it.fileName.toString() }
        assertEquals(listOf("a.rules", "z.rules"), names)
    }
}
