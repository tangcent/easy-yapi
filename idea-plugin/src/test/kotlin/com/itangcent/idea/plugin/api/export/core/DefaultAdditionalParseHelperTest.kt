package com.itangcent.idea.plugin.api.export.core

import org.junit.jupiter.api.Test
import kotlin.reflect.KClass
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Test case with [DefaultAdditionalParseHelper]
 */
internal class DefaultAdditionalParseHelperTest : AdditionalParseHelperTest() {

    override val additionalParseHelperClass: KClass<out AdditionalParseHelper>
        get() = DefaultAdditionalParseHelper::class

    @Test
    fun parseHeaderFromJson() {
        val header =
            additionalParseHelper.parseHeaderFromJson("{name: \"Authorization\",value: \"123h\",desc: \"Token in header\",required:true, demo:\"\"}")
        assertNotNull(header)
        assertEquals("Authorization", header.name)
        assertEquals("123h", header.value)
        assertEquals("Token in header", header.desc)
        assertEquals(true, header.required)

    }

    @Test
    fun parseParamFromJson() {
        val param =
            additionalParseHelper.parseParamFromJson("{name: \"Authorization\",value: \"123p\",desc: \"Token in param\",required:true, demo:\"\"}")
        assertNotNull(param)
        assertEquals("Authorization", param.name)
        assertEquals("123p", param.value)
        assertEquals("Token in param", param.desc)
        assertEquals(true, param.required)
    }
}