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
 * - [foreach] iterates over entries in their original order from config files
 * 
 * This allows configuration to be layered, with higher priority sources overriding
 * or supplementing values from lower priority sources.
 */
class LayeredConfigReader(
    private val sources: List<ConfigSource>
) : ConfigReader {
    companion object : IdeaLog

    private data class Snapshot(
        val orderedEntries: List<Pair<String, String>>,
        val valuesByKey: Map<String, List<String>>
    )

    private val snapshotRef: AtomicReference<Snapshot> = AtomicReference(
        Snapshot(emptyList(), emptyMap())
    )

    private fun values(key: String): List<String>? = snapshotRef.get().valuesByKey[key]

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

        val orderedEntries = ArrayList<Pair<String, String>>()
        val valuesByKey = LinkedHashMap<String, List<String>>()
        val resolver = PropertyResolver(lookup = { k -> valuesByKey[k].orEmpty() })

        for ((key, entryList) in allEntries) {
            val resolvedValues = ArrayList<String>()
            for (entry in entryList) {
                val resolvedValue = resolveEntry(entry, resolver)
                resolvedValues.add(resolvedValue)
                orderedEntries.add(key to resolvedValue)
            }
            valuesByKey[key] = resolvedValues
        }

        snapshotRef.set(Snapshot(orderedEntries, valuesByKey))
        LOG.info("Config reload complete: ${valuesByKey.size} keys from ${sources.size} sources")
    }

    override fun foreach(keyFilter: (String) -> Boolean, action: (String, String) -> Unit) {
        val snapshot = snapshotRef.get()
        for ((key, value) in snapshot.orderedEntries) {
            if (keyFilter(key)) {
                action(key, value)
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
