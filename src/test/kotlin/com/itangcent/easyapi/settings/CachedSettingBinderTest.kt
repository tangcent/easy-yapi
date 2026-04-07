package com.itangcent.easyapi.settings

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CachedSettingBinderTest {

    private lateinit var delegate: InMemorySettingBinder
    private lateinit var cached: CachedSettingBinder

    @Before
    fun setUp() {
        delegate = InMemorySettingBinder()
        cached = CachedSettingBinder(delegate, 30_000L)
    }

    @Test
    fun testRead_cachesResult() {
        delegate.save(Settings(feignEnable = true))
        val first = cached.read()
        assertTrue(first.feignEnable)

        // Change delegate, cached should still return old value
        delegate.save(Settings(feignEnable = false))
        val second = cached.read()
        assertTrue(second.feignEnable)
    }

    @Test
    fun testRead_expiredCache() {
        delegate.save(Settings(feignEnable = true))
        // Use -1 timeout to guarantee immediate expiry
        val shortCached = CachedSettingBinder(delegate, -1L)
        shortCached.read()

        delegate.save(Settings(feignEnable = false))
        val result = shortCached.read()
        assertFalse(result.feignEnable)
    }

    @Test
    fun testSave_updatesCacheAndDelegate() {
        val settings = Settings(postmanToken = "token-123")
        cached.save(settings)

        assertEquals("token-123", cached.read().postmanToken)
        assertEquals("token-123", delegate.read().postmanToken)
    }

    @Test
    fun testSave_null() {
        cached.save(Settings(feignEnable = true))
        cached.save(null)
        // After saving null, cache is cleared, next read goes to delegate
        assertNull(delegate.tryRead())
    }

    @Test
    fun testTryRead_cachesResult() {
        delegate.save(Settings(httpTimeOut = 99))
        val first = cached.tryRead()
        assertNotNull(first)
        assertEquals(99, first!!.httpTimeOut)

        delegate.save(Settings(httpTimeOut = 1))
        val second = cached.tryRead()
        assertEquals(99, second!!.httpTimeOut)
    }

    @Test
    fun testTryRead_expiredCache() {
        delegate.save(Settings(httpTimeOut = 99))
        val shortCached = CachedSettingBinder(delegate, -1L)
        shortCached.tryRead()

        delegate.save(Settings(httpTimeOut = 1))
        val result = shortCached.tryRead()
        assertEquals(1, result!!.httpTimeOut)
    }

    @Test
    fun testTryRead_returnsNullWhenDelegateReturnsNull() {
        val shortCached = CachedSettingBinder(delegate, 0L)
        assertNull(shortCached.tryRead())
    }

    @Test
    fun testLazyExtension() {
        val binder = delegate.lazy(5000L)
        assertTrue(binder is CachedSettingBinder)
    }

    @Test
    fun testUpdateExtension() {
        delegate.save(Settings())
        delegate.update { postmanToken = "updated" }
        assertEquals("updated", delegate.read().postmanToken)
    }

    /** Simple in-memory SettingBinder for testing */
    private class InMemorySettingBinder : SettingBinder {
        private var settings: Settings? = null

        override fun read(): Settings = settings ?: Settings()
        override fun save(settings: Settings?) { this.settings = settings }
        override fun tryRead(): Settings? = settings
    }
}
