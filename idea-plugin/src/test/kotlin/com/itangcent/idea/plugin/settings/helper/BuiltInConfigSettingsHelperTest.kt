package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Test case of [BuiltInConfigSettingsHelper]
 */
internal class BuiltInConfigSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var builtInConfigSettingsHelper: BuiltInConfigSettingsHelper

    @Test
    fun testBuiltInConfig() {
        settings.builtInConfig = "hello world"
        assertEquals("hello world", builtInConfigSettingsHelper.builtInConfig())
        settings.builtInConfig = "test-demo"
        assertEquals("test-demo", builtInConfigSettingsHelper.builtInConfig())
    }
}