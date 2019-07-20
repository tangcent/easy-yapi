package com.itangcent.common.model

abstract class Extensible {

    private var ext: LinkedHashMap<String, Any?>? = null

    fun <T> getExt(attr: String): T? {
        return ext?.get(attr) as T?
    }

    fun setExt(attr: String, value: Any?) {
        if (ext == null) {
            ext = LinkedHashMap()
        }
        ext!![attr] = value
    }
}