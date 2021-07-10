package com.itangcent.idea.utils

import com.google.inject.ImplementedBy
import com.google.inject.Singleton

@ImplementedBy(DefaultSystemProvider::class)
interface SystemProvider {
    fun currentTimeMillis(): Long

    fun runtime(): Runtime
}

@Singleton
class DefaultSystemProvider : SystemProvider {
    override fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    override fun runtime(): Runtime {
        return Runtime.getRuntime();
    }
}