package com.itangcent.easyapi.psi.adapter

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.psi.doc.DocComment

/**
 * Language-specific adapter for PSI element resolution.
 *
 * Provides a unified interface for working with PSI elements
 * across different JVM languages (Java, Kotlin, Groovy, Scala).
 *
 * Implementations:
 * - [JavaPsiAdapter] - For Java source files
 * - [KotlinPsiAdapter] - For Kotlin source files
 * - [GroovyPsiAdapter] - For Groovy source files
 * - [ScalaPsiAdapter] - For Scala source files
 *
 * ## Usage
 * ```kotlin
 * val adapter = adapters.find { it.supportsElement(element) }
 * val psiClass = adapter?.resolveClass(element)
 * val methods = adapter?.resolveMethods(psiClass)
 * ```
 *
 * @see JavaPsiAdapter for the base implementation
 */
interface PsiLanguageAdapter {
    fun supportsElement(element: PsiElement): Boolean

    fun resolveClass(element: PsiElement): PsiClass?

    fun resolveMethods(psiClass: PsiClass): List<PsiMethod>

    fun resolveFields(psiClass: PsiClass): List<PsiField>

    fun resolveAnnotations(element: PsiElement): List<PsiAnnotation>

    fun resolveDocComment(element: PsiElement): DocComment?

    fun resolveEnumConstants(psiClass: PsiClass): List<String>
}
