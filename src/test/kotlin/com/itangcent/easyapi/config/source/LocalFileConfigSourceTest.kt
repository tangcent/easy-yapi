package com.itangcent.easyapi.config.source

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

class LocalFileConfigSourceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var configTextParser: ConfigTextParser
    private lateinit var localFileConfigSource: LocalFileConfigSource

    @Before
    fun setUp() {
        configTextParser = mock()
    }

    @Test
    fun testPriority() {
        localFileConfigSource = LocalFileConfigSource("/tmp", configTextParser)
        assertEquals(4, localFileConfigSource.priority)
    }

    @Test
    fun testSourceId() {
        localFileConfigSource = LocalFileConfigSource("/tmp", configTextParser)
        assertEquals("local", localFileConfigSource.sourceId)
    }

    @Test
    fun testCollectWithNoConfigFiles() {
        localFileConfigSource = LocalFileConfigSource(tempFolder.root.absolutePath, configTextParser)
        
        runBlocking {
            val entries = localFileConfigSource.collect().toList()
            assertTrue(entries.isEmpty())
        }
    }

    @Test
    fun testCollectWithEasyApiConfigFile() {
        val configFile = tempFolder.newFile(".easy.api.config")
        configFile.writeText("server=https://api.example.com")
        
        val parsedEntries = sequenceOf(
            ConfigEntry("server", "https://api.example.com", "local", DirectiveSnapshot())
        )
        
        whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)
        
        localFileConfigSource = LocalFileConfigSource(tempFolder.root.absolutePath, configTextParser)
        
        runBlocking {
            val entries = localFileConfigSource.collect().toList()
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
            ConfigEntry("timeout", "30000", "local", DirectiveSnapshot())
        )
        
        whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)
        
        localFileConfigSource = LocalFileConfigSource(tempFolder.root.absolutePath, configTextParser)
        
        runBlocking {
            val entries = localFileConfigSource.collect().toList()
            assertEquals(1, entries.size)
            assertEquals("timeout", entries[0].key)
        }
    }

    @Test
    fun testCollectWithYamlFile() {
        val configFile = tempFolder.newFile(".easy.api.config.yml")
        configFile.writeText("server: https://api.example.com")
        
        val parsedEntries = sequenceOf(
            ConfigEntry("server", "https://api.example.com", "local", DirectiveSnapshot())
        )
        
        whenever(configTextParser.parse(any(), any(), anyOrNull())).thenReturn(parsedEntries)
        
        localFileConfigSource = LocalFileConfigSource(tempFolder.root.absolutePath, configTextParser)
        
        runBlocking {
            val entries = localFileConfigSource.collect().toList()
            assertEquals(1, entries.size)
        }
    }

    @Test
    fun testSetProjectBasePath() {
        localFileConfigSource = LocalFileConfigSource("/tmp", configTextParser)
        assertEquals("local", localFileConfigSource.sourceId)
        
        localFileConfigSource.setProjectBasePath("/new/path")
        assertEquals("local", localFileConfigSource.sourceId)
    }

    @Test
    fun testCollectWithMultipleConfigFiles() {
        val configFile1 = tempFolder.newFile(".easy.api.config")
        configFile1.writeText("server=https://api.example.com")
        
        val configFile2 = tempFolder.newFile(".easy.api.config.properties")
        configFile2.writeText("timeout=30000")
        
        val parsedEntries1 = sequenceOf(
            ConfigEntry("server", "https://api.example.com", "local", DirectiveSnapshot())
        )
        val parsedEntries2 = sequenceOf(
            ConfigEntry("timeout", "30000", "local", DirectiveSnapshot())
        )
        
        whenever(configTextParser.parse(any(), any(), anyOrNull()))
            .thenReturn(parsedEntries1)
            .thenReturn(parsedEntries2)
        
        localFileConfigSource = LocalFileConfigSource(tempFolder.root.absolutePath, configTextParser)
        
        runBlocking {
            val entries = localFileConfigSource.collect().toList()
            assertEquals(2, entries.size)
        }
    }
}
