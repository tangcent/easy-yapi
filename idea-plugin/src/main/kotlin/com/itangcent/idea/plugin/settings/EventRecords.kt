package com.itangcent.idea.plugin.settings

import com.intellij.ide.util.PropertiesComponent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object EventRecords {

    //region keys----------------------------------------------------

    const val ENUM_RESOLVE = "com.itangcent.enum_resolve"

    //endregion  keys----------------------------------------------------

    private val records = ConcurrentHashMap<String, RecordHolder>()

    private val propertiesComponent by lazy {
        PropertiesComponent.getInstance()
    }

    fun record(key: String): Int {
        return getRecordHolder(key).incr()
    }

    fun getRecord(key: String): Int {
        return getRecordHolder(key).get()
    }

    private fun getRecordHolder(key: String) = records.computeIfAbsent(key) { RecordHolder(it) }

    class RecordHolder(private val key: String) {

        private val value: AtomicInteger by lazy { AtomicInteger(propertiesComponent.getInt(key, 0)) }

        fun incr(): Int {
            return value.incrementAndGet().also {
                propertiesComponent.setValue(key, it, 0)
            }
        }

        fun get(): Int {
            return value.get()
        }
    }
}