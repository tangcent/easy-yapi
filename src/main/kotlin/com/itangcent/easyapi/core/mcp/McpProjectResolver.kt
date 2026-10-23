package com.itangcent.easyapi.core.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.io.FileUtil

/** Outcome of [McpProjectResolver.resolve]. */
internal sealed class ProjectResolution {
    /** The call is for [project]. */
    data class Found(val project: Project) : ProjectResolution()

    /**
     * No single project fits.
     *
     * @param code [McpToolError.PROJECT_NOT_FOUND] or [McpToolError.PROJECT_AMBIGUOUS]
     * @param message what went wrong and what to pass instead, in English
     * @param openProjects the roots of the open projects, for the caller to pick from
     */
    data class Failed(val code: String, val message: String, val openProjects: List<String>) : ProjectResolution()
}

/**
 * Decides which open project an MCP tool call is for, from the tool's own `projectPath` argument.
 * The MCP server's project lookup is not used: its API changed between IDE versions (see [EasyApiMcpToolset]).
 * So only the argument counts; a project the caller names in a request header or in `_meta` is not seen.
 *
 * - `projectPath` given → the innermost open project whose root is that path or contains it, so a
 *   directory or file inside the project works too, as with the IDE's own tools. Separators, a trailing
 *   slash and, on a case-insensitive file system, letter case do not matter;
 * - no `projectPath` (or a blank one) and exactly one project open → that project;
 * - anything else → [ProjectResolution.Failed], listing the roots of the open projects.
 */
internal object McpProjectResolver {

    /** Resolves [projectPath] against the projects open right now. */
    fun resolve(projectPath: String?): ProjectResolution =
        resolve(projectPath, ProjectManager.getInstance().openProjects.toList())

    /** The decision itself, against [openProjects] — separate so that several open projects can be tested. */
    fun resolve(projectPath: String?, openProjects: List<Project>): ProjectResolution {
        val roots = openProjects.mapNotNull { it.basePath }
        if (projectPath.isNullOrBlank()) {
            return when (openProjects.size) {
                1 -> ProjectResolution.Found(openProjects.single())
                0 -> ProjectResolution.Failed(McpToolError.PROJECT_NOT_FOUND, "No project is open in the IDE.", roots)
                else -> ProjectResolution.Failed(
                    McpToolError.PROJECT_AMBIGUOUS,
                    "${openProjects.size} projects are open in the IDE; pass projectPath as an argument of this " +
                        "tool: the absolute path of the project's root, or of a directory or file inside it. " +
                        "A project named only in a request header or in _meta is not seen by this tool.",
                    roots
                )
            }
        }
        val wanted = normalize(projectPath)
        // The deepest root wins when open projects are nested, as in the IDE's own lookup.
        val match = openProjects
            .mapNotNull { project -> project.basePath?.let { normalize(it) to project } }
            .filter { (root, _) -> FileUtil.isAncestor(root, wanted, false) }
            .maxByOrNull { (root, _) -> root.length }
            ?.second
        return match?.let { ProjectResolution.Found(it) } ?: ProjectResolution.Failed(
            McpToolError.PROJECT_NOT_FOUND,
            "No open project contains $projectPath; pass the absolute path of an open project's root, " +
                "or of a directory or file inside it.",
            roots
        )
    }

    /** Forward slashes and no trailing slash: `C:\work\shop\` → `C:/work/shop`. */
    private fun normalize(path: String): String = FileUtil.toSystemIndependentName(path.trim()).trimEnd('/')
}
