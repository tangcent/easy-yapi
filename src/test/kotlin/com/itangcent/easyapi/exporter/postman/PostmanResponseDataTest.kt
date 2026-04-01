package com.itangcent.easyapi.exporter.postman

import org.junit.Assert.*
import org.junit.Test

class PostmanResponseDataTest {

    @Test
    fun testConstruction() {
        val response = PostmanResponseData(
            name = "Success",
            statusCode = 200,
            headers = listOf(PostmanHeaderData("Content-Type", "application/json")),
            description = "Successful response"
        )
        
        assertEquals("Success", response.name)
        assertEquals(200, response.statusCode)
        assertEquals(1, response.headers.size)
        assertEquals("Successful response", response.description)
    }

    @Test
    fun testConstructionWithDefaults() {
        val response = PostmanResponseData()
        assertNull(response.name)
        assertNull(response.statusCode)
        assertTrue(response.headers.isEmpty())
        assertNull(response.body)
        assertNull(response.description)
    }

    @Test
    fun testCopy() {
        val response = PostmanResponseData(name = "Error", statusCode = 404)
        val copy = response.copy(statusCode = 500)
        assertEquals("Error", copy.name)
        assertEquals(500, copy.statusCode)
    }

    @Test
    fun testEquality() {
        val response1 = PostmanResponseData(name = "Success", statusCode = 200)
        val response2 = PostmanResponseData(name = "Success", statusCode = 200)
        assertEquals(response1, response2)
    }
}
