package com.itangcent.utils

import com.itangcent.common.constant.Attrs
import com.itangcent.common.model.Header
import com.itangcent.idea.plugin.api.export.yapi.setExample
import com.itangcent.utils.ExtensibleKit.fromJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test


/**
 * Test case for [ExtensibleKit]
 */
class ExtensibleKitTest {

    @Test
    fun testFromJson() {
        val acceptHeader = Header()

        acceptHeader.name = "Accept"
        acceptHeader.value = "*/*"
        acceptHeader.desc = "authentication"
        acceptHeader.required = true

        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true}"))
        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, demo:\"token123\"}"))

        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true}", Attrs.DEMO_ATTR))
        assertNotEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, demo:\"token123\"}", Attrs.DEMO_ATTR))

        acceptHeader.setExample("token123")

        assertNotEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true}"))
        assertNotEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, demo:\"token123\"}"))

        assertNotEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true}", Attrs.DEMO_ATTR))
        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, demo:\"token123\"}", Attrs.DEMO_ATTR))

        //ext with '@'
        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, \"@demo\":\"token123\"}"))
    }
}