package com.itangcent.easyapi.core.config

import com.itangcent.easyapi.core.config.model.ConfigEntry
import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.config.parser.DirectiveSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class FileConfigHelperTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val parser: ConfigTextParser = mock()

    @Test
    fun testEmptyFileListReturnsEmptySequence() = runBlocking {
        val entries = parser.parseFiles(emptyList(), "test").toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testParseSingleFile() = runBlocking {
        val file = tempFolder.newFile("rules.properties")
        file.writeText("api.name=test")

        val expected = sequenceOf(
            ConfigEntry("api.name", "test", "test", DirectiveSnapshot())
        )
        whenever(parser.parse("api.name=test", "test", file.parentFile.absolutePath))
            .thenReturn(expected)

        val entries = parser.parseFiles(listOf(file.toPath()), "test").toList()
        assertEquals(1, entries.size)
        assertEquals("api.name", entries[0].key)
        assertEquals("test", entries[0].value)
        assertEquals("test", entries[0].sourceId)
    }

    @Test
    fun testParseMultipleFiles() = runBlocking {
        val file1 = tempFolder.newFile("rules1.properties")
        file1.writeText("api.name=first")
        val file2 = tempFolder.newFile("rules2.properties")
        file2.writeText("api.version=2.0")

        whenever(parser.parse("api.name=first", "test", file1.parentFile.absolutePath))
            .thenReturn(sequenceOf(ConfigEntry("api.name", "first", "test", DirectiveSnapshot())))
        whenever(parser.parse("api.version=2.0", "test", file2.parentFile.absolutePath))
            .thenReturn(sequenceOf(ConfigEntry("api.version", "2.0", "test", DirectiveSnapshot())))

        val entries = parser.parseFiles(listOf(file1.toPath(), file2.toPath()), "test").toList()
        assertEquals(2, entries.size)
        assertEquals("first", entries[0].value)
        assertEquals("2.0", entries[1].value)
    }

    @Test
    fun testSourceIdPropagatedToParse() = runBlocking {
        val file = tempFolder.newFile("rules.properties")
        file.writeText("api.name=test")

        whenever(parser.parse(any(), any(), anyOrNull()))
            .thenReturn(sequenceOf(ConfigEntry("api.name", "test", "custom-source", DirectiveSnapshot())))

        val entries = parser.parseFiles(listOf(file.toPath()), "custom-source").toList()
        assertEquals(1, entries.size)
        assertEquals("custom-source", entries[0].sourceId)
    }

    @Test
    fun testBaseDirIsFileParent() = runBlocking {
        val file = tempFolder.newFile("rules.properties")
        file.writeText("api.name=test")
        val parentDir = file.parentFile.absolutePath

        whenever(parser.parse("api.name=test", "test", parentDir))
            .thenReturn(sequenceOf(ConfigEntry("api.name", "test", "test", DirectiveSnapshot())))

        val entries = parser.parseFiles(listOf(file.toPath()), "test").toList()
        assertEquals(1, entries.size)
    }

    @Test
    fun testUnreadableFileIsSkipped() = runBlocking {
        val missingFile = Path.of("/nonexistent/config.properties")

        val entries = parser.parseFiles(listOf(missingFile), "test").toList()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun testMixedReadableAndUnreadableFiles() = runBlocking {
        val readable = tempFolder.newFile("readable.properties")
        readable.writeText("api.name=present")

        whenever(parser.parse("api.name=present", "test", readable.parentFile.absolutePath))
            .thenReturn(sequenceOf(ConfigEntry("api.name", "present", "test", DirectiveSnapshot())))

        val missingFile = Path.of("/nonexistent/config.properties")

        val entries = parser.parseFiles(listOf(missingFile, readable.toPath()), "test").toList()
        assertEquals(1, entries.size)
        assertEquals("present", entries[0].value)
    }
}
