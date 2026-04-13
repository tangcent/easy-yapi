package com.itangcent.easyapi.util.storage

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.event.EventBus
import com.itangcent.easyapi.core.event.EventKeys
import com.itangcent.easyapi.util.storage.Storage.Companion.DEFAULT_GROUP

/**
 * In-memory [Storage] scoped to the current action.
 *
 * Data is stored in memory and cleared when the action completes.
 *
 * ## Usage
 * ```kotlin
 * val sessionStorage = SessionStorage()
 * sessionStorage.set("temp", "data")
 * val data = sessionStorage.get("temp")
 * // Data is automatically cleared when action ends
 * ```
 *
 * @see Storage for the interface
 * @see LocalStorage for persistent storage
 */
@Service(Service.Level.PROJECT)
class SessionStorage(private val project: Project) : AbstractStorage() {

    private val data: MutableMap<String, Any?> = linkedMapOf()

    init {
        EventBus.getInstance(project).register(EventKeys.ON_COMPLETED) {
            data.clear()
        }
    }

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

    companion object {
        fun getInstance(project: Project): SessionStorage = project.service()
    }
}
