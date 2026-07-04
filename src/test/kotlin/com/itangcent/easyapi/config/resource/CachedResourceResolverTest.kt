package com.itangcent.easyapi.config.resource

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.module.HttpSettings
import com.itangcent.easyapi.testFramework.ConstantSettingBinder
import com.itangcent.easyapi.util.storage.LocalStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CachedResourceResolverTest {

    private lateinit var project: Project
    private lateinit var localStorage: LocalStorage
    private lateinit var console: IdeaConsole
    private lateinit var resolver: CachedResourceResolver

    @Before
    fun setUp() {
        project = mock()
        localStorage = mock(LocalStorage::class.java)
        console = mock(IdeaConsole::class.java)

        val ideaConsoleProvider = mock<IdeaConsoleProvider>()
        whenever(ideaConsoleProvider.getConsole()).thenReturn(console)

        val settingBinder = ConstantSettingBinder()
        settingBinder.save(HttpSettings(httpTimeOut = 60))

        whenever(project.getService(LocalStorage::class.java)).thenReturn(localStorage)
        whenever(project.getService(IdeaConsoleProvider::class.java)).thenReturn(ideaConsoleProvider)
        whenever(project.getService(SettingBinder::class.java)).thenReturn(settingBinder)
    }

    @Test
    fun testGetFromCache() {
        val url = "https://fake-local-test.example/config.txt"
        val cachedContent = "cached content"

        `when`(localStorage.get("remote-cache", url)).thenReturn(cachedContent)
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(System.currentTimeMillis().toString())

        resolver = CachedResourceResolver(project)

        runBlocking {
            val result = resolver.get(url)
            assertEquals(cachedContent, result)
        }
    }

    @Test
    fun testGetWithExpiredCache() {
        val url = "https://fake-local-test.example/config.txt"
        val cachedContent = "cached content"
        // TTL is 2 hours; use a timestamp 3 hours ago to ensure expiry
        val oldTimestamp = (System.currentTimeMillis() - 3 * 60 * 60 * 1000L).toString()

        `when`(localStorage.get("remote-cache", url)).thenReturn(cachedContent)
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(oldTimestamp)

        resolver = CachedResourceResolver(project)

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

        resolver = CachedResourceResolver(project)

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

        resolver = CachedResourceResolver(project)

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

        resolver = CachedResourceResolver(project)

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

        resolver = CachedResourceResolver(project)

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
        // TTL is 2 hours; use a timestamp 3 hours ago to ensure expiry
        val oldTimestamp = (System.currentTimeMillis() - 3 * 60 * 60 * 1000L).toString()

        `when`(localStorage.get("remote-cache", url)).thenReturn(staleContent)
        `when`(localStorage.get("remote-cache-ts", url)).thenReturn(oldTimestamp)

        resolver = CachedResourceResolver(project)

        runBlocking {
            val result = resolver.get(url)
            assertEquals("Should fall back to stale cache on fetch failure", staleContent, result)
            verify(console).warn(anyString(), any())
        }
    }
}
