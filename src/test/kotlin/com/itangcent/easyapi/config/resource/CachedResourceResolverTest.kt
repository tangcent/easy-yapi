package com.itangcent.easyapi.config.resource

import com.itangcent.easyapi.cache.CacheService
import com.itangcent.easyapi.logging.IdeaConsole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class CachedResourceResolverTest {

    private lateinit var cacheService: CacheService
    private lateinit var console: IdeaConsole
    private lateinit var resolver: CachedResourceResolver

    @Before
    fun setUp() {
        cacheService = mock(CacheService::class.java)
        console = mock(IdeaConsole::class.java)
    }

    @Test
    fun testGetFromCache() {
        val url = "https://fake-local-test.example/config.txt"
        val cachedContent = "cached content"
        
        `when`(cacheService.getString("remote:$url")).thenReturn(cachedContent)
        `when`(cacheService.getString("remote-ts:$url")).thenReturn(System.currentTimeMillis().toString())
        
        resolver = CachedResourceResolver(cacheService, console, 60000L)
        
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
        
        `when`(cacheService.getString("remote:$url")).thenReturn(cachedContent)
        `when`(cacheService.getString("remote-ts:$url")).thenReturn(oldTimestamp)
        
        resolver = CachedResourceResolver(cacheService, console, 1000L)
        
        runBlocking {
            val result = resolver.get(url)
            assertEquals(cachedContent, result)
        }
    }

    @Test
    fun testGetWithNoCache() {
        val url = "https://fake-local-test.example/config.txt"
        
        `when`(cacheService.getString("remote:$url")).thenReturn(null)
        `when`(cacheService.getString("remote-ts:$url")).thenReturn(null)
        
        resolver = CachedResourceResolver(cacheService, console, 60000L)
        
        runBlocking {
            val result = resolver.get(url)
            assertNull(result)
        }
    }

    @Test
    fun testCacheKey() {
        val url = "https://fake-local-test.example/config.txt"
        val expectedKey = "remote:$url"
        
        `when`(cacheService.getString("remote:$url")).thenReturn(null)
        `when`(cacheService.getString("remote-ts:$url")).thenReturn(null)
        
        resolver = CachedResourceResolver(cacheService, console, 60000L)
        
        runBlocking {
            resolver.get(url)
            verify(cacheService).getString(expectedKey)
        }
    }

    @Test
    fun testCacheTimeKey() {
        val url = "https://fake-local-test.example/config.txt"
        val expectedTimeKey = "remote-ts:$url"
        
        `when`(cacheService.getString("remote:$url")).thenReturn("content")
        `when`(cacheService.getString("remote-ts:$url")).thenReturn(System.currentTimeMillis().toString())
        
        resolver = CachedResourceResolver(cacheService, console, 60000L)
        
        runBlocking {
            resolver.get(url)
            verify(cacheService).getString(expectedTimeKey)
        }
    }

    @Test
    fun testWithNullConsole() {
        val url = "https://fake-local-test.example/config.txt"
        
        `when`(cacheService.getString("remote:$url")).thenReturn(null)
        `when`(cacheService.getString("remote-ts:$url")).thenReturn(null)
        
        resolver = CachedResourceResolver(cacheService, null, 60000L)
        
        runBlocking {
            val result = resolver.get(url)
            assertNull(result)
        }
    }
}
