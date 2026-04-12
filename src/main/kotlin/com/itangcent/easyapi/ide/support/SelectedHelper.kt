package com.itangcent.easyapi.ide.support

import com.intellij.ide.projectView.impl.nodes.ClassTreeNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.pom.Navigatable
import com.intellij.psi.*
import com.itangcent.easyapi.core.threading.readAsync
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.util.FileType

/**
 * Utility for resolving user selections in the IDE.
 *
 * Handles various selection contexts:
 * - Editor cursor position (PSI_ELEMENT)
 * - Project tree selection (NAVIGATABLE_ARRAY)
 * - Current file (PSI_FILE)
 *
 * ## Usage
 * ```kotlin
 * override fun actionPerformed(e: AnActionEvent) {
 *     val scope = SelectedHelper.resolveSelection(e) ?: return
 *     
 *     // Get selected classes
 *     val classes = scope.classes().toList()
 *     
 *     // Get selected methods
 *     val methods = scope.methods().toList()
 * }
 * ```
 */
object SelectedHelper {

    /**
     * Resolves the current selection from an action event.
     *
     * @param event The action event
     * @return A [SelectionScope] containing the selected elements, or null if nothing selected
     */
    fun resolveSelection(event: AnActionEvent): SelectionScope? {
        // Prefer PSI_ELEMENT — it represents the actual element at the cursor
        // in the source file. This correctly handles clicks on annotations,
        // type references (String, UserInfo, etc.), and other non-class elements.
        val psiElement = event.getData(CommonDataKeys.PSI_ELEMENT)
        if (psiElement != null) {
            val resolved = resolveContainingClassOrMethod(psiElement)
            if (resolved != null) {
                return SelectionScope(listOf(resolved))
            }
        }

        // NAVIGATABLE_ARRAY is used for project tree selections (e.g. selecting
        // a class/directory in the project view). Filter out classes that are
        // not user-defined targets (annotation types, JDK classes, etc.).
        val navigatables = event.getData(CommonDataKeys.NAVIGATABLE_ARRAY)
        if (!navigatables.isNullOrEmpty()) {
            val elements = navigatables.mapNotNull { resolveNavigatable(it) }
            if (elements.isNotEmpty()) {
                return SelectionScope(elements)
            }
        }

        val psiFile = event.getData(CommonDataKeys.PSI_FILE)
        if (psiFile != null) {
            return SelectionScope(listOf(psiFile))
        }

        val navigatable = event.getData(CommonDataKeys.NAVIGATABLE)
        if (navigatable != null) {
            val element = resolveNavigatable(navigatable)
            if (element != null) {
                return SelectionScope(listOf(element))
            }
        }

        return null
    }

    /**
     * Walk up the PSI tree from [element] to find the nearest containing
     * PsiMethod or PsiClass. This handles the case where the element is a
     * PsiAnnotation — we resolve to the annotated method/class, not the
     * annotation type itself.
     */
    private fun resolveContainingClassOrMethod(element: PsiElement): Any? {
        var current: PsiElement? = element
        while (current != null) {
            when (current) {
                is PsiMethod -> return current
                is PsiClass -> if (!current.isAnnotationType) return current
            }
            current = current.parent
        }
        return null
    }

    private fun resolveNavigatable(navigatable: Navigatable): Any? {
        return when (navigatable) {
            is PsiDirectory -> navigatable
            is PsiClass -> if (navigatable.isAnnotationType) null else navigatable
            is ClassTreeNode -> navigatable.psiClass
            is PsiDirectoryNode -> navigatable.element?.value
            is PsiMethod -> navigatable
            is PsiMember -> navigatable.containingClass ?: navigatable
            else -> null
        }
    }
}

/**
 * Represents a resolved selection scope containing PSI elements.
 *
 * Provides lazy sequences for accessing different element types
 * (files, classes, methods) from the selection.
 *
 * ## Usage
 * ```kotlin
 * val scope = SelectedHelper.resolveSelection(event) ?: return
 * 
 * // Get first selected class
 * val targetClass = scope.psiClass()
 * 
 * // Get all selected classes
 * val allClasses = scope.classes().toList()
 * ```
 */
class SelectionScope(private val elements: List<Any>) {

    /**
     * Returns all files in the selection.
     */
    fun files(): Sequence<PsiFile> = sequence {
        for (element in elements) {
            when (element) {
                is PsiFile -> yield(element)
                is PsiClass -> element.containingFile?.let { yield(it) }
                is PsiMethod -> element.containingFile?.let { yield(it) }
                is PsiDirectory -> yieldAll(collectFilesFromDirectory(element))
            }
        }
    }.distinct()

    fun file(): PsiFile? = files().firstOrNull()

    fun classes(): Sequence<PsiClass> = sequence {
        for (element in elements) {
            when (element) {
                is PsiClass -> yield(element)
                is PsiMethod -> readSync { element.containingClass }?.let { yield(it) }
                is PsiFile -> if (element is PsiClassOwner) yieldAll(readSync { element.classes }.asSequence())
                is PsiDirectory -> yieldAll(collectClassesFromDirectory(element))
            }
        }
    }.distinct()

    fun psiClass(): PsiClass? = classes().firstOrNull()

    fun methods(): Sequence<PsiMethod> = sequence {
        for (element in elements) {
            when (element) {
                is PsiMethod -> yield(element)
            }
        }
    }

    fun method(): PsiMethod? = methods().firstOrNull()

    private fun collectFilesFromDirectory(directory: PsiDirectory): Sequence<PsiFile> = sequence {
        val stack = ArrayDeque<PsiDirectory>()
        stack.addLast(directory)
        while (stack.isNotEmpty()) {
            val dir = stack.removeFirst()
            for (file in readSync { dir.files }) {
                if (FileType.acceptable(file.name)) {
                    yield(file)
                }
            }
            readSync { dir.subdirectories }.forEach { stack.addLast(it) }
        }
    }

    private fun collectClassesFromDirectory(directory: PsiDirectory): Sequence<PsiClass> = sequence {
        for (file in collectFilesFromDirectory(directory)) {
            if (file is PsiClassOwner) {
                yieldAll(readSync { file.classes }.asSequence())
            }
        }
    }
}
