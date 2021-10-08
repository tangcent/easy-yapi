package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [RecommendConfigSettingsHelper]
 */
internal class RecommendConfigSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var recommendConfigSettingsHelper: RecommendConfigSettingsHelper

    @Test
    fun testUseRecommendConfig() {
        settings.useRecommendConfig = false
        assertFalse(recommendConfigSettingsHelper.useRecommendConfig())
        settings.useRecommendConfig = true
        assertTrue(recommendConfigSettingsHelper.useRecommendConfig())
    }

    @Test
    fun testLoadRecommendConfig() {
        settings.recommendConfigs =
            "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation"
        assertEquals(ResultLoader.load(), recommendConfigSettingsHelper.loadRecommendConfig().toUnixString())
    }
}