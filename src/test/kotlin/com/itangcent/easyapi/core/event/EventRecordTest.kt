package com.itangcent.easyapi.core.event

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EventRecordTest {

    @Test
    fun testEventRecordCreation() {
        val record = EventRecord("TEST_EVENT", mapOf("key" to "value"))
        assertEquals("TEST_EVENT", record.event)
        assertEquals(mapOf("key" to "value"), record.details)
        assertTrue(record.timestamp > 0)
    }

    @Test
    fun testEventRecordWithCustomTimestamp() {
        val customTimestamp = 1234567890L
        val record = EventRecord("TEST_EVENT", emptyMap(), customTimestamp)
        assertEquals(customTimestamp, record.timestamp)
    }

    @Test
    fun testEventRecordWithEmptyDetails() {
        val record = EventRecord("EMPTY_EVENT", emptyMap())
        assertTrue(record.details.isEmpty())
    }

    @Test
    fun testEventRecordWithComplexDetails() {
        val details = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true,
            "null" to null,
            "nested" to mapOf("inner" to "value")
        )
        val record = EventRecord("COMPLEX_EVENT", details)
        assertEquals(5, record.details.size)
        assertEquals("value", record.details["string"])
        assertEquals(42, record.details["number"])
        assertEquals(true, record.details["boolean"])
        assertNull(record.details["null"])
        assertEquals(mapOf("inner" to "value"), record.details["nested"])
    }

    @Test
    fun testEventRecordEquality() {
        val timestamp = System.currentTimeMillis()
        val record1 = EventRecord("EVENT", mapOf("key" to "value"), timestamp)
        val record2 = EventRecord("EVENT", mapOf("key" to "value"), timestamp)
        assertEquals(record1, record2)
    }

    @Test
    fun testEventRecordCopy() {
        val original = EventRecord("EVENT", mapOf("key" to "value"))
        val copy = original.copy(event = "MODIFIED_EVENT")
        assertEquals("MODIFIED_EVENT", copy.event)
        assertEquals(original.details, copy.details)
    }
}
