package com.itangcent.mock

import com.google.inject.Singleton
import com.itangcent.idea.utils.SystemProvider

@Singleton
class ImmutableSystemProvider : SystemProvider {

    var currentTimeMillis: Long? = null

    var runtime: Runtime? = null


    constructor()

    constructor(currentTimeMillis: Long) {
        this.currentTimeMillis = currentTimeMillis
    }

    override fun currentTimeMillis(): Long {
        return currentTimeMillis ?: System.currentTimeMillis();
    }

    override fun runtime(): Runtime {
        return runtime ?: Runtime.getRuntime()
    }
}