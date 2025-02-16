package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.ImplementedBy
import com.intellij.psi.PsiClass

/*
 * This interface defines a contract for resolving whether a given PsiClass
 * has a Spring controller annotation. It is implemented by various classes
 * to provide different strategies for determining the presence of controller annotations.
 */
@ImplementedBy(DefaultSpringControllerAnnotationResolver::class)
interface SpringControllerAnnotationResolver {
    fun hasControllerAnnotation(psiClass: PsiClass): Boolean
} 