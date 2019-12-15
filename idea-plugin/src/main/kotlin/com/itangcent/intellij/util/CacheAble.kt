package com.itangcent.intellij.util

import com.google.inject.ImplementedBy

@ImplementedBy(DefaultCacheAle::class)
interface CacheAble {

    fun <T : Any> cache(key: Any, action: () -> T?): T?

}