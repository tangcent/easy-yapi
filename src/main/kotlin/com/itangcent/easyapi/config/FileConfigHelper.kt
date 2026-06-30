package com.itangcent.easyapi.config

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.logging.IdeaLog
import java.nio.file.Files
import java.nio.file.Path

private object FileConfigHelperLog : IdeaLog

/**
 * Parses a list of local files into a sequence of [ConfigEntry] objects.
 *
 * Reads each file and delegates parsing to this [ConfigTextParser].
 * Unreadable files are skipped with a warning — never thrown.
 *
 * This is the shared building block used by [GlobalFileConfigSource] and
 * [ProjectFileConfigSource] for the read + parse step.
 */
suspend fun ConfigTextParser.parseFiles(
    files: List<Path>,
    sourceId: String
): Sequence<ConfigEntry> {
    if (files.isEmpty()) return emptySequence()
    val result = ArrayList<ConfigEntry>()
    for (file in files) {
        val content = runCatching { Files.readString(file, Charsets.UTF_8) }
            .onFailure { FileConfigHelperLog.LOG.warn("parseFiles: failed to read $file", it) }
            .getOrNull() ?: continue
        result += parse(content, sourceId, file.parent?.toString())
    }
    return result.asSequence()
}
