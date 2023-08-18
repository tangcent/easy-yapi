package com.itangcent.idea.plugin.utils

import com.itangcent.idea.plugin.utils.Storage.Companion.DEFAULT_GROUP
import com.itangcent.idea.plugin.utils.Storage.Companion.NULL
import java.util.*

/**
 * Abstract implementation of [Storage]
 */
abstract class AbstractStorage : Storage {

    override fun get(name: String?): Any? {
        return get(DEFAULT_GROUP, name)
    }

    override fun set(name: String?, value: Any?) {
        set(DEFAULT_GROUP, name, value)
    }

    override fun pop(name: String?): Any? {
        return pop(DEFAULT_GROUP, name)
    }

    override fun peek(name: String?): Any? {
        return peek(DEFAULT_GROUP, name)
    }

    override fun push(name: String?, value: Any?) {
        push(DEFAULT_GROUP, name, value)
    }

    override fun remove(name: String) {
        remove(DEFAULT_GROUP, name)
    }

    override fun keys(): Array<Any?> {
        return keys(DEFAULT_GROUP)
    }

    override fun clear() {
        clear(DEFAULT_GROUP)
    }

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

    /**
     * Use cache and update cache
     */
    @Synchronized
    private fun <T : Any> useCache(group: String?, action: MutableMap<String, Any?>.() -> T?): T? {
        val cache = getCache(group ?: DEFAULT_GROUP)
        try {
            return cache.action()
        } finally {
            onUpdate(group, cache)
        }
    }

    /**
     * Use queue and update cache
     */
    @Synchronized
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> useQueue(group: String?, name: String?, action: LinkedList<Any?>.() -> T?): T? {
        val cache = getCache(group ?: DEFAULT_GROUP)
        var queue = cache[name ?: NULL]
        try {
            when (queue) {

                is LinkedList<*> -> {
                    return action(queue as LinkedList<Any?>)
                }

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

    /**
     * Get cache of specified group
     */
    protected abstract fun getCache(group: String): MutableMap<String, Any?>

    /**
     * called when cache is updated
     */
    protected abstract fun onUpdate(group: String?, cache: MutableMap<String, Any?>)
}