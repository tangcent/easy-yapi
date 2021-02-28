package com.itangcent.utils

import com.itangcent.common.utils.KV
import com.itangcent.common.utils.readString
import com.itangcent.common.utils.safeComputeIfAbsent

object ResourceUtils {

    private val resourceCache: KV<String, String> = KV()

    fun readResource(configName: String): String {
        return resourceCache.safeComputeIfAbsent(configName) {
            (ResourceUtils::class.java.classLoader.getResourceAsStream(configName)
                    ?: ResourceUtils::class.java.getResourceAsStream(configName)).readString(Charsets.UTF_8)
        } ?: ""
    }
}
