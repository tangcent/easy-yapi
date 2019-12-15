package com.itangcent.intellij.util

import com.google.inject.Singleton
import com.itangcent.common.utils.safeComputeIfAbsent

@Singleton
class DefaultCacheAle : CacheAble {

    val cache: MutableMap<Any, Any> = HashMap()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> cache(key: Any, action: () -> T?): T? {
        val value = cache.safeComputeIfAbsent(key) {
            action() ?: NULL
        }
        if (value == NULL) {
            return null
        }
        return value as T?
    }

    companion object {
        val NULL = Any()
    }
}