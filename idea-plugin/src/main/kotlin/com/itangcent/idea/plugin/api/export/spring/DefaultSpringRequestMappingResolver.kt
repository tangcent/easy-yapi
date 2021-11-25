package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.itangcent.spi.SpiCompositeLoader

@Singleton
class DefaultSpringRequestMappingResolver : SpringRequestMappingResolver {

    private val delegate: SpringRequestMappingResolver by lazy {
        SpiCompositeLoader.loadComposite()
    }

    /**
     * @param psiElement annotated element(PsiMethod/PsiClass)
     * @return annotation attributes
     */
    override fun resolveRequestMapping(psiElement: PsiElement): Map<String, Any?>? {
        return if (psiElement is PsiClass) {
            resolveRequestMappingFromClass(psiElement)
        } else {
            resolveRequestMappingFromElement(psiElement)
        }
    }

    private fun resolveRequestMappingFromClass(psiClass: PsiClass): Map<String, Any?>? {
        val requestMappingAnn = resolveRequestMappingFromElement(psiClass)
        if (requestMappingAnn != null) return requestMappingAnn
        var superCls = psiClass.superClass
        while (superCls != null) {
            val requestMappingAnnInSuper = resolveRequestMappingFromElement(superCls)
            if (requestMappingAnnInSuper != null) return requestMappingAnnInSuper
            superCls = superCls.superClass
        }
        return null
    }

    private fun resolveRequestMappingFromElement(ele: PsiElement): Map<String, Any?>? {
        return delegate.resolveRequestMapping(ele)
    }
}