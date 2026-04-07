package com.itangcent.easyapi.recommend

import org.junit.Assert.*
import org.junit.Test

class RecommendPresetRegistryTest {

    @Test
    fun testAllPresets_notEmpty() {
        val presets = RecommendPresetRegistry.allPresets()
        assertNotNull(presets)
        assertTrue("Expected at least one preset", presets.isNotEmpty())
    }

    @Test
    fun testGetPreset_existing() {
        val presets = RecommendPresetRegistry.allPresets()
        if (presets.isNotEmpty()) {
            val firstCode = presets.first().code
            val preset = RecommendPresetRegistry.getPreset(firstCode)
            assertNotNull(preset)
            assertEquals(firstCode, preset!!.code)
        }
    }

    @Test
    fun testGetPreset_nonExisting() {
        val preset = RecommendPresetRegistry.getPreset("non_existing_preset_xyz")
        assertNull(preset)
    }

    @Test
    fun testCodes_returnsAllCodes() {
        val presets = RecommendPresetRegistry.allPresets()
        val codes = RecommendPresetRegistry.codes()
        assertEquals(presets.size, codes.size)
        presets.forEach { preset ->
            assertTrue(codes.contains(preset.code))
        }
    }

    @Test
    fun testDefaultCodes_onlyDefaultEnabled() {
        val defaultCodes = RecommendPresetRegistry.defaultCodes()
        val defaultPresets = RecommendPresetRegistry.allPresets().filter { it.defaultEnabled }
        if (defaultPresets.isEmpty()) {
            assertEquals("", defaultCodes)
        } else {
            val codeSet = defaultCodes.split(",").toSet()
            defaultPresets.forEach { preset ->
                assertTrue("Expected ${preset.code} in default codes", codeSet.contains(preset.code))
            }
        }
    }

    @Test
    fun testBuildRecommendConfig_withSpecificCode() {
        val presets = RecommendPresetRegistry.allPresets()
        if (presets.isNotEmpty()) {
            val preset = presets.first()
            val config = RecommendPresetRegistry.buildRecommendConfig(preset.code)
            if (preset.content.isNotEmpty()) {
                assertTrue(config.contains(preset.content))
            }
        }
    }

    @Test
    fun testBuildRecommendConfig_withEmptyCodes() {
        val config = RecommendPresetRegistry.buildRecommendConfig("")
        assertNotNull(config)
    }

    @Test
    fun testSelectedCodes_withSpecificCode() {
        val presets = RecommendPresetRegistry.allPresets()
        if (presets.isNotEmpty()) {
            val preset = presets.first()
            val selected = RecommendPresetRegistry.selectedCodes(preset.code)
            assertTrue(selected.contains(preset.code))
        }
    }

    @Test
    fun testAddSelectedConfig() {
        val result = RecommendPresetRegistry.addSelectedConfig("spring,mvc", "jaxrs")
        assertTrue(result.contains("spring"))
        assertTrue(result.contains("mvc"))
        assertTrue(result.contains("jaxrs"))
    }

    @Test
    fun testAddSelectedConfig_removesNegation() {
        val result = RecommendPresetRegistry.addSelectedConfig("spring,-jaxrs", "jaxrs")
        assertTrue(result.contains("jaxrs"))
        assertFalse(result.contains("-jaxrs"))
    }

    @Test
    fun testRemoveSelectedConfig() {
        val result = RecommendPresetRegistry.removeSelectedConfig("spring,mvc,jaxrs", "jaxrs")
        assertTrue(result.contains("spring"))
        assertTrue(result.contains("mvc"))
        assertFalse(result.split(",").contains("jaxrs"))
        assertTrue(result.contains("-jaxrs"))
    }

    @Test
    fun testRemoveSelectedConfig_addsNegation() {
        val result = RecommendPresetRegistry.removeSelectedConfig("spring", "jaxrs")
        assertTrue(result.contains("-jaxrs"))
    }

    @Test
    fun testPlaint_returnsRawConfig() {
        val raw = RecommendPresetRegistry.plaint()
        assertNotNull(raw)
    }
}
