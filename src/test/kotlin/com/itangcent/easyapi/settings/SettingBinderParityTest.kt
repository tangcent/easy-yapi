package com.itangcent.easyapi.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingBinderParityTest {

    @Test
    fun testUpdateAndLazyCache() {
        val binder = InMemoryBinder().lazy()
        binder.update { logLevel = 33 }
        assertEquals(33, binder.read().logLevel)
        binder.save(null)
        assertNull(binder.tryRead())
    }

    private class InMemoryBinder : SettingBinder {
        private var settings: Settings? = Settings()

        override fun read(): Settings = settings ?: Settings()

        override fun save(settings: Settings?) {
            this.settings = settings?.copy()
        }

        override fun tryRead(): Settings? = settings?.copy()
    }
}
