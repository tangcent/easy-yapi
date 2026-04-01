package com.itangcent.easyapi.core.event

/**
 * Predefined event keys used throughout the EasyAPI plugin.
 *
 * These keys are used with [CoroutineEventBus] for inter-component communication.
 */
object EventKeys {
    /**
     * Event fired when an ActionContext completes and is being stopped.
     *
     * Handlers registered for this event should perform cleanup operations.
     */
    const val ON_COMPLETED: String = "ON_COMPLETED"
}
