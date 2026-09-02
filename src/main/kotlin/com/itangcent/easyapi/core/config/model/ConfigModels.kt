package com.itangcent.easyapi.core.config.model

import com.itangcent.easyapi.core.config.parser.DirectiveSnapshot

/**
 * A single configuration entry from a [ConfigSource].
 *
 * @property key The configuration key (may include a `[filter]` suffix, e.g.
 *   `class.name[groovy:...]`; see [filter]).
 * @property value The configuration value
 * @property sourceId Identifier of the source that provided this entry
 * @property directives Directive settings active when this entry was parsed
 * @property lineNo 1-based source line where the entry starts, when the entry
 *   was parsed from a text file. `null` for entries synthesized outside the
 *   line-oriented text parser (e.g. programmatic sources).
 */
data class ConfigEntry(
    val key: String,
    val value: String,
    val sourceId: String,
    val directives: DirectiveSnapshot = DirectiveSnapshot(),
    val lineNo: Int? = null
)

/**
 * Represents a source of configuration entries.
 * 
 * Sources with higher [priority] values should be processed first,
 * meaning their values take precedence over sources with lower priority.
 */
interface ConfigSource {
    /** 
     * The priority of this source. Higher values mean higher priority.
     * Sources are processed in descending order of priority.
     */
    val priority: Int

    val sourceId: String

    suspend fun collect(): Sequence<ConfigEntry>
}

/**
 * The `[filter]` expression embedded in this entry's [ConfigEntry.key], or
 * `null` when the key carries no filter suffix. Mirrors the split performed
 * by [com.itangcent.easyapi.core.rule.RuleProvider] at rule-load time.
 */
fun ConfigEntry.filter(): String? {
    val bracketStart = key.indexOf('[')
    if (bracketStart < 0 || !key.endsWith("]")) return null
    val filter = key.substring(bracketStart + 1, key.length - 1).trim()
    return filter.ifEmpty { null }
}

/** The bare key with any `[filter]` suffix removed. */
fun ConfigEntry.bareKey(): String {
    val bracketStart = key.indexOf('[')
    return if (bracketStart < 0) key else key.substring(0, bracketStart)
}

/** `true` when this entry's value is a groovy script (starts with `groovy:`). */
fun ConfigEntry.isGroovyValue(): Boolean = value.startsWith("groovy:")

/** `true` when this entry's filter is a groovy script (starts with `groovy:`). */
fun ConfigEntry.isGroovyFilter(): Boolean = filter()?.startsWith("groovy:") == true
