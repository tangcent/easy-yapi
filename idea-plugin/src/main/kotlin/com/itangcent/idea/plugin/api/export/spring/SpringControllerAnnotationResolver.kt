package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.ProvidedBy
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.spi.SpiCompositeBeanProvider

/*
 * This interface defines a contract for resolving whether a given PsiClass
 * has a Spring controller annotation. It is implemented by various classes
 * to provide different strategies for determining the presence of controller annotations.
 */
@ProvidedBy(SpringControllerAnnotationResolverCompositeProvider::class)
interface SpringControllerAnnotationResolver {
    fun hasControllerAnnotation(psiClass: PsiClass): Boolean
}

@Singleton
class SpringControllerAnnotationResolverCompositeProvider :
    SpiCompositeBeanProvider<SpringControllerAnnotationResolver>()