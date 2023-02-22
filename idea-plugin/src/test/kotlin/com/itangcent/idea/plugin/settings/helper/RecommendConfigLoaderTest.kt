package com.itangcent.idea.plugin.settings.helper

import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test case of [RecommendConfigLoader]
 */
internal class RecommendConfigLoaderTest {

    @Test
    fun testPlaint() {
        assertTrue(RecommendConfigLoader.plaint().contains("module=#module"))
    }

    @Test
    fun testBuildRecommendConfig() {
        assertEquals(
            ResultLoader.load(),
            RecommendConfigLoader.buildRecommendConfig("-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation")
                .toUnixString()
        )
    }

    @Test
    fun testAddSelectedConfig() {
        assertEquals(
            "-deprecated_java,-yapi_tag,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_javax_validation,-support_mock_for_general",
            RecommendConfigLoader.addSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation,import_spring_properties",
                "import_spring_properties"
            )
        )
        assertEquals(
            "-import_spring_properties,-deprecated_java,module,-yapi_tag,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_javax_validation,-support_mock_for_general",
            RecommendConfigLoader.addSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation,import_spring_properties",
                "module"
            )
        )
        assertEquals(
            "-import_spring_properties,support_mock_for_javax_validation,-deprecated_java,-yapi_tag,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_general",
            RecommendConfigLoader.addSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation,import_spring_properties",
                "support_mock_for_javax_validation"
            )
        )

    }

    @Test
    fun testRemoveSelectedConfig() {
        assertEquals(
            "-import_spring_properties,-deprecated_java,-yapi_tag,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_javax_validation,-support_mock_for_general",
            RecommendConfigLoader.removeSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation,import_spring_properties",
                "import_spring_properties"
            )
        )
        assertEquals(
            "-import_spring_properties,-deprecated_java,-yapi_tag,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-module,-converts,-support_mock_for_javax_validation,-support_mock_for_general",
            RecommendConfigLoader.removeSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation,import_spring_properties",
                "module"
            )
        )
        assertEquals(
            "-import_spring_properties,-deprecated_java,-yapi_tag,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_javax_validation,-support_mock_for_general",
            RecommendConfigLoader.removeSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation,import_spring_properties",
                "support_mock_for_javax_validation"
            )
        )

    }

    @Test
    fun testCodes() {
        assertEquals(
            "[module, ignore, deprecated_java, deprecated_kotlin, Jackson, Jackson_JsonIgnoreProperties, Jackson_JsonUnwrapped, Gson, ignore_transient_field, converts, spring_Entity, spring_webflux, spring.validations, spring.ui, jakarta.validation, jakarta.validation(grouped), javax.validation, javax.validation(grouped), is_file, yapi_tag, yapi_tag_kotlin, yapi_status, yapi_mock, yapi_tag, import_spring_properties, resolve_spring_properties, ignore_serialVersionUID, support_mock_for_general, private_protected_field_only, support_mock_for_javax_validation, not_ignore_static_final_field, Jackson_JsonNaming, Jackson_UpperCamelCaseStrategy, Jackson_SnakeCaseStrategy, Jackson_LowerCaseStrategy, Jackson_KebabCaseStrategy, Jackson_LowerDotCaseStrategy, properties, Fastjson, enum_auto_select_field_by_type, enum_use_name, enum_use_ordinal]",
            RecommendConfigLoader.codes().contentToString()
        )

    }

    @Test
    fun testSelectedCodes() {
        assertEquals(
            "[module, ignore, Jackson, Gson, ignore_transient_field, spring.validations, jakarta.validation, is_file, yapi_tag_kotlin, yapi_status, yapi_mock, import_spring_properties, ignore_serialVersionUID, properties, Fastjson, enum_auto_select_field_by_type]",
            RecommendConfigLoader.selectedCodes("-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation,import_spring_properties")
                .contentToString()
        )
    }

    @Test
    fun testDefaultCodes() {
        assertEquals(
            "module,ignore,deprecated_java,deprecated_kotlin,Jackson,Gson,ignore_transient_field,converts,spring_Entity,spring.validations,spring.ui,jakarta.validation,javax.validation,is_file,yapi_tag,yapi_tag_kotlin,yapi_status,yapi_mock,yapi_tag,ignore_serialVersionUID,support_mock_for_general,support_mock_for_javax_validation,properties,Fastjson,enum_auto_select_field_by_type",
            RecommendConfigLoader.defaultCodes()
        )
    }

    @Test
    fun testGet() {
        assertEquals(
            "#Get the module from the comment,group the apis\n" +
                    "module=#module", RecommendConfigLoader["module"]?.toUnixString()
        )
        assertEquals("module", RecommendConfigLoader[0])
    }
}