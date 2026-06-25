package com.itangcent.easyapi.config.resource

import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.util.storage.LocalStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

class CachedResourceResolverTest {

    private lateinit var localStorage: LocalStorage
    private lateinit var console: IdeaConsole
    private lateinit var resolver: CachedResourceResolver

    @Before
    fun setUp() {
        localStorage = mock(LocalStorage::class.java)
        console = mock(IdeaConsole::class.java)
    }

    @Test
    fun testGetFromCache() {
        val url = "https://fake-local-test.example/config.txt"
        val cachedContent = "cached content"

        `when`(localStorage.get("remote-cache", url)).thenReturn(cachedContent)
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(System.currentTimeMillis().toString())

        resolver = CachedResourceResolver(localStorage, console, 60000L)

        runBlocking {
            val result = resolver.get(url)
            assertEquals(cachedContent, result)
        }
    }

    @Test
    fun testGetWithExpiredCache() {
        val url = "https://fake-local-test.example/config.txt"
        val cachedContent = "cached content"
        val oldTimestamp = (System.currentTimeMillis() - 100000).toString()

        `when`(localStorage.get("remote-cache", url)).thenReturn(cachedContent)
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(oldTimestamp)

        resolver = CachedResourceResolver(localStorage, console, 1000L)

        runBlocking {
            val result = resolver.get(url)
            assertEquals(cachedContent, result)
        }
    }

    @Test
    fun testGetWithNoCache() {
        val url = "https://fake-local-test.example/config.txt"

        `when`(localStorage.get("remote-cache", url)).thenReturn(null)
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(null)

        resolver = CachedResourceResolver(localStorage, console, 60000L)

        runBlocking {
            val result = resolver.get(url)
            assertNull(result)
        }
    }

    @Test
    fun testCacheGroup() {
        val url = "https://fake-local-test.example/config.txt"

        `when`(localStorage.get("remote-cache", url)).thenReturn(null)
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(null)

        resolver = CachedResourceResolver(localStorage, console, 60000L)

        runBlocking {
            resolver.get(url)
            verify(localStorage).get("remote-cache", url)
        }
    }

    @Test
    fun testCacheTimestampGroup() {
        val url = "https://fake-local-test.example/config.txt"

        `when`(localStorage.get("remote-cache", url)).thenReturn("content")
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(System.currentTimeMillis().toString())

        resolver = CachedResourceResolver(localStorage, console, 60000L)

        runBlocking {
            resolver.get(url)
            verify(localStorage).get("remote-cache-ts", url)
        }
    }

    @Test
    fun testFetchFailureLogsWarningToConsole() {
        val url = "https://fake-local-test.example/config.txt"

        `when`(localStorage.get("remote-cache", url)).thenReturn(null)
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(null)

        resolver = CachedResourceResolver(localStorage, console, 60000L)

        runBlocking {
            val result = resolver.get(url)
            assertNull("Should return null when fetch fails and no cache", result)
            // The fetch failure (non-timeout) should be logged via console.warn
            verify(console).warn(anyString(), any())
        }
    }

    @Test
    fun testFetchFailureWithExpiredCacheReturnsStaleContent() {
        val url = "https://fake-local-test.example/config.txt"
        val staleContent = "stale content"
        val oldTimestamp = (System.currentTimeMillis() - 100000).toString()

        `when`(localStorage.get("remote-cache", url)).thenReturn(staleContent)
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(oldTimestamp)

        resolver = CachedResourceResolver(localStorage, console, 1000L)

        runBlocking {
            val result = resolver.get(url)
            assertEquals("Should fall back to stale cache on fetch failure", staleContent, result)
            verify(console).warn(anyString(), any())
        }
    }
}
