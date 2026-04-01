package com.itangcent.easyapi.exporter.jaxrs

import org.junit.Assert.*
import org.junit.Test

class JaxRsContentTypesTest {

    @Test
    fun testConstruction() {
        val contentTypes = JaxRsContentTypes(
            consumes = listOf("application/json"),
            produces = listOf("application/json", "application/xml")
        )
        
        assertEquals(1, contentTypes.consumes.size)
        assertEquals("application/json", contentTypes.consumes[0])
        assertEquals(2, contentTypes.produces.size)
        assertEquals("application/json", contentTypes.produces[0])
        assertEquals("application/xml", contentTypes.produces[1])
    }

    @Test
    fun testConstructionWithEmptyLists() {
        val contentTypes = JaxRsContentTypes(
            consumes = emptyList(),
            produces = emptyList()
        )
        
        assertTrue(contentTypes.consumes.isEmpty())
        assertTrue(contentTypes.produces.isEmpty())
    }

    @Test
    fun testCopy() {
        val contentTypes = JaxRsContentTypes(
            consumes = listOf("application/json"),
            produces = listOf("application/json")
        )
        
        val copy = contentTypes.copy(produces = listOf("application/xml"))
        assertEquals(1, copy.consumes.size)
        assertEquals(1, copy.produces.size)
        assertEquals("application/xml", copy.produces[0])
    }

    @Test
    fun testEquality() {
        val contentTypes1 = JaxRsContentTypes(
            consumes = listOf("application/json"),
            produces = listOf("application/json")
        )
        val contentTypes2 = JaxRsContentTypes(
            consumes = listOf("application/json"),
            produces = listOf("application/json")
        )
        assertEquals(contentTypes1, contentTypes2)
    }
}
