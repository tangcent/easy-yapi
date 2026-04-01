package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.logging.IdeaLog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configuration source for local project configuration files.
 *
 * Searches for configuration files starting from the project directory
 * and walking up the directory tree. Supports multiple file formats:
 * - `.easy.api.config`
 * - `.easy.api.config.properties`
 * - `.easy.api.config.yml`
 * - `.easy.api.config.yaml`
 *
 * This source has priority 4, higher than built-in but lower than
 * recommend and remote sources.
 *
 * @param projectBasePath The project base directory path
 * @param configTextParser Parser for configuration text
 */
class LocalFileConfigSource(
    private var projectBasePath: String,
    private val configTextParser: ConfigTextParser
) : ConfigSource {
    companion object : IdeaLog

    override val priority: Int = 4

    override val sourceId: String = "local"

    override suspend fun collect(): Sequence<ConfigEntry> {
        val base = Paths.get(projectBasePath)
        val configFiles = searchConfigFiles(base)
        if (configFiles.isEmpty()) return emptySequence()

        return sequence {
            for (file in configFiles) {
                val content = runCatching { Files.readString(file, Charsets.UTF_8) }.getOrNull() ?: continue
                val entries = configTextParser.parse(content, sourceId, file.parent?.toString()).toList()
                LOG.info("Loaded ${entries.size} entries from local config file: $file")
                yieldAll(entries)
            }
        }
    }

    fun setProjectBasePath(path: String) {
        this.projectBasePath = path
    }

    private fun searchConfigFiles(start: Path): List<Path> {
        val result = ArrayList<Path>()
        var current: Path? = start
        while (current != null) {
            val candidates = listOf(
                current.resolve(".easy.api.config"),
                current.resolve(".easy.api.config.properties"),
                current.resolve(".easy.api.config.yml"),
                current.resolve(".easy.api.config.yaml")
            )
            candidates.filter { Files.exists(it) && Files.isRegularFile(it) }.forEach { result.add(it) }
            current = current.parent
        }
        return result
    }
}
