package com.itangcent.easyapi.core.ai.tools

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.core.psi.type.TypeResolver

/**
 * Resolves class simple names / FQNs to [PsiClass] instances and normalizes
 * the `context` argument supplied by AI tools to a [PsiElement] suitable for
 * import-scope resolution.
 *
 * Three resolution strategies are supported for class lookup:
 * - **FQN path** (name contains `.`): [JavaPsiFacade.findClass] in
 *   [GlobalSearchScope.allScope].
 * - **Simple name + context**: [TypeResolver.resolveFromCanonicalText] using
 *   the context element's import scope.
 * - **Simple name, no context**: [PsiShortNamesCache.getClassesByName] in
 *   [GlobalSearchScope.allScope]. Returns the single match, or `null` with a
 *   `LOG.info` decision log when zero or >1 matches are found.
 *
 * All PSI access is performed inside a read action (NFR-1). Logging follows
 * NFR-2 — `LOG.info` is the floor; no arguments or result bodies are logged
 * beyond the identifier needed to explain a decision.
 */
internal object PsiNameResolver : IdeaLog {

    /**
     * Resolves [name] to a single [PsiClass], or `null` when the name is
     * blank, ambiguous (more than one match in the no-context simple-name
     * path), or simply not found.
     *
     * @param name simple name (e.g. `"User"`) or FQN (e.g. `"com.example.User"`).
     * @param project the IntelliJ project used for PSI lookups.
     * @param contextElement optional [PsiElement] whose import scope is used
     *   to resolve simple names (typically the [com.intellij.psi.PsiFile]
     *   returned by [resolveContextElement]).
     * @return the resolved [PsiClass], or `null`.
     */
    suspend fun resolveClass(
        name: String,
        project: Project,
        contextElement: PsiElement? = null
    ): PsiClass? = read {
        if (name.isBlank()) return@read null

        // FQN path — direct lookup in all scope.
        if (name.contains('.')) {
            return@read JavaPsiFacade.getInstance(project)
                .findClass(name, GlobalSearchScope.allScope(project))
        }

        // Simple name + context — use TypeResolver's 4-step import resolution.
        if (contextElement != null) {
            val resolved = TypeResolver.resolveFromCanonicalText(name, project, contextElement)
            (resolved as? ResolvedType.ClassType)?.psiClass?.let { return@read it }
        }

        // Simple name, no context (or context didn't resolve) — short-names cache.
        val matches = PsiShortNamesCache.getInstance(project)
            .getClassesByName(name, GlobalSearchScope.allScope(project))
        when {
            matches.isEmpty() -> {
                LOG.info("no class found for '$name'")
                null
            }
            matches.size == 1 -> matches[0]
            else -> {
                LOG.info("ambiguous: '$name' (${matches.size} matches)")
                null
            }
        }
    }

    /**
     * Resolves [name] to all matching [PsiClass] instances.
     *
     * Unlike [resolveClass], this never returns `null` for ambiguity — it
     * returns every match so the caller can probe each candidate (used by
     * `find_classes_by_annotation` / `find_classes_by_supertype` for
     * tolerance resolution).
     *
     * @param name simple name or FQN.
     * @param project the IntelliJ project used for PSI lookups.
     * @param contextElement optional [PsiElement] whose import scope is used
     *   to prefer a context-reachable match. If the context nails the
     *   resolution, only that single class is returned.
     * @return a list of matching [PsiClass] instances (possibly empty).
     */
    suspend fun resolveAllClasses(
        name: String,
        project: Project,
        contextElement: PsiElement? = null
    ): List<PsiClass> = read {
        if (name.isBlank()) return@read emptyList()

        // FQN path — single-or-empty.
        if (name.contains('.')) {
            return@read listOfNotNull(
                JavaPsiFacade.getInstance(project)
                    .findClass(name, GlobalSearchScope.allScope(project))
            )
        }

        // Simple name + context — if context nails it, return just that class.
        if (contextElement != null) {
            val resolved = TypeResolver.resolveFromCanonicalText(name, project, contextElement)
            if (resolved is ResolvedType.ClassType) {
                return@read listOf(resolved.psiClass)
            }
        }

        // Simple name, no context → all matches.
        PsiShortNamesCache.getInstance(project)
            .getClassesByName(name, GlobalSearchScope.allScope(project))
            .toList()
    }

