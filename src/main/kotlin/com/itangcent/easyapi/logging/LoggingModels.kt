package com.itangcent.easyapi.logging

/**
 * A log message with level, content, and timestamp.
 *
 * @param level The log level
 * @param message The log message content
 * @param timestamp The time when the message was created
 */
data class LogMessage(
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Log levels for filtering and display.
 *
 * Levels are ordered by severity threshold:
 * - TRACE (0) - Detailed debugging information
 * - DEBUG (10) - Debugging information
 * - INFO (20) - General information
 * - WARN (30) - Warning messages
 * - ERROR (40) - Error messages
 *
 * @param threshold The numeric threshold for filtering
 */
enum class LogLevel(val threshold: Int) {
    TRACE(0),
    DEBUG(10),
    INFO(20),
    WARN(30),
    ERROR(40)
}
