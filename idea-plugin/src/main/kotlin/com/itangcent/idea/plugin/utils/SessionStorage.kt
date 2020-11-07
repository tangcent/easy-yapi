package com.itangcent.idea.plugin.utils

import com.google.inject.Singleton
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.utils.sub
import com.itangcent.common.utils.KV
import com.itangcent.idea.plugin.utils.Storage.Companion.NULL
import java.util.*

@Singleton
@ScriptTypeName("session")
class SessionStorage : AbstractStorage() {

    private val localKV = ThreadLocal.withInitial {
        KV.create<String, Any?>()
    }

    private val kv: KV<String, Any?>
        get() = localKV.get()

    override fun get(group: String?, name: String?): Any? {
        return kv.sub(group ?: NULL)[name]
    }

    override fun set(group: String?, name: String?, value: Any?) {
        kv.sub(group ?: NULL)[name ?: NULL] = value
    }

    override fun pop(group: String?, name: String?): Any? {
        return queue(group, name).removeLast()
    }

    override fun peek(group: String?, name: String?): Any? {
        return queue(group, name).peekLast()
    }

    override fun push(group: String?, name: String?, value: Any?) {
        queue(group, name).addLast(value)
    }

    @Suppress("UNCHECKED_CAST")
    private fun queue(group: String?, name: String?): LinkedList<Any?> {
        val sub = kv.sub(group ?: NULL)
        var queue = sub[name ?: NULL]
        if (queue == null || queue !is LinkedList<*>) {
            queue = LinkedList<Any>()
            sub[name ?: NULL] = queue
        }
        return queue as LinkedList<Any?>
    }

    override fun remove(group: String?, name: String) {
        kv.sub(group ?: NULL).remove(name)
    }

    override fun keys(group: String?): Array<Any?> {
        return kv.sub(group ?: NULL).keys.toTypedArray()
    }

    override fun clear(group: String?) {
        kv.remove(group)
    }
}
