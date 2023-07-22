package com.itangcent.cache

/**
 * This interface defines a way to enable or disable caching behavior within an object or system.
 */
interface CacheSwitcher {

    fun notUserCache()

    fun userCache()
}

/**
 * allow a block of code to be executed with caching disabled.
 */
fun CacheSwitcher.withoutCache(call: () -> Unit) {
    this.notUserCache()
    try {
        call()
    } finally {
        this.userCache()
    }
}