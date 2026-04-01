package com.itangcent.easyapi.core.event

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class EventRecordsTest {

    private lateinit var eventRecords: EventRecords

    @Before
    fun setUp() {
        eventRecords = EventRecords()
    }

    @Test
    fun testRecordEvent() {
        eventRecords.record("TEST_EVENT", mapOf("key" to "value"))
        val results = eventRecords.query("TEST_EVENT")
        assertEquals(1, results.size)
        assertEquals("TEST_EVENT", results[0].event)
        assertEquals(mapOf("key" to "value"), results[0].details)
    }

    @Test
    fun testRecordEventWithEmptyDetails() {
        eventRecords.record("EMPTY_EVENT")
        val results = eventRecords.query("EMPTY_EVENT")
        assertEquals(1, results.size)
        assertTrue(results[0].details.isEmpty())
    }

    @Test
    fun testQueryNonExistentEvent() {
        val results = eventRecords.query("NON_EXISTENT")
        assertTrue(results.isEmpty())
    }

    @Test
    fun testMultipleEvents() {
        eventRecords.record("EVENT_1", mapOf("id" to 1))
        eventRecords.record("EVENT_2", mapOf("id" to 2))
        eventRecords.record("EVENT_1", mapOf("id" to 3))

        val event1Results = eventRecords.query("EVENT_1")
        assertEquals(2, event1Results.size)

        val event2Results = eventRecords.query("EVENT_2")
        assertEquals(1, event2Results.size)
    }

    @Test
    fun testClear() {
        eventRecords.record("EVENT_1")
        eventRecords.record("EVENT_2")
        eventRecords.record("EVENT_3")

        eventRecords.clear()

        assertTrue(eventRecords.query("EVENT_1").isEmpty())
        assertTrue(eventRecords.query("EVENT_2").isEmpty())
        assertTrue(eventRecords.query("EVENT_3").isEmpty())
    }

    @Test
    fun testThreadSafety() {
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) { eventId ->
                    eventRecords.record("EVENT_$threadId", mapOf("seq" to eventId))
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        var totalRecords = 0
        for (i in 1..10) {
            totalRecords += eventRecords.query("EVENT_$i").size
        }
        assertEquals(1000, totalRecords)
    }

    @Test
    fun testEventOrderPreserved() {
        eventRecords.record("ORDERED", mapOf("seq" to 1))
        eventRecords.record("ORDERED", mapOf("seq" to 2))
        eventRecords.record("ORDERED", mapOf("seq" to 3))

        val results = eventRecords.query("ORDERED")
        assertEquals(3, results.size)
        assertEquals(1, results[0].details["seq"])
        assertEquals(2, results[1].details["seq"])
        assertEquals(3, results[2].details["seq"])
    }
}
