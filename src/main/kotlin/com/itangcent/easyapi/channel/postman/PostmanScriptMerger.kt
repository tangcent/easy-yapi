package com.itangcent.easyapi.channel.postman

import com.itangcent.easyapi.channel.postman.model.PostmanEvent

object PostmanScriptMerger {
    fun merge(events: List<PostmanEvent>): List<PostmanEvent> {
        if (events.isEmpty()) return emptyList()
        val grouped = events.groupBy { it.listen }
        return grouped.map { (listen, list) ->
            val mergedExec = LinkedHashSet<String>()
            for (e in list) {
                mergedExec.addAll(e.script.exec)
            }
            PostmanEvent(listen = listen, script = list.first().script.copy(exec = mergedExec.toList()))
        }
    }
}

