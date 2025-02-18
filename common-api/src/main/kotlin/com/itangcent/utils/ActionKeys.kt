package com.itangcent.utils

/**
 * Event keys for inner action completion
 */
object ActionKeys {
    /**
     * Event key for when an inner action is completed.
     * Listeners can use this to perform cleanup operations (e.g. clear caches)
     * but should remain active for future actions.
     */
    const val ACTION_COMPLETED = "com.itangcent.action.completed"
} 