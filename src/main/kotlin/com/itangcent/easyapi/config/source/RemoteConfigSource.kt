package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.config.resource.CachedResourceResolver

/**
 * Configuration source for remote configuration URLs.
 *
 * Fetches configuration from remote URLs with caching support via
 * [CachedResourceResolver]. This allows configuration to be centrally
 * managed and shared across projects.
 *
 * This source has priority 3, higher than built-in and recommend sources.
 *
 * @param urls List of remote configuration URLs
 * @param configTextParser Parser for configuration text
 * @param cachedResourceResolver Resolver for fetching and caching remote content
 */
class RemoteConfigSource(
    private val urls: List<String>,
    private val configTextParser: ConfigTextParser,
    private val cachedResourceResolver: CachedResourceResolver
) : ConfigSource {
    override val priority: Int = 3
    override val sourceId: String = "remote"

    override suspend fun collect(): Sequence<ConfigEntry> {
        if (urls.isEmpty()) return emptySequence()
        val contents = urls.map { url -> url to cachedResourceResolver.get(url) }
        return sequence {
            for ((url, content) in contents) {
                if (content != null) {
                    val baseDir = url.substringBeforeLast('/', missingDelimiterValue = url)
                    yieldAll(configTextParser.parse(content, sourceId, baseDir))
                }
            }
        }
    }
}
