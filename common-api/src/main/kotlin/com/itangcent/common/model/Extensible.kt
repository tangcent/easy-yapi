package com.itangcent.common.model

abstract class Extensible {

    private var ext: LinkedHashMap<String, Any?>? = null

    fun hasExt(attr: String): Boolean {
        return ext?.containsKey(attr) ?: false
    }

    fun hasAnyExt(vararg attr: String): Boolean {
        if (ext == null) {
            return false
        }
        return attr.any { ext!!.containsKey(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getExt(attr: String): T? {
        return ext?.get(attr) as T?
    }

    fun setExt(attr: String, value: Any?) {
        if (ext == null) {
            ext = LinkedHashMap()
        }
        ext!![attr] = value
    }

    fun exts(): Map<String, Any?>? {
        return ext
    }
}