package com.itangcent.easyapi.core.config.source

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves and validates rule-file paths for the AI agent's `read_rule_file`
 * tool.
 *
 * Computes the set of directories from which the tool may read:
 * - The `~/.easyapi/` folder (global rules source of truth).
 * - The `<base>/.easyapi/` folder (project rules source of truth).
 * - Parent directories of legacy `.easy.api.config*` files (walk-up).
 *
 * The tool normalises the requested path and checks
 * `allowedDirs.any { path.startsWith(it) }`, refusing otherwise.
 */
class RuleFileResolver(private val project: Project) {

    /**
     * The directories the agent is allowed to read rule files from.
     */
    fun allowedDirs(): List<Path> {
        val dirs = mutableListOf<Path>()

        // Global rule files live in ~/.easyapi/
        dirs.add(globalDir())

        // Project rule files:.easyapi/ folder + parent dirs of legacy walk-up files.
        dirs.addAll(projectDirs())

        return dirs.distinct()
    }

    /**
     * Resolve [requestedPath] against the allowed directories.
     *
     * @return The resolved, normalised [Path] if it is inside an allowed dir,
     * or `null` if access is refused.
     */
    fun resolve(requestedPath: String): Path? {
        val candidate = Paths.get(requestedPath).toAbsolutePath().normalize()
        return allowedDirs().firstOrNull { candidate.startsWith(it) }?.let { candidate }
    }

    /**
     * Resolve a rule file by **name** (optionally scope-prefixed), searching
     * the tracked rule directories.
     *
     * Accepts:
     * - `global:<name>` — search only the `~/.easyapi/` folder.
     * - `project:<name>` — search the project `.easyapi/` folder and the
     * parent dirs of legacy walk-up files.
     * - `<name>` — search every tracked directory in priority order.
     *
     * `<name>` is treated as a bare file name (no directory components); a
     * name containing path separators is rejected to prevent escaping a
     * tracked directory. Returns the first regular-file match, or `null` when
     * no such file exists. This lets the AI agent read a rule file without
     * ever knowing the user's home directory.
     */
    fun resolveByName(name: String): Path? {
        val (scope, fileName) = when {
            name.startsWith("global:") -> ScopeFilter.GLOBAL to name.removePrefix("global:")
            name.startsWith("project:") -> ScopeFilter.PROJECT to name.removePrefix("project:")
            else -> ScopeFilter.ANY to name
        }
        // Reject anything that looks like a path — a rule file is addressed by
        // its bare name inside a tracked directory.
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return null
        val dirs = when (scope) {
            ScopeFilter.GLOBAL -> listOf(globalDir())
            ScopeFilter.PROJECT -> projectDirs()
            ScopeFilter.ANY -> allowedDirs()
        }.filterNotNull()
        return dirs.firstNotNullOfOrNull { dir ->
            val candidate = dir.resolve(fileName).toAbsolutePath().normalize()
            // Confirm it stays inside the dir (defensive) and is a regular file.
            if (candidate.startsWith(dir) && Files.isRegularFile(candidate)) candidate else null
        }
    }

    private enum class ScopeFilter { GLOBAL, PROJECT, ANY }

    /** The global `~/.easyapi/` rule directory. */
    private fun globalDir(): Path =
        Paths.get(System.getProperty("user.home"), ".easyapi").toAbsolutePath().normalize()

    /** Project-scoped tracked dirs: `.easyapi/` + parents of legacy walk-up files. */
    private fun projectDirs(): List<Path> {
        val projectBasePath = project.basePath ?: return emptyList()
        val dirs = mutableListOf<Path>()
        dirs.add(Paths.get(projectBasePath, ".easyapi").toAbsolutePath().normalize())
        ProjectFileConfigSource.legacyFiles(projectBasePath)
.mapNotNull { it.parent }
.forEach { parent -> dirs.add(parent.toAbsolutePath().normalize()) }
        return dirs.distinct()
    }

    /**
     * Lists the names of existing rule files across all [allowedDirs], for the
     * AI agent's ambient perception. Accepts both `.properties` and `.rules`
     * (the two extensions [ProjectFileConfigSource.easyapiFolderFiles] loads),
     * so the agent's ambient list matches the files actually in effect.
     *
     * Missing or unreadable directories are skipped silently.
     */
    fun listRuleFiles(): List<String> {
        return allowedDirs().flatMap { dir ->
            runCatching {
                if (!Files.isDirectory(dir)) emptyList()
                else Files.list(dir).use { stream ->
                    stream.filter { Files.isRegularFile(it) }
.map { it.fileName.toString() }
.filter { it.endsWith(".properties") || it.endsWith(".rules") }
.toList()
                }
            }.getOrDefault(emptyList())
        }.distinct().sorted()
    }
}
