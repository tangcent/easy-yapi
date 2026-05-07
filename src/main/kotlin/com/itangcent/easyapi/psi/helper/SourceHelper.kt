package com.itangcent.easyapi.psi.helper

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMember
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.itangcent.easyapi.core.threading.read
import java.io.File
import java.util.*

/**
 * Resolves compiled JAR classes to their source counterparts for documentation extraction.
 *
 * When working with VO/DTO classes from external JAR dependencies, IntelliJ loads them
 * as compiled classes ([ClsClassImpl]) which don't have direct access to Javadoc comments.
 * This helper resolves these compiled classes to their source versions when source JARs
 * are attached, enabling proper documentation extraction.
 *
 * ## Resolution Strategy
 *
 * 1. **Local classes**: Return as-is (already in source form)
 * 2. **Cached resolution**: Return previously resolved source class if available
 * 3. **Navigation element**: Use IntelliJ's built-in navigation for decompiled classes
 * 4. **Source JAR search**: Search attached source roots for matching source files
 *
 * ## Usage
 *
 * ```kotlin
 * val sourceHelper = SourceHelper.getInstance(project)
 *
 * // Resolve a class from JAR to source
 * val sourceClass = sourceHelper.getSourceClassSync(jarClass)
 *
 * // Resolve any element (class, field, method) to source
 * val sourceElement = sourceHelper.getSourceElementSync(compiledElement)
 * ```
 *
 * ## Thread Safety
 *
 * - [getSourceClass] and [getSourceElement] are suspend functions that wrap PSI access
 *   in [read] blocks for [ReadAction](https://plugins.jetbrains.com/docs/intellij/threading-model.html) compliance.
 * - [getSourceClassSync] and [getSourceElementSync] are synchronous versions for use
 *   when already inside a read action context.
 *
 * ## Performance
 *
 * - Resolved source classes are cached in the PSI element's user data to avoid repeated lookups
 * - Source JAR search is skipped during dumb mode to prevent indexing conflicts
 *
 * @see ClsClassImpl IntelliJ's representation of compiled classes from JARs
 * @see UnifiedDocHelper Uses SourceHelper for documentation extraction from JAR classes
 * @see LinkResolver Uses SourceHelper for link resolution from JAR classes
 */
@Service(Service.Level.PROJECT)
class SourceHelper(private val project: Project) {

    companion object {
        /**
         * Cache key for storing resolved source classes on the original compiled class.
         * This avoids repeated source resolution for the same class during an operation.
         */
        private val SOURCE_ELEMENT = Key.create<PsiClass>("EasyApi.SOURCE_ELEMENT")

        /**
         * Get the SourceHelper instance for a project.
         *
         * @param project The IntelliJ project
         * @return The SourceHelper service instance
         */
        fun getInstance(project: Project): SourceHelper = project.service()
    }

    /**
     * Resolves a compiled class to its source counterpart (suspend version).
     *
     * This is the preferred method when calling from a coroutine context.
     * It wraps the PSI access in a read action automatically.
     *
     * @param original The potentially compiled PsiClass
     * @return The source PsiClass if found, otherwise the original class
     */
    suspend fun getSourceClass(original: PsiClass): PsiClass {
        return read {
            tryGetSourceClass(original)
        }
    }

    /**
     * Resolves a compiled class to its source counterpart (synchronous version).
     *
     * Use this method when already inside a read action context.
     * If not in a read action, consider using [getSourceClass] instead.
     *
     * @param original The potentially compiled PsiClass
     * @return The source PsiClass if found, otherwise the original class
     */
    fun getSourceClassSync(original: PsiClass): PsiClass {
        return tryGetSourceClass(original)
    }

    /**
     * Internal implementation for resolving a class to source.
     *
     * Resolution order:
     * 1. Local inner classes - return as-is (already source)
     * 2. Cached result - return previously resolved source class
     * 3. Navigation element - use IntelliJ's built-in source navigation
     * 4. Source JAR search - find source file in attached source roots
     *
     * @param original The potentially compiled PsiClass
     * @return The source PsiClass if found, otherwise the original class
     */
    private fun tryGetSourceClass(original: PsiClass): PsiClass {
        try {
            if (isLocalClass(original)) {
                return original
            }

            val cached = original.getUserData(SOURCE_ELEMENT)
            if (cached != null && cached.isValid) {
                return cached
            }

            if (original is ClsClassImpl) {
                val navigationElement = original.navigationElement
                if (navigationElement != original && navigationElement is PsiClass) {
                    return navigationElement
                }
            }

            if (!DumbService.isDumb(project)) {
                val vFile = original.containingFile?.virtualFile ?: return original
                val idx = ProjectRootManager.getInstance(project).fileIndex
                if (idx.isInLibraryClasses(vFile)) {
                    val sourceRootForFile = idx.getSourceRootForFile(vFile)
                    if (sourceRootForFile != null) {
                        tryFindSourceClass(sourceRootForFile, original)?.let { return it }
                    }

                    val orderEntriesForFile = idx.getOrderEntriesForFile(vFile)
                    orderEntriesForFile.asSequence()
                        .flatMap { it.getFiles(OrderRootType.SOURCES).asSequence() }
                        .distinct()
                        .map { tryFindSourceClass(it, original) }
                        .firstOrNull()
                        ?.let { return it }
                }
            }
        } catch (_: Exception) {
        }

        return original
    }

