package com.itangcent.easyapi.logging

/**
 * Console interface for logging messages in IntelliJ IDEA.
 *
 * Provides methods for logging at different levels:
 * - trace - Detailed debugging information
 * - debug - Debugging information
 * - info - General information
 * - warn - Warning messages
 * - error - Error messages
 *
 * Implementations can output to different destinations:
 * - IDEA event log
 * - Tool window console
 * - File logs
 *
 * @see IdeaConsoleProvider for obtaining console instances
 * @see ConfigurableIdeaConsole for configurable implementation
 */
interface IdeaConsole {
    /**
     * Prints a message with newline (defaults to info level).
     */
    fun println(msg: String) {
        info(msg)
    }

    /**
     * Prints a message without newline (defaults to info level).
     */
    fun print(msg: String) {
        info(msg)
    }

    /**
     * Logs a trace-level message.
     */
    fun trace(msg: String)
    
    /**
     * Logs a debug-level message.
     */
    fun debug(msg: String)
    
    /**
     * Logs an info-level message.
     */
    fun info(msg: String)
    
    /**
     * Logs a warning message with optional throwable.
     */
    fun warn(msg: String, t: Throwable? = null)
    
    /**
     * Logs an error message with optional throwable.
     */
    fun error(msg: String, t: Throwable? = null)
}
