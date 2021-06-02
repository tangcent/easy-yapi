package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.intellij.psi.JsonOption
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [IntelligentSettingsHelper]
 */
internal class IntelligentSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var intelligentSettingsHelper: IntelligentSettingsHelper

    @Test
    fun testFormExpanded() {
        settings.formExpanded = false
        assertFalse(intelligentSettingsHelper.formExpanded())
        settings.formExpanded = true
        assertTrue(intelligentSettingsHelper.formExpanded())
    }
    
    @Test
    fun testQueryExpanded() {
        settings.queryExpanded = false
        assertFalse(intelligentSettingsHelper.queryExpanded())
        settings.queryExpanded = true
        assertTrue(intelligentSettingsHelper.queryExpanded())
    }

    @Test
    fun testReadGetter() {
        settings.readGetter = false
        assertFalse(intelligentSettingsHelper.readGetter())
        settings.readGetter = true
        assertTrue(intelligentSettingsHelper.readGetter())
    }

    @Test
    fun testReadSetter() {
        settings.readSetter = false
        assertFalse(intelligentSettingsHelper.readSetter())
        settings.readSetter = true
        assertTrue(intelligentSettingsHelper.readSetter())
    }

    @Test
    fun testInferEnable() {
        settings.inferEnable = false
        assertFalse(intelligentSettingsHelper.inferEnable())
        settings.inferEnable = true
        assertTrue(intelligentSettingsHelper.inferEnable())
    }

    @Test
    fun testInferMaxDeep() {
        settings.inferMaxDeep = 1
        assertEquals(1, intelligentSettingsHelper.inferMaxDeep())
        settings.inferMaxDeep = 10
        assertEquals(10, intelligentSettingsHelper.inferMaxDeep())
        settings.inferMaxDeep = 100
        assertEquals(100, intelligentSettingsHelper.inferMaxDeep())
    }

    @Test
    fun testJsonOptionForInput() {
        settings.readSetter = false
        assertEquals(JsonOption.NONE, intelligentSettingsHelper.jsonOptionForInput(JsonOption.NONE))
        settings.readSetter = true
        assertEquals(JsonOption.READ_SETTER, intelligentSettingsHelper.jsonOptionForInput(JsonOption.NONE))
    }

    @Test
    fun testJsonOptionForOutput() {
        settings.readGetter = false
        assertEquals(JsonOption.NONE, intelligentSettingsHelper.jsonOptionForOutput(JsonOption.NONE))
        settings.readGetter = true
        assertEquals(JsonOption.READ_GETTER, intelligentSettingsHelper.jsonOptionForOutput(JsonOption.NONE))

    }
}