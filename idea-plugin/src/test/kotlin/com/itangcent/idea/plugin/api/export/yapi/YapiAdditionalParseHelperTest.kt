package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.idea.plugin.api.export.core.AdditionalParseHelper
import com.itangcent.idea.plugin.api.export.core.AdditionalParseHelperTest
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test case with [YapiAdditionalParseHelper]
 */
internal class YapiAdditionalParseHelperTest : AdditionalParseHelperTest() {

    override val additionalParseHelperClass: KClass<out AdditionalParseHelper>
        get() = YapiAdditionalParseHelper::class

    @Test
    fun parseHeaderFromJson() {
        val header =
            additionalParseHelper.parseHeaderFromJson("{name: \"Authorization\",value: \"123h\",desc: \"Token in header\",required:true, demo:\"321h\"}")
        assertNotNull(header)
        assertEquals("Authorization", header.name)
        assertEquals("123h", header.value)
        assertEquals("Token in header", header.desc)
        assertEquals(true, header.required)
        assertEquals("321h", header.getExample())
    }

    @Test
    fun parseParamFromJson() {
        val param =
            additionalParseHelper.parseParamFromJson("{name: \"Authorization\",value: \"123p\",desc: \"Token in param\",required:true, demo:\"321p\"}")
        assertNotNull(param)
        assertEquals("Authorization", param.name)
        assertEquals("123p", param.value)
        assertEquals("Token in param", param.desc)
        assertEquals(true, param.required)
        assertEquals("321p", param.getExample())
    }
}