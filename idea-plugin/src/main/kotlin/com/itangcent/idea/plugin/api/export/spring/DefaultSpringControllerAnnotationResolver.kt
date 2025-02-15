package com.itangcent.idea.plugin.api.export.spring

import com.intellij.psi.PsiClass
import com.itangcent.spi.SpiCompositeLoader

/*
 * This class provides a default implementation for resolving whether a given PsiClass
 * has a Spring controller annotation. It delegates the resolution to a composite
 * loader that can handle multiple strategies.
 */
class DefaultSpringControllerAnnotationResolver : SpringControllerAnnotationResolver {

    private val delegate: SpringControllerAnnotationResolver by lazy {
        SpiCompositeLoader.loadComposite()
    }

    override fun hasControllerAnnotation(psiClass: PsiClass): Boolean {
        return delegate.hasControllerAnnotation(psiClass)
    }
}