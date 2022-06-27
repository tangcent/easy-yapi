package com.itangcent.idea.plugin

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey

class DataEventCollector : DataContext {

    private val cache: LinkedHashMap<String, Any?> = LinkedHashMap()

    @Inject
    private var anActionEvent: AnActionEvent? = null

    private var dataReachable: Boolean = true

    constructor()

    constructor(anActionEvent: AnActionEvent) {
        this.anActionEvent = anActionEvent
    }

    override fun getData(dataId: String): Any? {
        if (cache.containsKey(dataId)) {
            return cache[dataId]
        }
        if (!dataReachable) {
            return null
        }
        val data = anActionEvent!!.getData<Any?>(DataKey.create(dataId))
        cache[dataId] = data
        return data
    }

    fun disableDataReach() {
        dataReachable = false
    }
}