    /**
     * Resolves any PSI element to its source counterpart (suspend version).
     *
     * Handles classes, fields, and methods by:
     * 1. For PsiClass: delegates to [getSourceClass]
     * 2. For PsiMember (field/method): resolves the containing class, then finds the member
     * 3. For other elements: uses navigationElement fallback
     *
     * @param element The potentially compiled PSI element
     * @return The source element if found, otherwise the original element
     */
    suspend fun getSourceElement(element: PsiElement): PsiElement {
        return read {
            tryGetSourceElement(element)
        }
    }

    /**
     * Resolves any PSI element to its source counterpart (synchronous version).
     *
     * Use this method when already inside a read action context.
     * If not in a read action, consider using [getSourceElement] instead.
     *
     * @param element The potentially compiled PSI element
     * @return The source element if found, otherwise the original element
     */
    fun getSourceElementSync(element: PsiElement): PsiElement {
        return tryGetSourceElement(element)
    }

    /**
     * Internal implementation for resolving any element to source.
     *
     * @param element The potentially compiled PSI element
     * @return The source element if found, otherwise the original element
     */
    private fun tryGetSourceElement(element: PsiElement): PsiElement {
        if (element is PsiClass) {
            return tryGetSourceClass(element)
        }

        if (element is PsiMember) {
            val containingClass = element.containingClass ?: return element
            val sourceClass = tryGetSourceClass(containingClass)
            if (sourceClass !== containingClass) {
                return when (element) {
                    is PsiField -> sourceClass.findFieldByName(element.name, false) ?: element
                    is PsiMethod -> sourceClass.findMethodsByName(element.name, false)
                        .firstOrNull() as? PsiMethod ?: element
                    else -> element
                }
            }
        }

        val navigationElement = element.navigationElement
        return if (navigationElement != element && navigationElement.isValid) {
            navigationElement
        } else {
            element
        }
    }

    /**
     * Checks if a class is a local inner class (not from a JAR).
     *
     * Local inner classes are defined inside source files and don't need
     * source resolution. This check avoids unnecessary lookups.
     *
     * @param psiClass The class to check
     * @return true if the class is a local inner class in source form
     */
    private fun isLocalClass(psiClass: PsiClass): Boolean {
        return psiClass.containingClass != null && psiClass.containingClass !is ClsClassImpl
    }

    /**
     * Searches for a source class in a source root directory.
     *
     * Looks for a Java file matching the class's qualified name and caches
     * the result on the original class for future lookups.
     *
     * @param sourceRoot The virtual file representing the source root
     * @param original The compiled class to find source for
     * @return The source PsiClass if found, null otherwise
     */
    private fun tryFindSourceClass(
        sourceRoot: VirtualFile,
        original: PsiClass
    ): PsiClass? {
        val qualifiedName = original.qualifiedName ?: return null
        val find = findPsiFileInRoot(sourceRoot, qualifiedName)
            ?: sourceRoot.findChild(qualifiedName)
        if (find != null && find is PsiJavaFile) {
            find.classes.forEach {
                if (it.qualifiedName == qualifiedName) {
                    original.putUserData(SOURCE_ELEMENT, it)
                    return it
                }
            }
        }
        return null
    }

    /**
     * Finds a Java source file in a source root by class qualified name.
     *
     * Uses a depth-first search to locate the file, handling nested packages
     * and matching by qualified name to support classes that may not follow
     * standard file naming conventions.
     *
     * @param dirFile The source root directory to search in
     * @param className The fully qualified class name to find
     * @return The PsiJavaFile if found, null otherwise
     */
    private fun findPsiFileInRoot(dirFile: VirtualFile, className: String): PsiJavaFile? {
        val javaName = StringUtil.getQualifiedName(className, StdFileTypes.JAVA.defaultExtension)
        if (className.isNotEmpty()) {
            val classFile = dirFile.findChild(javaName)
            if (classFile != null) {
                val psiFile = PsiManager.getInstance(project).findFile(classFile)
                if (psiFile is PsiJavaFile) {
                    return psiFile
                }
            }
        }

        val dirs = Stack<VirtualFile>()
        var dir: VirtualFile = dirFile
        val rootPath = dirFile.path
        while (true) {
            val children = dir.children
            for (child in children) {
                if (StdFileTypes.JAVA == child.fileType && child.isValid) {
                    val psiFile = PsiManager.getInstance(project).findFile(child)
                    if (psiFile is PsiJavaFile) {
                        if (child.name == javaName) {
                            return psiFile
                        }

                        for (cls in psiFile.classes) {
                            if (cls.qualifiedName == className) {
                                return psiFile
                            }
                        }
                    }
                } else {
                    val prefix = dir.path.removePrefix(rootPath)
                        .replace(File.separatorChar, '.')
                    if (javaName.startsWith(prefix)) {
                        dirs.push(child)
                    }
                }
            }
            if (dirs.isEmpty()) {
                break
            }
            dir = dirs.pop()
        }
        return null
    }
}
