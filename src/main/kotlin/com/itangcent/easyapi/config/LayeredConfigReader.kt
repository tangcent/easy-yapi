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
        val sourcesByKey: Map<String, List<SourceValue>>
    )

    private val snapshotRef: AtomicReference<Snapshot> = AtomicReference(
        Snapshot(emptyList(), emptyMap())
    )

    private fun sources(key: String): List<SourceValue>? = snapshotRef.get().sourcesByKey[key]

    override fun getFirst(key: String): String? {
        return sources(key)?.firstOrNull()?.value
    }

    override fun getAll(key: String): List<String> {
        return sources(key).orEmpty().map { it.value }
    }

    override fun sourcesForKey(key: String): List<SourceValue> {
        return sources(key).orEmpty()
    }

    override suspend fun reload() {
        val allEntries = LinkedHashMap<String, MutableList<ConfigEntry>>()
        val sourcePriorityById = HashMap<String, Int>()
        for (source in sources.sortedByDescending { it.priority }) {
            sourcePriorityById[source.sourceId] = source.priority
            var count = 0
            for (entry in source.collect()) {
                allEntries.computeIfAbsent(entry.key) { ArrayList() }.add(entry)
                count++
            }
            LOG.info("Loaded $count config entries from [${source.sourceId}] (priority=${source.priority})")
        }

        val orderedEntries = ArrayList<Pair<String, String>>()
        val sourcesByKey = LinkedHashMap<String, List<SourceValue>>()
        val resolver = PropertyResolver(lookup = { k -> sourcesByKey[k].orEmpty().map { it.value } })

        for ((key, entryList) in allEntries) {
            val resolvedValues = ArrayList<SourceValue>()
            for (entry in entryList) {
                val resolvedValue = resolveEntry(entry, resolver)
                resolvedValues.add(
                    SourceValue(
                        sourceId = entry.sourceId,
                        priority = sourcePriorityById[entry.sourceId] ?: 0,
                        value = resolvedValue
                    )
                )
                orderedEntries.add(key to resolvedValue)
            }
            sourcesByKey[key] = resolvedValues
        }

        snapshotRef.set(Snapshot(orderedEntries, sourcesByKey))
        LOG.info("Config reload complete: ${sourcesByKey.size} keys from ${sources.size} sources")
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
