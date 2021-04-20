package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [SupportSettingsHelper]
 */
internal class SupportSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var supportSettingsHelper: SupportSettingsHelper

    @Test
    fun testMethodDocEnable() {
        settings.methodDocEnable = true
        assertTrue(supportSettingsHelper.methodDocEnable())
        settings.methodDocEnable = false
        assertFalse(supportSettingsHelper.methodDocEnable())
    }
}