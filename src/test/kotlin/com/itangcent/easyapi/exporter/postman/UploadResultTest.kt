package com.itangcent.easyapi.exporter.postman

import org.junit.Assert.*
import org.junit.Test

class UploadResultTest {

    @Test
    fun testConstruction() {
        val result = UploadResult(
            success = true,
            message = "Collection uploaded successfully",
            collectionId = "12345"
        )
        
        assertTrue(result.success)
        assertEquals("Collection uploaded successfully", result.message)
        assertEquals("12345", result.collectionId)
    }

    @Test
    fun testConstructionWithDefaults() {
        val result = UploadResult(success = false)
        
        assertFalse(result.success)
        assertNull(result.message)
        assertNull(result.collectionId)
    }

    @Test
    fun testCopy() {
        val result = UploadResult(success = false, message = "Failed")
        val copy = result.copy(success = true, message = "Success")
        
        assertTrue(copy.success)
        assertEquals("Success", copy.message)
    }

    @Test
    fun testEquality() {
        val result1 = UploadResult(success = true, message = "Success", collectionId = "123")
        val result2 = UploadResult(success = true, message = "Success", collectionId = "123")
        
        assertEquals(result1, result2)
    }
}
