package com.itangcent.easyapi.util.ide

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.itangcent.easyapi.core.threading.read

/**
 * Utility object for resolving IntelliJ module information.
 * 
 * Provides methods to find modules for PSI elements and files,
 * and extract module names and paths.
 */
object ModuleHelper {
    /**
     * Resolves the IntelliJ module for a PSI element.
     * 
     * @param psiElement The PSI element
     * @return The containing module, or null if not found
     */
    suspend fun resolveModule(psiElement: PsiElement): Module? = read {
        ModuleUtilCore.findModuleForPsiElement(psiElement)
    }

    /**
     * Resolves the module name for a PSI element.
     * 
     * @param psiElement The PSI element
     * @return The module name, or null if not found
     */
    suspend fun resolveModuleName(psiElement: PsiElement): String? = read { resolveModule(psiElement)?.name }

    /**
     * Resolves the module's content root path for a PSI element.
     * 
     * @param psiElement The PSI element
     * @return The module path, or null if not found
     */
    suspend fun resolveModulePath(psiElement: PsiElement): String? = read {
        resolveModule(psiElement)?.let { mod ->
            com.intellij.openapi.roots.ModuleRootManager.getInstance(mod)
                .contentRoots.firstOrNull()?.path
        }
    }

    /**
     * Resolves the IntelliJ module for a virtual file.
     * 
     * @param file The virtual file
     * @param project The project context
     * @return The containing module, or null if not found
     */
    suspend fun resolveModule(file: VirtualFile, project: Project): Module? = read {
        ModuleUtilCore.findModuleForFile(file, project)
    }

    /**
     * Extracts a module name from a file path.
     * Uses heuristics to find the module name from source paths.
     * 
     * @param path The file path
     * @return The inferred module name, or null
     */
    fun resolveModuleNameByPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        var currentPath = path.replace('\\', '/')
        val cutAt = listOf("/src/", "/main/", "/java/", "/kotlin/", "/scala/")
            .firstOrNull { currentPath.contains(it) }
        if (cutAt != null) currentPath = currentPath.substringBefore(cutAt)
        return currentPath.substringAfterLast('/', missingDelimiterValue = currentPath).ifBlank { null }
    }
}
