package com.itangcent.easyapi.exporter.postman

import org.junit.Assert.*
import org.junit.Test

class PostmanHeaderDataTest {

    @Test
    fun testConstruction() {
        val header = PostmanHeaderData(name = "Content-Type", value = "application/json")
        assertEquals("Content-Type", header.name)
        assertEquals("application/json", header.value)
    }

    @Test
    fun testConstructionWithNullValue() {
        val header = PostmanHeaderData(name = "Authorization")
        assertEquals("Authorization", header.name)
        assertNull(header.value)
    }

    @Test
    fun testCopy() {
        val header = PostmanHeaderData(name = "Accept", value = "application/json")
        val copy = header.copy(value = "application/xml")
        assertEquals("Accept", copy.name)
        assertEquals("application/xml", copy.value)
    }

    @Test
    fun testEquality() {
        val header1 = PostmanHeaderData(name = "Content-Type", value = "application/json")
        val header2 = PostmanHeaderData(name = "Content-Type", value = "application/json")
        assertEquals(header1, header2)
    }
}