    /**
     * Normalizes the `context` argument supplied by AI tools to a [PsiElement]
     * suitable for import-scope resolution.
     *
     * Tries, in order:
     * 1. **Class FQN** → [PsiClass.getContainingFile] via [JavaPsiFacade.findClass].
     * 2. **Absolute file path** → [com.intellij.psi.PsiFile] via
     *    [LocalFileSystem] + [PsiManager].
     * 3. **Project-relative file path** → [com.intellij.psi.PsiFile] via
     *    the project base path + [PsiManager].
     *
     * Returns `null` and logs a decision `LOG.info` when [context] cannot be
     * resolved by any path; the caller falls back to the no-context path.
     *
     * @param context a class FQN or file path (absolute or project-relative).
     * @param project the IntelliJ project.
     * @return a [PsiElement] (typically a [com.intellij.psi.PsiFile]) usable
     *   as the `contextElement` for [resolveClass] / [resolveAllClasses], or
     *   `null`.
     */
    suspend fun resolveContextElement(
        context: String,
        project: Project
    ): PsiElement? = read {
        // 1. Try as class FQN.
        JavaPsiFacade.getInstance(project)
            .findClass(context, GlobalSearchScope.allScope(project))
            ?.let { return@read it.containingFile }

        // 2. Try as absolute file path.
        LocalFileSystem.getInstance().findFileByPath(context)?.let { vf ->
            PsiManager.getInstance(project).findFile(vf)?.let { return@read it }
        }

        // 3. Try as project-relative file path.
        project.basePath?.let { basePath ->
            LocalFileSystem.getInstance().findFileByPath(basePath)?.findFileByRelativePath(context)
        }?.let { vf ->
            PsiManager.getInstance(project).findFile(vf)?.let { return@read it }
        }

        // 4. Not resolvable — log the decision and return null so the caller
        //    falls back to the no-context path.
        LOG.info("context not resolvable: $context")
        null
    }

    /**
     * Resolves the optional `context` argument supplied by AI tools to a
     * [PsiElement] for import-scope resolution. Non-string values are logged
     * and treated as `null` (per REQ-4 AC-5).
     *
     * Shared by tools that accept a `context` parameter
     * ([GetPsiClassInfoTool], [GetPsiMethodInfoTool], [FindClassesByAnnotationTool],
     * [FindClassesBySupertypeTool], [FindClassesByNameTool]).
     *
     * @param args the tool's argument map.
     * @param project the IntelliJ project.
     * @return the resolved [PsiElement], or `null` when the argument is absent,
     *   blank, non-string, or unresolvable.
     */
    suspend fun resolveContextArg(
        args: Map<String, Any?>,
        project: Project
    ): PsiElement? {
        val raw = args["context"] ?: return null
        if (raw !is String) {
            LOG.info("context argument ignored: non-string value")
            return null
        }
        if (raw.isBlank()) return null
        return resolveContextElement(raw, project)
    }

    /**
     * Extracts a string list from a tool's argument map, supporting both
     * single-value (`[singleKey]` → string) and batch (`[batchKey]` → array)
     * modes. Blank entries are filtered out.
     *
     * Shared by tools that accept single-or-batch name/FQN parameters
     * ([GetPsiClassInfoTool], [FindClassesByAnnotationTool],
     * [FindClassesBySupertypeTool], [FindClassesByNameTool],
     * [GetExistingRulesForKeyTool]).
     *
     * @param args the tool's argument map.
     * @param singleKey the key for the single-value form (e.g. `"fqn"`, `"name"`).
     * @param batchKey the key for the batch form (e.g. `"fqns"`, `"names"`).
     *   When present, takes precedence over [singleKey].
     * @return the non-blank string list (possibly empty).
     */
    fun extractStringList(
        args: Map<String, Any?>,
        singleKey: String,
        batchKey: String
    ): List<String> {
        val batch = args[batchKey] as? List<*>
        if (batch != null) {
            return batch.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }
        }
        val single = args[singleKey] as? String
        return if (single.isNullOrBlank()) emptyList() else listOf(single)
    }
}
