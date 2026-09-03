package com.itangcent.easyapi.core.ai.agent

/**
 * External knowledge state for the agent loop.
 *
 * Rule-key catalogs, script-context profiles, and script-object API signatures
 * are stored here instead of being appended as full [ToolResult.Text] to the
 * conversation history. Each section is rendered as a compact system message
 * block injected at request time, so repeated tool calls produce no duplicate
 * knowledge in the transcript.
 *
 * ## Sections
 *
 * | Section       | Content                                      | Source tool(s)          |
 * |---------------|----------------------------------------------|-------------------------|
 * | `§keys`       | Compact directory line per rule key          | `list_rule_keys`         |
 * | `§keyContexts`| Per-key bindings and object refs             | `get_rule_context`       |
 * | `§objects`    | Shared script-object method signatures       | `get_script_object_api` |
 *
 * **One section, one writer.** `§objects` is written only by
 * `get_script_object_api` — a second writer rendering the same object with a
 * poorer level of detail would overwrite the full method signatures and make
 * the section's version oscillate. `get_rule_context` only references object
 * ids (see [Entry] consumers).
 *
 * ## Deduplication
 *
 * An upsert is a no-op when the content is already present — either under the
 * same id (id-level) or under any other id in the same section (content-level).
 * The content-level layer is what keeps an alias (`doc.param`) from inserting a
 * second copy of the canonical key's line (`param.doc`).
 */
class KnowledgeState {

    private val sections = linkedMapOf<String, Section>()

    /**
     * One entry in a section — identified by [id], deduplicated by the hash of
     * [renderedLine].
     *
     * [contentHash] is **derived, never supplied**. Callers used to pass it by
     * hand (`renderedLine.hashCode()`), which silently broke dedup whenever a
     * caller forgot it or passed a constant — now the type guarantees it.
     */
    data class Entry(val id: String, val renderedLine: String) {
        val contentHash: Int get() = renderedLine.hashCode()
    }

    /**
     * A named section containing ordered entries.
     *
     * Indexed twice: by id ([entries], the rendered order) and by content hash
     * ([contentIndex]) so an id whose content is already rendered does not add
     * a second copy of the same line.
     */
    class Section(val title: String) {
        val entries = LinkedHashMap<String, Entry>()

        /** contentHash → ids carrying that content (rendered or deduped). */
        private val contentIndex = LinkedHashMap<Int, LinkedHashSet<String>>()

        var version = 0

        fun hasContent(): Boolean = entries.isNotEmpty()

        /** Whether [contentHash] is already rendered in this section (any id). */
        fun hasContent(contentHash: Int): Boolean =
            contentIndex[contentHash]?.isNotEmpty() == true

        /** Insert/replace [entry], re-indexing it by id and content hash. */
        fun put(entry: Entry) {
            unindex(entry.id)
            entries[entry.id] = entry
            contentIndex.getOrPut(entry.contentHash) { linkedSetOf() }.add(entry.id)
        }

        /**
         * Record that [id] refers to content already rendered under another id,
         * without rendering a second copy of the line.
         */
        fun indexOnly(id: String, contentHash: Int) {
            unindex(id)
            contentIndex.getOrPut(contentHash) { linkedSetOf() }.add(id)
        }

        fun clear() {
            entries.clear()
            contentIndex.clear()
        }

        /** Drop [id] from every index. */
        private fun unindex(id: String) {
            entries.remove(id)
            val emptied = mutableListOf<Int>()
            contentIndex.forEach { (hash, ids) ->
                if (ids.remove(id) && ids.isEmpty()) emptied.add(hash)
            }
            emptied.forEach { contentIndex.remove(it) }
        }
    }

    /** Outcome of an [upsert] call. */
    sealed class UpsertResult {
        /** The section was modified — at least one entry was added or updated. */
        data class Changed(
            val section: String,
            val added: List<String>,
            val updated: List<String>,
            val unchanged: Int
        ) : UpsertResult()

        /**
         * Every entry was already present with the same content — no-op.
         *
         * @param unchanged how many entries were recognized as already present.
         */
        data class NoChange(val section: String, val unchanged: Int) : UpsertResult()
    }

