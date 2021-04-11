package com.itangcent.mock

import com.google.inject.Singleton
import com.itangcent.idea.utils.SystemProvider

@Singleton
class ImmutableSystemProvider(private val currentTimeMillis: Long) : SystemProvider {

    override fun currentTimeMillis(): Long {
        return currentTimeMillis
    }
}