package com.itangcent.easyapi.util.storage

/**
 * Grouped key-value storage with queue operations.
 *
 * Provides a storage interface that supports:
 * - Grouped key-value pairs
 * - Queue operations (push, pop, peek)
 * - Clear operations for groups
 *
 * Implementations:
 * - [SessionStorage] - In-memory storage for current session
 * - [LocalStorage] - Persistent SQLite-backed storage
 *
 * ## Usage
 * ```kotlin
 * // Basic operations
 * storage.set("key", "value")
 * val value = storage.get("key")
 *
 * // Grouped operations
 * storage.set("group1", "key", "value")
 * val groupValue = storage.get("group1", "key")
 *
 * // Queue operations
 * storage.push("queue", item1)
 * storage.push("queue", item2)
 * val first = storage.pop("queue")
 * ```
 *
 * @see SessionStorage for in-memory implementation
 * @see LocalStorage for persistent implementation
 */
interface Storage {

    fun get(name: String?): Any?

    fun get(group: String?, name: String?): Any?

    fun set(name: String?, value: Any?)

    fun set(group: String?, name: String?, value: Any?)

    fun pop(name: String?): Any?

    fun pop(group: String?, name: String?): Any?

    fun peek(name: String?): Any?

    fun peek(group: String?, name: String?): Any?

    fun push(name: String?, value: Any?)

    fun push(group: String?, name: String?, value: Any?)

    fun remove(name: String)

    fun remove(group: String?, name: String)

    fun keys(): Array<Any?>

    fun keys(group: String?): Array<Any?>

    fun clear()

    fun clear(group: String?)

    companion object {
        const val DEFAULT_GROUP = "__default_local_group"
        const val NULL = "__null"
    }
}
