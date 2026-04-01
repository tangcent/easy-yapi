package com.itangcent.easyapi.psi

/**
 * Listener for context switches during PSI processing.
 *
 * Called when the processing context changes between modules.
 * Used to:
 * - Track processing state
 * - Handle cross-references
 * - Manage caching strategies
 *
 * @see DefaultContextSwitchListener for default implementation
 */
interface ContextSwitchListener {
    /**
     * Called when switching to a new module context.
     *
     * @param oldModule The previous module name, or null if this is the first
     * @param newModule The new module name
     */
    fun onModuleChanged(oldModule: String?, newModule: String)
}

