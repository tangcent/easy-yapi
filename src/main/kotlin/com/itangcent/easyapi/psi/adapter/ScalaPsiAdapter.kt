package com.itangcent.easyapi.psi.adapter

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.di.ConditionOnClass
import com.itangcent.easyapi.psi.doc.DocComment

/**
 * PSI adapter for Scala source files.
 *
 * Handles PSI element resolution for Scala classes by delegating
 * to [JavaPsiAdapter] for most operations. Scala PSI classes
 * are compatible with Java PSI APIs for most use cases.
 *
 * This adapter is only loaded when the Scala plugin is available.
 *
 * @see PsiLanguageAdapter for the interface
 * @see JavaPsiAdapter for the delegate implementation
 */
@ConditionOnClass("org.jetbrains.plugins.scala.ScalaLanguage")
class ScalaPsiAdapter : PsiLanguageAdapter {
    private val delegate = JavaPsiAdapter()

    override fun supportsElement(element: PsiElement): Boolean {
        val id = element.containingFile?.language?.id ?: return false
        return id.equals("scala", true) || id.equals("Scala", true)
    }

    override fun resolveClass(element: PsiElement): PsiClass? = delegate.resolveClass(element)

    override fun resolveMethods(psiClass: PsiClass): List<PsiMethod> = delegate.resolveMethods(psiClass)

    override fun resolveFields(psiClass: PsiClass): List<PsiField> = delegate.resolveFields(psiClass)

    override fun resolveAnnotations(element: PsiElement): List<PsiAnnotation> = delegate.resolveAnnotations(element)

    override fun resolveDocComment(element: PsiElement): DocComment? = delegate.resolveDocComment(element)

    override fun resolveEnumConstants(psiClass: PsiClass): List<String> = delegate.resolveEnumConstants(psiClass)
}
