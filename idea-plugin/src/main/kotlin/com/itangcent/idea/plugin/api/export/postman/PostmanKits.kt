package com.itangcent.idea.plugin.api.export.postman

import com.itangcent.common.utils.isMutableMap
import com.itangcent.intellij.extend.asHashMap


fun HashMap<String, Any?>.isApi(): Boolean {
    return this.containsKey("request")
}

fun HashMap<String, Any?>.isCollection(): Boolean {
    return !this.isApi()
}

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.getEditableItem(): ArrayList<HashMap<String, Any?>> {
    var items = this["item"]
    if (items != null) {
        if (items is ArrayList<*>) {
            val firstOrNull = items.firstOrNull()
            if (firstOrNull != null && firstOrNull !is HashMap<*, *>) {
                val arrayListItems = ArrayList<HashMap<String, Any?>>()
                items.forEach { arrayListItems.add(it.asHashMap()) }
                if (this.isMutableMap()) {
                    (this as MutableMap)["item"] = arrayListItems
                }
                return arrayListItems
            }
            return items as ArrayList<HashMap<String, Any?>>
        }

        if (items is List<*>) {
            val arrayListItems = ArrayList<HashMap<String, Any?>>()
            items.filterNotNull().forEach { arrayListItems.add((it.asHashMap())) }
            if (this.isMutableMap()) {
                (this as MutableMap)["item"] = arrayListItems
            }
            return arrayListItems
        }
    }

    items = ArrayList<HashMap<String, Any?>>()
    if (this.isMutableMap()) {
        (this as MutableMap)["item"] = items
    }
    return items
}