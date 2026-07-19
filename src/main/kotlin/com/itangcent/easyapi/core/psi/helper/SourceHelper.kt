package com.itangcent.easyapi.core.psi.helper

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMember
import com.intellij.psi.impl.compiled.ClsClassImpl
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.core.internal.threading.read
import java.io.File
import java.util.*

/**
 * Resolves compiled JAR classes to their source counterparts for documentation extraction.
 *
 * When working with VO/DTO classes from external JAR dependencies, IntelliJ loads them
 * as compiled classes ([ClsClassImpl]) which don't have direct access to Javadoc/KDoc comments.
 * This helper resolves these compiled classes to their source versions when source JARs
 * are attached, enabling proper documentation extraction.
 *
 * ## Resolution Strategy
 *
 * 1. **Local classes**: Return as-is (already in source form)
 * 2. **Cached resolution**: Return previously resolved source class if available
 * 3. **Navigation element**: Use IntelliJ's built-in navigation for decompiled classes
 * 4. **Source JAR search**: Search attached source roots for matching source files
 *    (both `.java` and `.kt` files are supported via [SOURCE_EXTENSIONS])
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
class SourceHelper(private val project: Project) : com.itangcent.easyapi.core.logging.IdeaLog {

    companion object {
        /**
         * File extension for Java source files.
         *
         * Defined locally rather than using [com.intellij.openapi.fileTypes.StdFileTypes]
         * because `StdFileTypes.JAVA.defaultExtension` only provides "java" without the dot,
         * and `StdFileTypes` API may vary across IntelliJ versions. Using a simple constant
         * avoids deprecated API dependencies and keeps the extension format consistent
         * (with leading dot) for direct use in path construction.
         */
        private const val DOT_JAVA = ".java"

        /**
         * File extension for Kotlin source files.
         *
         * Kotlin source files (`.kt`) may contain classes that are referenced from JARs.
         * Including this extension enables source resolution for Kotlin classes in
         * attached source JARs, not just Java.
         */
        private const val DOT_KOTLIN = ".kt"

        /**
         * Supported source file extensions for class resolution.
         *
         * When searching for a source class in attached source roots, both `.java` and `.kt`
         * files are checked. This allows resolving source for classes compiled from either
         * language.
         */
        private val SOURCE_EXTENSIONS = listOf(DOT_JAVA, DOT_KOTLIN)

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
                    val vfm = VirtualFileManager.getInstance()
                    val sourceFiles = orderEntriesForFile.asSequence()
                        .filterIsInstance<LibraryOrderEntry>()
                        .mapNotNull { it.library }
                        .flatMap { library ->
                            library.getUrls(OrderRootType.SOURCES).asSequence()
                                .mapNotNull { url -> vfm.findFileByUrl(url) }
                        }
                        .distinct()
                    for (srcFile in sourceFiles) {
                        tryFindSourceClass(srcFile, original)?.let { return it }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.warn("SourceHelper: failed to find source class for ${original.qualifiedName}", e)
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
     * Looks for a Java or Kotlin source file matching the class's qualified name
     * (see [SOURCE_EXTENSIONS]) and caches the result on the original class for
     * future lookups via [SOURCE_ELEMENT].
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
        val sourceClass = findSourceClassInRoot(sourceRoot, qualifiedName)
        if (sourceClass != null) {
            original.putUserData(SOURCE_ELEMENT, sourceClass)
            return sourceClass
        }
        return null
    }

    /**
     * Finds a source class in a source root directory by qualified name.
     *
     * Uses a two-phase search strategy:
     *
     * 1. **Direct path lookup**: Constructs the expected relative path from the
     *    package and class name (e.g., `com/example/Foo.java` or `com/example/Foo.kt`)
     *    and checks each extension in [SOURCE_EXTENSIONS]. This is the fast path
     *    and works for most cases where the source file follows standard naming conventions.
     *
     * 2. **Directory traversal**: Falls back to a depth-first search through directories
     *    that match the class's package prefix. Only source files whose names match
     *    `SimpleName.java` or `SimpleName.kt` (from [SOURCE_EXTENSIONS]) are inspected.
     *    This handles cases where the directory structure doesn't strictly follow the
     *    package layout (e.g., Kotlin multi-file facades or non-standard source layouts).
     *
     * Both phases delegate to [findClassInVirtualFile] to resolve the [PsiClass] from
     * the matched virtual file.
     *
     * @param dirFile The source root directory to search in
     * @param className The fully qualified class name to find
     * @return The source PsiClass if found, null otherwise
     */
    private fun findSourceClassInRoot(dirFile: VirtualFile, className: String): PsiClass? {
        val simpleName = className.substringAfterLast('.')
        val packagePath = className.substringBeforeLast('.', "").replace('.', '/')

        for (ext in SOURCE_EXTENSIONS) {
            val relativePath = if (packagePath.isNotEmpty()) "$packagePath/$simpleName$ext" else "$simpleName$ext"
            val file = dirFile.findFileByRelativePath(relativePath)
            if (file != null && file.isValid) {
                findClassInVirtualFile(file, className)?.let { return it }
            }
        }

        val targetFileNames = SOURCE_EXTENSIONS.map { simpleName + it }.toSet()
        val dirs = Stack<VirtualFile>()
        var dir: VirtualFile = dirFile
        val rootPath = dirFile.path
        while (true) {
            val children = dir.children
            for (child in children) {
                if (child.isDirectory) {
                    val relativeDirPath = dir.path.removePrefix(rootPath)
                        .removePrefix(File.separator)
                    val packagePrefix = relativeDirPath.replace(File.separatorChar, '.')
                    if (packagePrefix.isEmpty() || className.startsWith("$packagePrefix.")) {
                        dirs.push(child)
                    }
                } else if (child.isValid && child.name in targetFileNames) {
                    findClassInVirtualFile(child, className)?.let { return it }
                }
            }
            if (dirs.isEmpty()) {
                break
            }
            dir = dirs.pop()
        }
        return null
    }

    /**
     * Resolves a PsiClass from a virtual file by qualified name.
     *
     * For files that implement [PsiClassOwner] (e.g., Java/Kotlin source files),
     * directly inspects the declared classes. For other file types, uses
     * [JavaPsiFacade] with a file-scoped search.
     *
     * @param file The virtual file to resolve the class from
     * @param qualifiedName The fully qualified class name to find
     * @return The matching PsiClass if found, null otherwise
     */
    private fun findClassInVirtualFile(file: VirtualFile, qualifiedName: String): PsiClass? {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        if (psiFile is PsiClassOwner) {
            return psiFile.classes.find { it.qualifiedName == qualifiedName }
        }
        val scope = GlobalSearchScope.fileScope(project, file)
        return JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope)
    }
}
