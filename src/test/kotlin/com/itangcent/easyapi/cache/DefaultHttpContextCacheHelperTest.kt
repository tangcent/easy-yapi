package com.itangcent.easyapi.cache

import com.itangcent.easyapi.http.HttpCookie
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class DefaultHttpContextCacheHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var cacheHelper: DefaultHttpContextCacheHelper
    private lateinit var cacheRepository: ProjectCacheRepository

    override fun setUp() {
        super.setUp()
        cacheRepository = ProjectCacheRepository.getInstance(project)
        cacheRepository.clear()
        cacheHelper = DefaultHttpContextCacheHelper.getInstance(project)
    }

    override fun tearDown() {
        cacheRepository.clear()
        super.tearDown()
    }

    fun testGetInstance() {
        assertNotNull(cacheHelper)
        assertSame(cacheHelper, DefaultHttpContextCacheHelper.getInstance(project))
    }

    fun testGetHostsDefault() {
        val hosts = cacheHelper.getHosts()
        assertEquals(1, hosts.size)
        assertEquals("http://localhost:8080", hosts.first())
    }

    fun testAddHost() {
        cacheHelper.addHost("http://api.example.com")
        val hosts = cacheHelper.getHosts()
        assertTrue(hosts.contains("http://api.example.com"))
        assertEquals("http://api.example.com", hosts.first())
    }

    fun testAddHostMovesToTop() {
        cacheHelper.addHost("http://api1.example.com")
        cacheHelper.addHost("http://api2.example.com")
        cacheHelper.addHost("http://api1.example.com")
        
        val hosts = cacheHelper.getHosts()
        assertEquals("http://api1.example.com", hosts.first())
    }

    fun testAddHostDeduplicatesCaseInsensitive() {
        cacheHelper.addHost("http://API.EXAMPLE.COM")
        cacheHelper.addHost("http://api.example.com")
        
        val hosts = cacheHelper.getHosts()
        assertEquals(1, hosts.count { it.equals("http://api.example.com", ignoreCase = true) })
    }

    fun testAddHostMaxLimit() {
        for (i in 1..15) {
            cacheHelper.addHost("http://api$i.example.com")
        }
        
        val hosts = cacheHelper.getHosts()
        assertTrue(hosts.size <= 10)
    }

    fun testAddEmptyHost() {
        val initialHosts = cacheHelper.getHosts()
        cacheHelper.addHost("")
        assertEquals(initialHosts, cacheHelper.getHosts())
    }

    fun testAddBlankHost() {
        val initialHosts = cacheHelper.getHosts()
        cacheHelper.addHost("   ")
        assertEquals(initialHosts, cacheHelper.getHosts())
    }

    fun testGetCookiesEmpty() {
        val cookies = cacheHelper.getCookies()
        assertTrue(cookies.isEmpty())
    }

    fun testAddCookies() {
        val cookies = listOf(
            HttpCookie("session", "abc123", "example.com", "/")
        )
        cacheHelper.addCookies(cookies)
        
        val storedCookies = cacheHelper.getCookies()
        assertEquals(1, storedCookies.size)
        assertEquals("session", storedCookies.first().name)
        assertEquals("abc123", storedCookies.first().value)
    }

    fun testAddMultipleCookies() {
        val cookies = listOf(
            HttpCookie("session", "abc123", "example.com", "/"),
            HttpCookie("token", "xyz789", "example.com", "/")
        )
        cacheHelper.addCookies(cookies)
        
        val storedCookies = cacheHelper.getCookies()
        assertEquals(2, storedCookies.size)
    }

    fun testUpdateExistingCookie() {
        cacheHelper.addCookies(listOf(
            HttpCookie("session", "old", "example.com", "/")
        ))
        
        cacheHelper.addCookies(listOf(
            HttpCookie("session", "new", "example.com", "/")
        ))
        
        val storedCookies = cacheHelper.getCookies()
        assertEquals(1, storedCookies.size)
        assertEquals("new", storedCookies.first().value)
    }

    fun testCookiesWithDifferentDomains() {
        cacheHelper.addCookies(listOf(
            HttpCookie("session", "value1", "example.com", "/"),
            HttpCookie("session", "value2", "other.com", "/")
        ))
        
        val storedCookies = cacheHelper.getCookies()
        assertEquals(2, storedCookies.size)
    }

    fun testCookiesWithDifferentPaths() {
        cacheHelper.addCookies(listOf(
            HttpCookie("session", "value1", "example.com", "/api"),
            HttpCookie("session", "value2", "example.com", "/web")
        ))
        
        val storedCookies = cacheHelper.getCookies()
        assertEquals(1, storedCookies.size)
    }
}
