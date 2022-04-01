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
        resolveRequestMappingFromElement(psiClass)?.let { return it }

        for (inter in psiClass.interfaces) {
            resolveRequestMappingFromElement(inter)?.let { return it }
        }

        var superCls = psiClass.superClass
        while (superCls != null) {
            resolveRequestMappingFromElement(superCls)?.let { return it }
            superCls = superCls.superClass
        }
        return null
    }

    private fun resolveRequestMappingFromElement(ele: PsiElement): Map<String, Any?>? {
        return delegate.resolveRequestMapping(ele)
    }
}