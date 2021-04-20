package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.mock.toUnixString
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
        settings.recommendConfigs = "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation"
        assertEquals("#Get the module from the comment,group the apis\n" +
                "module=#module\n" +
                "#Ignore class/api\n" +
                "ignore=#ignore\n" +
                "#Support for Jackson annotations\n" +
                "field.name=@com.fasterxml.jackson.annotation.JsonProperty#value\n" +
                "field.ignore=@com.fasterxml.jackson.annotation.JsonIgnore#value\n" +
                "#Support for Gson annotations\n" +
                "field.name=@com.google.gson.annotations.SerializedName#value\n" +
                "field.ignore=!@com.google.gson.annotations.Expose#serialize\n" +
                "#ignore transient field\n" +
                "field.ignore=groovy:it.hasModifier(\"transient\")\n" +
                "#Support spring.validations\n" +
                "field.required=@org.springframework.lang.NonNull\n" +
                "param.ignore=groovy:it.type().isExtend(\"org.springframework.validation.BindingResult\")\n" +
                "#Support spring file\n" +
                "type.is_file=groovy:it.isExtend(\"org.springframework.web.multipart.MultipartFile\")\n" +
                "#yapi tag for kotlin\n" +
                "api.tag[@kotlin.Deprecated]=deprecated\n" +
                "api.tag[groovy:it.containingClass().hasAnn(\"kotlin.Deprecated\")]=deprecated\n" +
                "#yapi status\n" +
                "api.status[#undone]=undone\n" +
                "api.status[#todo]=undone\n" +
                "#yapi mock\n" +
                "field.mock=#mock\n" +
                "#ignore serialVersionUID\n" +
                "constant.field.ignore=groovy:it.name()==\"serialVersionUID\"\n" +
                "#support the common ways to use enum\n" +
                "enum.use.name=groovy:it.type().name()==\"java.lang.String\"\n" +
                "enum.use.ordinal=groovy:it.type().name()==\"int\"\n" +
                "enum.use.ordinal=groovy:it.type().name()==\"java.lang.Integer\"", recommendConfigSettingsHelper.loadRecommendConfig().toUnixString())
    }
}