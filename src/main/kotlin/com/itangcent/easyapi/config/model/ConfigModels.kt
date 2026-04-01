package com.itangcent.easyapi.config.model

import com.itangcent.easyapi.config.parser.DirectiveSnapshot

/**
 * A single configuration entry from a [ConfigSource].
 *
 * @property key The configuration key
 * @property value The configuration value
 * @property sourceId Identifier of the source that provided this entry
 * @property directives Directive settings active when this entry was parsed
 */
data class ConfigEntry(
    val key: String,
    val value: String,
    val sourceId: String,
    val directives: DirectiveSnapshot = DirectiveSnapshot()
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
