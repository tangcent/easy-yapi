package com.itangcent.easyapi.core.config.source

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.config.model.ConfigEntry
import com.itangcent.easyapi.core.config.model.ConfigSource
import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.config.parseFiles
import com.itangcent.easyapi.core.logging.IdeaLog
import java.nio.file.Files
import java.nio.file.Path

/**
 * Configuration source for global user rule files.
 *
 * Scans the `~/.easyapi/` folder (all regular files) and parses each one
 * as a rule file. Disabled files (paths in [disabledFiles]) are skipped.
 * The folder is the source of truth — no explicit file list is maintained.
 *
 * This source has priority 2: lower than [ExtensionConfigSource]
 * (3) so bundled framework extensions take precedence, but provides
 * user-managed global defaults that other project-specific sources override.
 *
 * File reading and parsing is delegated to [parseFiles].
 *
 * @param project The IntelliJ project, used to obtain [ConfigTextParser]
 * @param globalDir The `~/.easyapi` directory to scan
 * @param disabledFiles Absolute paths of files to skip (in [disabledGlobalRuleFiles])
 */
class GlobalFileConfigSource(
    private val project: Project,
    private val globalDir: Path,
    private val disabledFiles: Set<String>
) : ConfigSource {
    companion object : IdeaLog {

        /**
         * Lists every regular file directly inside [globalDir]. Exposed so the
         * Global sub-tab can list files without instantiating a [ConfigSource].
         * Returns an empty list if the directory does not exist.
         */
        fun listFiles(globalDir: Path): List<Path> {
            if (!Files.isDirectory(globalDir)) return emptyList()
            return Files.list(globalDir).use { stream ->
                stream.filter(Files::isRegularFile).sorted().toList()
            }
        }
    }

    override val priority: Int = 2

    override val sourceId: String = "global-file"

    override suspend fun collect(): Sequence<ConfigEntry> {
        val files = listFiles(globalDir).filter {
            it.toAbsolutePath().toString() !in disabledFiles
        }
        return ConfigTextParser.getInstance(project).parseFiles(files, sourceId)
    }
}
