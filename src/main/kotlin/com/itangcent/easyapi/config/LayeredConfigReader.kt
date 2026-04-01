package com.itangcent.easyapi.config

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.PropertyResolver
import com.itangcent.easyapi.logging.IdeaLog
import java.util.concurrent.atomic.AtomicReference

/**
 * A [ConfigReader] that reads configuration from multiple [ConfigSource]s in priority order.
 * 
 * Sources are processed in descending order of [ConfigSource.priority], meaning:
 * - Higher priority sources are processed first
 * - [getFirst] returns the value from the highest priority source
 * - [getAll] returns values ordered by source priority (highest first)
 * 
 * This allows configuration to be layered, with higher priority sources overriding
 * or supplementing values from lower priority sources.
 */
class LayeredConfigReader(
    private val sources: List<ConfigSource>
) : ConfigReader {
    companion object : IdeaLog

    private val snapshotRef: AtomicReference<Map<String, List<String>>> = AtomicReference(emptyMap())

    private fun values(key: String): List<String>? = snapshotRef.get()[key]

    override fun getFirst(key: String): String? {
        return values(key)?.firstOrNull()
    }

    override fun getAll(key: String): List<String> {
        return values(key).orEmpty()
    }

    override suspend fun reload() {
        val allEntries = LinkedHashMap<String, MutableList<ConfigEntry>>()
        for (source in sources.sortedByDescending { it.priority }) {
            var count = 0
            for (entry in source.collect()) {
                allEntries.computeIfAbsent(entry.key) { ArrayList() }.add(entry)
                count++
            }
            LOG.info("Loaded $count config entries from [${source.sourceId}] (priority=${source.priority})")
        }

        val allResolved = LinkedHashMap<String, List<String>>()
        val resolver = PropertyResolver(lookup = { k -> allResolved[k].orEmpty() })

        for ((key, entryList) in allEntries) {
            val resolvedValues = entryList.map { entry -> resolveEntry(entry, resolver) }
            allResolved[key] = resolvedValues
        }

        snapshotRef.set(allResolved)
        LOG.info("Config reload complete: ${allResolved.size} keys from ${sources.size} sources")
    }

    override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
        val snapshot = snapshotRef.get()
        for ((key, values) in snapshot) {
            if (keyFilter(key)) {
                for (value in values) {
                    action(key, value)
                }
            }
        }
    }

    private fun resolveEntry(entry: ConfigEntry, resolver: PropertyResolver): String {
        val d = entry.directives
        if (!d.resolveProperty) return entry.value
        return resolver.resolve(
            entry.value,
            resolveMulti = d.resolveMulti,
            ignoreUnresolved = d.ignoreUnresolved
        )
    }
}
