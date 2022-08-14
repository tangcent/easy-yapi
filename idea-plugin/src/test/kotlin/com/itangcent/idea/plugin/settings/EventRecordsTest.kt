package com.itangcent.idea.plugin.settings

import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [EventRecords]
 */
internal class EventRecordsTest : PluginContextLightCodeInsightFixtureTestCase() {

    fun testRecord() {
        assertEquals(EventRecords.getRecord("com.itangcent.test.record"), 0)
        assertEquals(EventRecords.record("com.itangcent.test.record"), 1)
        assertEquals(EventRecords.getRecord("com.itangcent.test.record"), 1)
        assertEquals(EventRecords.record("com.itangcent.test.record"), 2)
        assertEquals(EventRecords.getRecord("com.itangcent.test.record"), 2)
    }
}