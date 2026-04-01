package com.itangcent.easyapi.util.storage

import com.itangcent.easyapi.util.storage.Storage.Companion.DEFAULT_GROUP
import com.itangcent.easyapi.util.storage.Storage.Companion.NULL
import java.util.*

/**
 * Abstract implementation of [Storage].
 *
 * Provides default implementations for single-argument methods
 * by delegating to the two-argument versions with the default group.
 * Subclasses only need to implement [getCache] and [onUpdate].
 *
 * ## Implementation Notes
 * - Single-arg methods delegate to default group
 * - Queue operations use LinkedList internally
 * - All operations are synchronized for thread safety
 *
 * @see Storage for the interface
 * @see SessionStorage for in-memory implementation
 * @see LocalStorage for persistent implementation
 */
abstract class AbstractStorage : Storage {

    // ── single-arg delegates to default group ───────────────────────

    override fun get(name: String?): Any? = get(DEFAULT_GROUP, name)
    override fun set(name: String?, value: Any?) = set(DEFAULT_GROUP, name, value)
    override fun pop(name: String?): Any? = pop(DEFAULT_GROUP, name)
    override fun peek(name: String?): Any? = peek(DEFAULT_GROUP, name)
    override fun push(name: String?, value: Any?) = push(DEFAULT_GROUP, name, value)
    override fun remove(name: String) = remove(DEFAULT_GROUP, name)
    override fun keys(): Array<Any?> = keys(DEFAULT_GROUP)
    override fun clear() = clear(DEFAULT_GROUP)

    // ── two-arg implementations ─────────────────────────────────────

    override fun get(group: String?, name: String?): Any? {
        return getCache(group ?: DEFAULT_GROUP)[name]
    }

    override fun set(group: String?, name: String?, value: Any?) {
        useCache(group) { put(name ?: NULL, value) }
    }

    override fun pop(group: String?, name: String?): Any? {
        return useQueue(group, name) { pollLast() }
    }

    override fun peek(group: String?, name: String?): Any? {
        return useQueue(group, name) { peekLast() }
    }

    override fun push(group: String?, name: String?, value: Any?) {
        useQueue(group, name) { addLast(value) }
    }

    override fun remove(group: String?, name: String) {
        useCache(group) { remove(name) }
    }

    override fun keys(group: String?): Array<Any?> {
        return getCache(group ?: DEFAULT_GROUP).keys.toTypedArray()
    }

    override fun clear(group: String?) {
        useCache(group ?: DEFAULT_GROUP) { clear() }
    }

    // ── internal helpers ────────────────────────────────────────────

    @Synchronized
    private fun <T : Any> useCache(group: String?, action: MutableMap<String, Any?>.() -> T?): T? {
        val cache = getCache(group ?: DEFAULT_GROUP)
        try {
            return cache.action()
        } finally {
            onUpdate(group, cache)
        }
    }

    @Synchronized
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> useQueue(group: String?, name: String?, action: LinkedList<Any?>.() -> T?): T? {
        val cache = getCache(group ?: DEFAULT_GROUP)
        var queue = cache[name ?: NULL]
        try {
            when (queue) {
                is LinkedList<*> -> return action(queue as LinkedList<Any?>)
                is Collection<*> -> {
                    queue = LinkedList<Any?>(queue)
                    cache[name ?: NULL] = queue
                    return action(queue)
                }
                else -> {
                    queue = LinkedList<Any?>()
                    cache[name ?: NULL] = queue
                    return action(queue)
                }
            }
        } finally {
            if (queue is Collection<*> && queue.isEmpty()) {
                cache.remove(name ?: NULL)
            }
            onUpdate(group, cache)
        }
    }

    protected abstract fun getCache(group: String): MutableMap<String, Any?>

    protected abstract fun onUpdate(group: String?, cache: MutableMap<String, Any?>)
}
