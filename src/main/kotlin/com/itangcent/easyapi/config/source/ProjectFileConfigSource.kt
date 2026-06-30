package com.itangcent.easyapi.config.source

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource
import com.itangcent.easyapi.config.parser.ConfigTextParser
import com.itangcent.easyapi.config.parseFiles
import com.itangcent.easyapi.logging.IdeaLog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Configuration source for project configuration files.
 *
 * Reads from two layers:
 * - **`.easyapi/` folder** — every regular file directly inside
 *   `<projectBasePath>/.easyapi/` (the source of truth for plugin-managed
 *   project rules; editable/renamable/removable from the Rules tab).
 * - **Legacy walk-up** — `.easy.api.config[.properties|.yml|.yaml]` files
 *   discovered by walking up from [projectBasePath]. These remain
 *   read-only/toggle-only in the UI.
 *
 * Auto-detected files (both layers) whose absolute path is in
 * [disabledFiles] are skipped.
 *
 * This source has the highest priority (4) so project-specific rules win
 * over extension, remote, and global sources.
 *
 * File reading and parsing is delegated to [parseFiles].
 *
 * @param project The IntelliJ project, used to obtain [ConfigTextParser]
 * @param projectBasePath The project base directory path
 * @param disabledFiles Absolute paths of files to skip
 */
class ProjectFileConfigSource(
    private val project: Project,
    private var projectBasePath: String,
    private val disabledFiles: Set<String> = emptySet()
) : ConfigSource {
    companion object : IdeaLog {

        /**
         * Lists every regular file directly inside `<projectBasePath>/.easyapi/`.
         * Exposed so the Project sub-tab can list editable rule files without
         * instantiating a [ConfigSource]. Returns an empty list if the
         * directory does not exist.
         */
        fun easyapiFolderFiles(projectBasePath: String): List<Path> {
            val folder = Paths.get(projectBasePath, ".easyapi")
            if (!Files.isDirectory(folder)) return emptyList()
            return Files.list(folder).use { stream ->
                stream.filter(Files::isRegularFile).sorted().toList()
            }
        }

        /**
         * Detects legacy `.easy.api.config[.properties|.yml|.yaml]` files by
         * walking up from [projectBasePath]. Exposed so the Project sub-tab
         * can list legacy files (read-only/toggle-only) separately from the
         * `.easyapi/` folder files.
         */
        fun legacyFiles(projectBasePath: String): List<Path> {
            val result = ArrayList<Path>()
            var current: Path? = Paths.get(projectBasePath)
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

    override val priority: Int = 4

    override val sourceId: String = "project"

    override suspend fun collect(): Sequence<ConfigEntry> {
        val configFiles = (easyapiFolderFiles(projectBasePath) + legacyFiles(projectBasePath))
            .filter { it.toAbsolutePath().toString() !in disabledFiles }

        return ConfigTextParser.getInstance(project).parseFiles(configFiles, sourceId)
    }

    fun setProjectBasePath(path: String) {
        this.projectBasePath = path
    }
}
