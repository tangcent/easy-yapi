package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.intellij.jvm.AnnotationHelper

/*
 * This class provides a standard implementation for resolving whether a given PsiClass
 * has a Spring controller annotation.
 */
class StandardSpringControllerAnnotationResolver : SpringControllerAnnotationResolver {

    @Inject
    private lateinit var annotationHelper: AnnotationHelper

    override fun hasControllerAnnotation(psiClass: PsiClass): Boolean {
        // Check for direct Spring controller annotations
        return SpringClassName.SPRING_CONTROLLER_ANNOTATION.any { annotationHelper.hasAnn(psiClass, it) }
    }
} 