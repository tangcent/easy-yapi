package com.itangcent.idea.plugin.utils

/**
 * The [Storage] provides a way to store data.
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