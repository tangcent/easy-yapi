package com.itangcent.easyapi.http

import com.itangcent.easyapi.cache.ProjectCacheRepository
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class CookiePersistenceHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var cookiePersistenceHelper: CookiePersistenceHelper

    override fun setUp() {
        super.setUp()
        cookiePersistenceHelper = CookiePersistenceHelper.getInstance(project)
    }

    fun testLoadCookiesWhenEmpty() {
        cookiePersistenceHelper.saveCookies(emptyList())
        val cookies = cookiePersistenceHelper.loadCookies()
        assertNotNull("Load should return non-null list", cookies)
        assertTrue("Load on empty cache should return empty list", cookies.isEmpty())
    }

    fun testSaveAndLoadCookies() {
        val cookies = listOf(
            HttpCookie("session", "abc123", "example.com", "/"),
            HttpCookie("token", "xyz789", "example.com", "/api")
        )
        cookiePersistenceHelper.saveCookies(cookies)

        val loaded = cookiePersistenceHelper.loadCookies()
        assertEquals("Should load same number of cookies", 2, loaded.size)
        assertEquals("First cookie name should match", "session", loaded[0].name)
        assertEquals("First cookie value should match", "abc123", loaded[0].value)
        assertEquals("First cookie domain should match", "example.com", loaded[0].domain)
        assertEquals("Second cookie name should match", "token", loaded[1].name)
    }

    fun testSaveEmptyListAndLoad() {
        cookiePersistenceHelper.saveCookies(emptyList())
        val loaded = cookiePersistenceHelper.loadCookies()
        assertTrue("Loading empty saved list should return empty list", loaded.isEmpty())
    }

    fun testOverwriteCookies() {
        val first = listOf(HttpCookie("old", "value1"))
        val second = listOf(HttpCookie("new", "value2"))

        cookiePersistenceHelper.saveCookies(first)
        cookiePersistenceHelper.saveCookies(second)

        val loaded = cookiePersistenceHelper.loadCookies()
        assertEquals("Should load the most recently saved cookies", 1, loaded.size)
        assertEquals("Cookie name should be from the second save", "new", loaded[0].name)
    }

    fun testCookieWithMinimalFields() {
        val cookies = listOf(HttpCookie("simple", "val"))
        cookiePersistenceHelper.saveCookies(cookies)

        val loaded = cookiePersistenceHelper.loadCookies()
        assertEquals("Should load cookie with minimal fields", 1, loaded.size)
        assertEquals("simple", loaded[0].name)
        assertEquals("val", loaded[0].value)
        assertNull("Domain should be null", loaded[0].domain)
        assertNull("Path should be null", loaded[0].path)
    }
}
