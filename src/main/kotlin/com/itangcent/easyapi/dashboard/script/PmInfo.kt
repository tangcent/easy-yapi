package com.itangcent.easyapi.dashboard.script

/**
 * Provides metadata about the current script execution context, compatible with Postman's `pm.info` API.
 *
 * Groovy usage:
 * ```groovy
 * logger.info("Running ${pm.info.eventName} script for ${pm.info.requestName}")
 * ```
 *
 * @property eventName The event type: "prerequest" or "test" (post-response)
 * @property requestName The human-readable name of the request being executed
 * @property requestId The unique identifier of the request
 */
class PmInfo(
    val eventName: String,
    val requestName: String,
    val requestId: String
)
