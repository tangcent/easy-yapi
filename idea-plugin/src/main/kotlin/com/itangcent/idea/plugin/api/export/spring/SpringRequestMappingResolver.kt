package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.ImplementedBy
import com.intellij.psi.PsiElement

/*
 * This interface defines a contract for resolving Spring request mapping annotations.
 * It is responsible for extracting mapping information from Spring MVC request mapping
 * annotations on classes and methods. This includes both standard Spring annotations
 * and custom annotations that may be used for request mapping.
 */
@ImplementedBy(DefaultSpringRequestMappingResolver::class)
interface SpringRequestMappingResolver {

    /**
     * @param psiElement annotated element(PsiMethod/PsiClass)
     * @return annotation attributes
     */
    fun resolveRequestMapping(psiElement: PsiElement): Map<String, Any?>?
}