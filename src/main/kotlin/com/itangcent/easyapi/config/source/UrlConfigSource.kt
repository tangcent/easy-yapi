package com.itangcent.easyapi.config.source

import com.intellij.openapi.project.Project
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
 * @param project The IntelliJ project, used to obtain [CachedResourceResolver]
 */
class UrlConfigSource(
    private val urls: List<String>,
    private val configTextParser: ConfigTextParser,
    private val project: Project
) : ConfigSource {
    override val priority: Int = 3
    override val sourceId: String = "remote"

    override suspend fun collect(): Sequence<ConfigEntry> {
        if (urls.isEmpty()) return emptySequence()
        val resolver = CachedResourceResolver.getInstance(project)
        val result = ArrayList<ConfigEntry>()
        for (url in urls) {
            val content = resolver.get(url) ?: continue
            val baseDir = url.substringBeforeLast('/', missingDelimiterValue = url)
            result += configTextParser.parse(content, sourceId, baseDir)
        }
        return result.asSequence()
    }
}
