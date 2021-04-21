package com.itangcent.idea.plugin.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Test case of [Settings]
 */
internal class SettingsTest {

    @Test
    fun testCEH() {
        val settings = Settings()
        settings.pullNewestDataBefore = true
        settings.yapiServer = "http://127.0.0.1"

        val copySettings = settings.copy()
        assertEquals(settings, copySettings)
        assertEquals(settings.hashCode(), copySettings.hashCode())
        settings.pullNewestDataBefore = false
        assertNotEquals(settings.hashCode(), copySettings.hashCode())
    }
}