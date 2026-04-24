package com.itangcent.easyapi.psi.adapter

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.psi.doc.DocComment

/**
 * PSI adapter for Groovy source files.
 *
 * Handles PSI element resolution for Groovy classes. Delegates to
 * [JavaPsiAdapter] for operations where Groovy PSI is compatible
 * with Java PSI (methods, fields, annotations, enum constants, doc comments).
 *
 * ## Groovy-specific differences from Java PSI
 *
 * - **resolveClass**: Groovy files use `GroovyFileBase` (not `PsiJavaFile`),
 *   so the Java adapter's `PsiJavaFile` branch won't match. This adapter
 *   uses reflection to access `GroovyFileBase.classes` for file-level resolution.
 *
 * - **resolveDocComment**: Groovy classes implement `PsiDocCommentOwner` and
 *   use standard `PsiDocComment` for Groovydoc, so the Java adapter handles
 *   this correctly. Groovydoc syntax is compatible with Javadoc.
 *
 * - **EOL comments**: Groovy uses its own token types (`SL_COMMENT`, `ML_COMMENT`)
 *   for end-of-line and block comments, and requires skipping Groovy-specific
 *   syntax elements (`T_SEMI`, `NL`) when scanning siblings. This is deferred
 *   to a future iteration.
 *
 * @see PsiLanguageAdapter for the interface
 * @see JavaPsiAdapter for the delegate implementation
 */
class GroovyPsiAdapter : PsiLanguageAdapter {

    private val delegate = JavaPsiAdapter()

    override fun supportsElement(element: PsiElement): Boolean {
        val id = element.containingFile?.language?.id ?: return false
        return id.equals("groovy", true) || id.equals("Groovy", true)
    }

    /**
     * Resolves a PsiClass from a Groovy PSI element.
     *
     * Unlike Java where files are `PsiJavaFile`, Groovy files are `GroovyFileBase`.
     * The delegate handles `PsiClass` and parent-based resolution, but cannot
     * handle `GroovyFileBase` directly. This method adds Groovy file support
     * via reflection to avoid a compile-time dependency on the Groovy plugin.
     */
    override fun resolveClass(element: PsiElement): PsiClass? {
        // Delegate handles PsiClass and parent-based resolution
        val fromDelegate = delegate.resolveClass(element)
        if (fromDelegate != null) return fromDelegate

        // Handle GroovyFileBase — Groovy files are not PsiJavaFile but have a `classes` property
        return resolveClassFromGroovyFile(element)
    }

    override fun resolveMethods(psiClass: PsiClass): List<PsiMethod> = delegate.resolveMethods(psiClass)

    override fun resolveFields(psiClass: PsiClass): List<PsiField> = delegate.resolveFields(psiClass)

    override fun resolveAnnotations(element: PsiElement): List<PsiAnnotation> = delegate.resolveAnnotations(element)

    /**
     * Resolves doc comments from Groovy PSI elements.
     *
     * Groovy classes implement `PsiDocCommentOwner` and use standard `PsiDocComment`
     * for Groovydoc, so the Java adapter's parsing works correctly. Groovydoc syntax
     * (`@param`, `@return`, etc.) is compatible with Javadoc.
     */
    override fun resolveDocComment(element: PsiElement): DocComment? = delegate.resolveDocComment(element)

    override fun resolveEnumConstants(psiClass: PsiClass): List<String> = delegate.resolveEnumConstants(psiClass)

    /**
     * Resolves a PsiClass from a GroovyFileBase element via reflection.
     *
     * GroovyFileBase has a `getClasses()` method that returns `PsiClass[]`,
     * similar to `PsiJavaFile.classes`. We use reflection to avoid requiring
     * the Groovy plugin at compile time.
     */
    private fun resolveClassFromGroovyFile(element: PsiElement): PsiClass? {
        val file = element.containingFile ?: return null
        val groovyFileBaseClass = runCatching {
            Class.forName("org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase")
        }.getOrNull() ?: return null

        if (!groovyFileBaseClass.isInstance(file)) return null

        val getClassesMethod = runCatching {
            groovyFileBaseClass.getMethod("getClasses")
        }.getOrNull() ?: return null

        val classes = runCatching {
            @Suppress("UNCHECKED_CAST")
            getClassesMethod.invoke(file) as? Array<PsiClass>
        }.getOrNull() ?: return null

        return classes.firstOrNull()
    }
}
