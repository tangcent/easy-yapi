package com.itangcent.cache

interface CacheSwitcher {

    fun notUserCache()

    fun userCache()
}

fun CacheSwitcher.withoutCache(call: () -> Unit) {
    this.notUserCache()
    try {
        call()
    } finally {
        this.userCache()
    }
}