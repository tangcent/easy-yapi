package com.itangcent.idea.plugin.settings

import com.intellij.ide.util.PropertiesComponent
import java.util.concurrent.ConcurrentHashMap

object EventRecords {

    //region keys----------------------------------------------------

    const val ENUM_RESOLVE = "com.itangcent.enum_resolve"

    //endregion  keys----------------------------------------------------

    private val records = ConcurrentHashMap<String, RecordHolder>()

    private val propertiesComponent by lazy {
        PropertiesComponent.getInstance()
    }

    fun record(key: String): Int {
        return records.computeIfAbsent(key) { RecordHolder(it) }.incr()
    }

    fun getRecord(key: String): Int {
        return records.computeIfAbsent(key) { RecordHolder(it) }.get()
    }

    class RecordHolder(private val key: String) {

        private var value: Int? = null

        @Synchronized
        fun incr(): Int {
            value = get() + 1
            propertiesComponent.setValue(key, value!!, 0)
            return value!!
        }

        @Synchronized
        fun get(): Int {
            if (value == null) {
                value = propertiesComponent.getInt(key, 0)
            }
            return value ?: 0
        }
    }
}