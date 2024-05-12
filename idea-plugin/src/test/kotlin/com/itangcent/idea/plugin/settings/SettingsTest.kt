package com.itangcent.idea.plugin.settings

import org.junit.jupiter.api.Test

/**
 * Test case of [Settings]
 */
internal class SettingsTest {

    @Test
    fun testCETH() {
        val original = Settings()
        original.pullNewestDataBefore = true
        original.yapiServer = "http://127.0.0.1"

        ETHUtils.testCETH(original) { copy() }
    }
}