    /**
     * Insert or update [entries] in [sectionName].
     *
     * An entry is **added** if its id is new and its content is not yet in the
     * section, **updated** if its id exists but [Entry.contentHash] differs, or
     * **unchanged** if the content already matches — either under the same id
     * or, via the content index, under a different one. On any change, the
     * section's version is incremented.
     *
     * @param sectionName The section key (e.g. [SECTION_KEYS], [SECTION_OBJECTS]).
     * @param entries Entries to upsert (order preserved per section).
     * @return [Changed] with the change counts, or [NoChange] if nothing changed.
     */
    fun upsert(sectionName: String, entries: List<Entry>): UpsertResult {
        val section = sections.getOrPut(sectionName) { Section(sectionName) }
        val added = mutableListOf<String>()
        val updated = mutableListOf<String>()
        var unchanged = 0

        entries.forEach { entry ->
            val existing = section.entries[entry.id]
            when {
                existing == null && section.hasContent(entry.contentHash) -> {
                    // Same content already rendered under another id (e.g. an
                    // alias resolved to its canonical key) — dedup by content
                    // instead of emitting a duplicate line.
                    section.indexOnly(entry.id, entry.contentHash)
                    unchanged++
                }
                existing == null -> {
                    section.put(entry)
                    added.add(entry.id)
                }
                existing.contentHash != entry.contentHash -> {
                    section.put(entry)
                    updated.add(entry.id)
                }
                else -> unchanged++
            }
        }

        if (added.isNotEmpty() || updated.isNotEmpty()) {
            section.version++
            return UpsertResult.Changed(sectionName, added, updated, unchanged)
        }
        return UpsertResult.NoChange(sectionName, unchanged)
    }

    /**
     * Remove all entries in [sectionName] and increment its version.
     * Use when enablement changes make the cached knowledge stale.
     */
    fun invalidate(sectionName: String) {
        val section = sections[sectionName] ?: return
        section.clear()
        section.version++
    }

    /**
     * Return the current version of [sectionName], or `0` if the section
     * has never been upserted.
     */
    fun sectionVersion(sectionName: String): Int =
        sections[sectionName]?.version ?: 0

    /** Snapshot of the rendered entries in [sectionName] (empty when unknown). */
    fun entries(sectionName: String): List<Entry> =
        sections[sectionName]?.entries?.values?.toList() ?: emptyList()

    /** Whether any section has content. */
    fun hasContent(): Boolean = sections.any { (_, s) -> s.hasContent() }

    /**
     * Render all sections with content into a compact system-message block.
     *
     * Format:
     * ```
     * === Knowledge State ===
     *
     * §keys (v:1)
     * key1 | general | Compact summary
     * key2 | postman | Another summary
     *
     * §objects (v:1)
     * ### object: logger
     * ...methods...
     * ```
     *
     * Returns an empty string when no section has content.
     */
    fun render(): String {
        if (!hasContent()) return ""

        val sb = StringBuilder()
        sb.append("=== Knowledge State ===\n\n")

        sections.forEach { (name, section) ->
            if (!section.hasContent()) return@forEach
            sb.append("$name (v:${section.version})\n")
            section.entries.values.forEach { entry ->
                sb.append(entry.renderedLine).append("\n")
            }
            sb.append("\n")
        }

        return sb.toString().trimEnd()
    }

    /**
     * Rough token estimate for the rendered state block.
     * Uses the same heuristic as [com.itangcent.easyapi.core.ai.agent.estimatedTokens]
     * (~4 chars per token).
     */
    fun estimatedTokens(): Int {
        val text = render()
        if (text.isEmpty()) return 0
        return (text.length + 3) / 4
    }

    /**
     * Remove all content from all sections and reset versions.
     */
    fun clear() {
        sections.clear()
    }

    companion object {
        /** Directory line per rule key — written by `list_rule_keys`. */
        const val SECTION_KEYS = "§keys"

        /** Per-key bindings / object refs — written by `get_rule_context`. */
        const val SECTION_KEY_CONTEXTS = "§keyContexts"

        /** Script-object method signatures — written by `get_script_object_api`. */
        const val SECTION_OBJECTS = "§objects"
    }
}
