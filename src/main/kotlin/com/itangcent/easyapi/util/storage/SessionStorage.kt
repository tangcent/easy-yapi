package com.itangcent.easyapi.util.storage

import com.itangcent.easyapi.core.context.AutoClear
import com.itangcent.easyapi.util.storage.Storage.Companion.DEFAULT_GROUP

/**
 * In-memory [Storage] scoped to the current action.
 *
 * Data is stored in memory and cleared when the action completes.
 * Implements [AutoClear] to integrate with ActionContext lifecycle.
 *
 * ## Usage
 * ```kotlin
 * val sessionStorage = SessionStorage()
 * sessionStorage.set("temp", "data")
 * val data = sessionStorage.get("temp")
 * // Data is automatically cleared when action context ends
 * ```
 *
 * @see Storage for the interface
 * @see LocalStorage for persistent storage
 */
class SessionStorage : AbstractStorage(), AutoClear {

    private val data: MutableMap<String, Any?> = linkedMapOf()

    @Suppress("UNCHECKED_CAST")
    override fun getCache(group: String): MutableMap<String, Any?> {
        return data.getOrPut(group) { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
    }

    override fun onUpdate(group: String?, cache: MutableMap<String, Any?>) {
        val g = group ?: DEFAULT_GROUP
        if (cache.isEmpty()) {
            data.remove(g)
        } else {
            data[g] = cache
        }
    }

    override suspend fun cleanup() {
        data.clear()
    }
}