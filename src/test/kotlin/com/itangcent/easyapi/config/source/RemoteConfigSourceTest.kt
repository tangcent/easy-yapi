package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.config.parser.DirectiveSnapshot
import com.itangcent.easyapi.config.resource.CachedResourceResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RemoteConfigSourceTest {

    private lateinit var cachedResourceResolver: CachedResourceResolver
    private lateinit var configTextParser: ConfigTextParser
    private lateinit var remoteConfigSource: RemoteConfigSource

    @Before
    fun setUp() {
        cachedResourceResolver = mock()
        configTextParser = mock()
    }

    @Test
    fun testPriority() {
        remoteConfigSource = RemoteConfigSource(emptyList(), configTextParser, cachedResourceResolver)
        assertEquals(3, remoteConfigSource.priority)
    }

    @Test
    fun testSourceId() {
        remoteConfigSource = RemoteConfigSource(emptyList(), configTextParser, cachedResourceResolver)
        assertEquals("remote", remoteConfigSource.sourceId)
    }

    @Test
    fun testCollectWithEmptyUrls() {
        remoteConfigSource = RemoteConfigSource(emptyList(), configTextParser, cachedResourceResolver)
        
        runBlocking {
            val entries = remoteConfigSource.collect().toList()
            assertTrue(entries.isEmpty())
        }
    }

    @Test
    fun testCollectWithSingleUrl() {
        val url = "https://fake-local-test.example/config.txt"
        val content = "server=https://api.example.com"
        val parsedEntries = sequenceOf(
            ConfigEntry("server", "https://api.example.com", "remote", DirectiveSnapshot())
        )
        
        runBlocking {
            whenever(cachedResourceResolver.get(url)).thenReturn(content)
            whenever(configTextParser.parse(content, "remote", "https://fake-local-test.example")).thenReturn(parsedEntries)
        }
        
        remoteConfigSource = RemoteConfigSource(listOf(url), configTextParser, cachedResourceResolver)
        
        runBlocking {
            val entries = remoteConfigSource.collect().toList()
            assertEquals(1, entries.size)
            assertEquals("server", entries[0].key)
            assertEquals("https://api.example.com", entries[0].value)
        }
    }

    @Test
    fun testCollectWithMultipleUrls() {
        val url1 = "https://fake-local-test.example/config1.txt"
        val url2 = "https://fake-local-test.example/config2.txt"
        val content1 = "server=https://api1.example.com"
        val content2 = "token=abc123"
        
        runBlocking {
            whenever(cachedResourceResolver.get(url1)).thenReturn(content1)
            whenever(cachedResourceResolver.get(url2)).thenReturn(content2)
            whenever(configTextParser.parse(content1, "remote", "https://fake-local-test.example")).thenReturn(
                sequenceOf(ConfigEntry("server", "https://api1.example.com", "remote", DirectiveSnapshot()))
            )
            whenever(configTextParser.parse(content2, "remote", "https://fake-local-test.example")).thenReturn(
                sequenceOf(ConfigEntry("token", "abc123", "remote", DirectiveSnapshot()))
            )
        }
        
        remoteConfigSource = RemoteConfigSource(listOf(url1, url2), configTextParser, cachedResourceResolver)
        
        runBlocking {
            val entries = remoteConfigSource.collect().toList()
            assertEquals(2, entries.size)
        }
    }

    @Test
    fun testCollectWithNullContent() {
        val url = "https://fake-local-test.example/config.txt"
        
        runBlocking {
            whenever(cachedResourceResolver.get(url)).thenReturn(null)
        }
        
        remoteConfigSource = RemoteConfigSource(listOf(url), configTextParser, cachedResourceResolver)
        
        runBlocking {
            val entries = remoteConfigSource.collect().toList()
            assertTrue(entries.isEmpty())
            verify(configTextParser, never()).parse(any(), any(), any())
        }
    }

    @Test
    fun testCollectWithMixedNullContent() {
        val url1 = "https://fake-local-test.example/config1.txt"
        val url2 = "https://fake-local-test.example/config2.txt"
        val content2 = "token=abc123"
        
        runBlocking {
            whenever(cachedResourceResolver.get(url1)).thenReturn(null)
            whenever(cachedResourceResolver.get(url2)).thenReturn(content2)
            whenever(configTextParser.parse(content2, "remote", "https://fake-local-test.example")).thenReturn(
                sequenceOf(ConfigEntry("token", "abc123", "remote", DirectiveSnapshot()))
            )
        }
        
        remoteConfigSource = RemoteConfigSource(listOf(url1, url2), configTextParser, cachedResourceResolver)
        
        runBlocking {
            val entries = remoteConfigSource.collect().toList()
            assertEquals(1, entries.size)
            assertEquals("token", entries[0].key)
        }
    }
}
