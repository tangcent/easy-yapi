package com.itangcent.easyapi.core.event

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Represents a single event record with timestamp.
 *
 * @param event The event name/type
 * @param details Additional details about the event
 * @param timestamp When the event occurred (defaults to current time)
 */
data class EventRecord(
    val event: String,
    val details: Map<String, Any?>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * A thread-safe collection for recording and querying events.
 *
 * Useful for debugging, auditing, and tracking operations within the plugin.
 *
 * ## Usage
 * ```kotlin
 * val records = EventRecords()
 *
 * // Record events
 * records.record("API_EXPORT", mapOf("file" to "api.json"))
 * records.record("API_EXPORT", mapOf("file" to "api2.json"))
 *
 * // Query events
 * val exports = records.query("API_EXPORT")
 * exports.forEach { println(it.details) }
 *
 * // Clear all records
 * records.clear()
 * ```
 */
class EventRecords {
    private val records = ConcurrentLinkedQueue<EventRecord>()

    /**
     * Records a new event.
     *
     * @param event The event name/type
     * @param details Additional details about the event
     */
    fun record(event: String, details: Map<String, Any?> = emptyMap()) {
        records.add(EventRecord(event, details))
    }

    /**
     * Queries all records matching the specified event name.
     *
     * @param event The event name to search for
     * @return List of matching EventRecord instances
     */
    fun query(event: String): List<EventRecord> {
        return records.filter { it.event == event }
    }

    /**
     * Clears all recorded events.
     */
    fun clear() {
        records.clear()
    }
}
