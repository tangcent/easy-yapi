package com.itangcent.idea.plugin.utils

/**
 * The [Storage] provides a way to store data.
 */
interface Storage {

    /**
     * Get the value of the specified name in the default group
     */
    fun get(name: String?): Any?

    /**
     * Get the value of the specified name in the specified group
     */
    fun get(group: String?, name: String?): Any?

    /**
     * Set the value of the specified name in the default group
     */
    fun set(name: String?, value: Any?)

    /**
     * Set the value of the specified name in the specified group
     */
    fun set(group: String?, name: String?, value: Any?)

    /**
     * Pop the value of the specified name in the default group
     */
    fun pop(name: String?): Any?

    /**
     * Pop the value of the specified name in the specified group
     */
    fun pop(group: String?, name: String?): Any?

    /**
     * Peek the value of the specified name in the default group
     */
    fun peek(name: String?): Any?

    /**
     * Peek the value of the specified name in the specified group
     */
    fun peek(group: String?, name: String?): Any?

    /**
     * Push the value of the specified name in the default group
     */
    fun push(name: String?, value: Any?)

    /**
     * Push the value of the specified name in the specified group
     */
    fun push(group: String?, name: String?, value: Any?)

    /**
     * Remove the value of the specified name in the default group
     */
    fun remove(name: String)

    /**
     * Remove the value of the specified name in the specified group
     */
    fun remove(group: String?, name: String)

    /**
     * Get all keys in the default group
     */
    fun keys(): Array<Any?>

    /**
     * Get all keys in the specified group
     */
    fun keys(group: String?): Array<Any?>

    /**
     * Clear all data in the default group
     */
    fun clear()

    /**
     * Clear all data in the specified group
     */
    fun clear(group: String?)

    companion object {
        /**
         * The default group
         */
        const val DEFAULT_GROUP = "__default_local_group"

        /**
         * The null key
         */
        const val NULL = "__null"
    }
}