package com.itangcent.cache

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Test for [CacheSwitcher]
 *
 * @author tangcent
 */
class CacheSwitcherTest {

    // This class implements the CacheSwitcher interface and provides an getObject() function that returns a cached object if caching is enabled, and a new object if caching is disabled.
    class TestCacheSwitcher : CacheSwitcher {
        // This variable holds the cached object, if caching is enabled.
        private var cachedObject: Any? = null

        // This variable tracks whether caching is currently enabled or disabled.
        var cachingEnabled = true

        // This function returns the cached object, if caching is enabled.
        // Otherwise, it returns a new object and caches it.
        fun getObject(): Any {
            return if (cachingEnabled && cachedObject != null) {
                cachedObject!!
            } else {
                val newObject = Any()
                cachedObject = newObject
                newObject
            }
        }

        override fun notUserCache() {
            // Disable caching.
            cachingEnabled = false
        }

        override fun userCache() {
            // Enable caching.
            cachingEnabled = true
        }
    }

    @Test
    fun `notUserCache disables caching for non-user data`() {
        // Create a new TestCacheSwitcher object with caching enabled.
        val cacheSwitcher = TestCacheSwitcher()
        cacheSwitcher.userCache()

        // Call notUserCache() to disable caching.
        cacheSwitcher.notUserCache()

        // Call getObject() twice and verify that different objects are returned each time.
        val obj1 = cacheSwitcher.getObject()
        val obj2 = cacheSwitcher.getObject()
        assertNotEquals(obj1, obj2)
    }

    @Test
    fun `userCache enables caching for data that was previously disabled`() {
        // Create a new TestCacheSwitcher object with caching disabled.
        val cacheSwitcher = TestCacheSwitcher()
        cacheSwitcher.notUserCache()

        // Call userCache() to enable caching.
        cacheSwitcher.userCache()

        // Call getObject() twice and verify that the same object is returned both times.
        val obj1 = cacheSwitcher.getObject()
        val obj2 = cacheSwitcher.getObject()
        assertSame(obj1, obj2)
    }

    @Test
    fun `withoutCache prevents caching for the duration of the call`() {
        // Create a new TestCacheSwitcher object with caching enabled.
        val cacheSwitcher = TestCacheSwitcher()
        cacheSwitcher.userCache()

        // Call the withoutCache() extension function and verify that caching is disabled for the duration of the call.
        cacheSwitcher.withoutCache {
            // Verify that caching is currently disabled.
            assertFalse(cacheSwitcher.cachingEnabled)

            // Call getObject() twice and verify that different objects are returned each time.
            val obj1 = cacheSwitcher.getObject()
            val obj2 = cacheSwitcher.getObject()
            assertNotEquals(obj1, obj2)
        }

        // Verify that caching is currently enabled.
        assertTrue(cacheSwitcher.cachingEnabled)

        // Call getObject() twice and verify that the same object is returned both times.
        val obj1 = cacheSwitcher.getObject()
        val obj2 = cacheSwitcher.getObject()
        assertSame(obj1, obj2)
    }
}