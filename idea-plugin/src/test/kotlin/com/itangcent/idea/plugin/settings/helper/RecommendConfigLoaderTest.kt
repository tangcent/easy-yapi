package com.itangcent.idea.plugin.settings.helper

import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import org.junit.jupiter.api.Assertions.assertArrayEquals
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
            RecommendConfigLoader.buildRecommendConfig("-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation")
                .toUnixString()
        )
    }

    @Test
    fun testAddSelectedConfig() {
        assertEquals(
            "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties",
            RecommendConfigLoader.addSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties",
                "import_spring_properties"
            )
        )
        assertEquals(
            "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties,module",
            RecommendConfigLoader.addSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties",
                "module"
            )
        )
        assertEquals(
            "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),import_spring_properties,support_mock_for_javax_validation",
            RecommendConfigLoader.addSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties",
                "support_mock_for_javax_validation"
            )
        )

    }

    @Test
    fun testRemoveSelectedConfig() {
        assertEquals(
            "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation",
            RecommendConfigLoader.removeSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties",
                "import_spring_properties"
            )
        )
        assertEquals(
            "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties,-module",
            RecommendConfigLoader.removeSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties",
                "module"
            )
        )
        assertEquals(
            "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties",
            RecommendConfigLoader.removeSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties",
                "support_mock_for_javax_validation"
            )
        )

    }

    @Test
    fun testCodes() {
        assertArrayEquals(
            arrayOf(
                "module",
                "ignore",
                "deprecated_java",
                "deprecated_kotlin",
                "not_ignore_irregular_api_method",
                "Jackson",
                "Jackson_JsonPropertyOrder",
                "Jackson_JsonIgnoreProperties",
                "Jackson_JsonUnwrapped",
                "Jackson_JsonView",
                "Gson",
                "ignore_transient_field",
                "converts",
                "spring_Entity",
                "spring_webflux",
                "spring.validations",
                "spring.ui",
                "jakarta.validation",
                "jakarta.validation(strict)",
                "javax.validation",
                "javax.validation(strict)",
                "is_file",
                "yapi_tag",
                "yapi_tag_kotlin",
                "yapi_status",
                "yapi_mock",
                "yapi_tag",
                "import_spring_properties",
                "resolve_spring_properties",
                "ignore_serialVersionUID",
                "support_mock_for_general",
                "private_protected_field_only",
                "support_mock_for_javax_validation",
                "not_ignore_static_final_field",
                "Jackson_JsonNaming",
                "Jackson_UpperCamelCaseStrategy",
                "Jackson_SnakeCaseStrategy",
                "Jackson_LowerCaseStrategy",
                "Jackson_KebabCaseStrategy",
                "Jackson_LowerDotCaseStrategy",
                "properties",
                "Fastjson",
                "enum_auto_select_field_by_type",
                "enum_use_name",
                "enum_use_ordinal",
                "ignore_some_common_classes",
                "field_order",
                "field_order_child_first",
                "field_order_parent_first",
                "field_order_alphabetically",
                "field_order_alphabetically_descending"
            ),
            RecommendConfigLoader.codes()
        )

    }

    @Test
    fun testSelectedCodes() {
        assertEquals(
            "[module, ignore, Jackson, Gson, ignore_transient_field, spring.validations, jakarta.validation, is_file, yapi_tag_kotlin, yapi_status, yapi_mock, import_spring_properties, ignore_serialVersionUID, properties, Fastjson, enum_auto_select_field_by_type, ignore_some_common_classes]",
            RecommendConfigLoader.selectedCodes("-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(strict),-support_mock_for_javax_validation,import_spring_properties")
                .contentToString()
        )
    }

    @Test
    fun testDefaultCodes() {
        assertEquals(
            "module,ignore,deprecated_java,deprecated_kotlin,Jackson,Gson,ignore_transient_field,converts,spring_Entity,spring.validations,spring.ui,jakarta.validation,javax.validation,is_file,yapi_tag,yapi_tag_kotlin,yapi_status,yapi_mock,yapi_tag,ignore_serialVersionUID,support_mock_for_general,support_mock_for_javax_validation,properties,Fastjson,enum_auto_select_field_by_type,ignore_some_common_classes",
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