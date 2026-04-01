package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.util.storage.Storage

/**
 * Thin wrapper that delegates all calls to the underlying [Storage].
 *
 * Exists so that the script engine receives a concrete class (not an interface)
 * which some JSR-223 engines handle more reliably for method resolution.
 */
class ScriptStorageWrapper(private val delegate: Storage) {

    fun get(name: String?): Any? = delegate.get(name)
    fun get(group: String?, name: String?): Any? = delegate.get(group, name)

    fun set(name: String?, value: Any?) = delegate.set(name, value)
    fun set(group: String?, name: String?, value: Any?) = delegate.set(group, name, value)

    fun pop(name: String?): Any? = delegate.pop(name)
    fun pop(group: String?, name: String?): Any? = delegate.pop(group, name)

    fun peek(name: String?): Any? = delegate.peek(name)
    fun peek(group: String?, name: String?): Any? = delegate.peek(group, name)

    fun push(name: String?, value: Any?) = delegate.push(name, value)
    fun push(group: String?, name: String?, value: Any?) = delegate.push(group, name, value)

    fun remove(name: String) = delegate.remove(name)
    fun remove(group: String?, name: String) = delegate.remove(group, name)

    fun keys(): Array<Any?> = delegate.keys()
    fun keys(group: String?): Array<Any?> = delegate.keys(group)

    fun clear() = delegate.clear()
    fun clear(group: String?) = delegate.clear(group)
}
