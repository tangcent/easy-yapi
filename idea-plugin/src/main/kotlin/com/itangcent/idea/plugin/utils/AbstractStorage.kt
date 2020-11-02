package com.itangcent.idea.plugin.utils

import com.itangcent.idea.plugin.utils.Storage.Companion.DEFAULT_GROUP

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

}