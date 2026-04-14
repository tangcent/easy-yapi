package com.itangcent.easyapi.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class SettingBinderParityTest {

    @Test
    fun testUpdateAndLazyCache() {
        val binder = InMemoryBinder().lazy(1000.milliseconds)
        binder.update { logLevel = 33 }
        assertEquals(33, binder.read().logLevel)
        assertEquals(33, binder.tryRead()?.logLevel)
    }

    @Test
    fun testCacheExpiresAfterTimeout() {
        val delegate = InMemoryBinder()
        val binder = CachedSettingBinder(delegate, 100_000.milliseconds)

        delegate.settings = Settings().apply { logLevel = 42 }
        binder.read()
        binder.read()
        binder.read()

        assertEquals(1, delegate.readCount)
    }

    @Test
    fun testCacheRefreshesOnTimeout() {
        val delegate = InMemoryBinder()
        val binder = CachedSettingBinder(delegate, 50.milliseconds)

        delegate.settings = Settings().apply { logLevel = 1 }
        binder.read()
        val countBeforeTimeout = delegate.readCount

        Thread.sleep(60)
        binder.read()
        assertEquals(countBeforeTimeout + 1, delegate.readCount)
    }

    @Test
    fun testSaveRefreshesCacheTimestamp() {
        val delegate = InMemoryBinder()
        val binder = CachedSettingBinder(delegate, 10_000.milliseconds)

        delegate.settings = Settings().apply { logLevel = 1 }
        assertEquals(1, binder.read().logLevel)

        delegate.settings = Settings().apply { logLevel = 2 }
        assertEquals(1, binder.read().logLevel)

        binder.save(Settings().apply { logLevel = 3 })

        delegate.settings = Settings().apply { logLevel = 4 }
        assertEquals(3, binder.read().logLevel)

        delegate.settings = Settings().apply { logLevel = 5 }
        assertEquals(3, binder.read().logLevel)
    }

    private class InMemoryBinder : SettingBinder {
        var settings: Settings? = null
        var readCount: Int = 0

        override fun read(): Settings {
            readCount++
            return settings?.copy() ?: Settings()
        }

        override fun save(settings: Settings) {
            this.settings = settings.copy()
        }

        override fun tryRead(): Settings? = settings?.copy()
    }
}
