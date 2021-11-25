package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.ImplementedBy
import com.intellij.psi.PsiElement

@ImplementedBy(DefaultSpringRequestMappingResolver::class)
interface SpringRequestMappingResolver {

    /**
     * @param psiElement annotated element(PsiMethod/PsiClass)
     * @return annotation attributes
     */
    fun resolveRequestMapping(psiElement: PsiElement): Map<String, Any?>?
}