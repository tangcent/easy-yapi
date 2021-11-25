package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.itangcent.common.utils.cast
import com.itangcent.common.utils.merge
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.AnnotationHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * Support custom annotation which annotated with [SpringClassName.SPRING_REQUEST_MAPPING_ANNOTATIONS]
 */
@Singleton
class CustomSpringRequestMappingResolver : SpringRequestMappingResolver {

    private val resolvedSpringRequestMappingResolver = ConcurrentHashMap<String, SpringRequestMappingResolver>()

    @Inject
    private lateinit var annotationHelper: AnnotationHelper

    @Inject
    private lateinit var standardSpringRequestMappingResolver: StandardSpringRequestMappingResolver

    @Inject
    private lateinit var actionContext: ActionContext

    /**
     * @param psiElement annotated element(PsiMethod/PsiClass)
     * @return annotation attributes
     */
    override fun resolveRequestMapping(psiElement: PsiElement): Map<String, Any?>? {
        return actionContext.callInReadUI {
            val annotations = psiElement.cast(PsiAnnotationOwner::class)?.annotations
                ?: psiElement.cast(PsiModifierListOwner::class)?.annotations ?: return@callInReadUI null
            for (annotation in annotations) {
                getSpringRequestMappingResolverForAnnotation(annotation)?.let {
                    return@callInReadUI it.resolveRequestMapping(psiElement)
                }
            }
            return@callInReadUI null
        }
    }

    private fun getSpringRequestMappingResolverForAnnotation(annotation: PsiAnnotation): SpringRequestMappingResolver? {
        val annotationName = annotation.qualifiedName ?: return null
        return resolvedSpringRequestMappingResolver.computeIfAbsent(annotationName) {
            resolveSpringRequestMappingResolverForAnnotation(annotation)
        }
    }

    private fun resolveSpringRequestMappingResolverForAnnotation(annotation: PsiAnnotation): SpringRequestMappingResolver {
        val annotationType = annotation.nameReferenceElement?.resolve() ?: return NopSpringRequestMappingResolver
        val requestMapping = standardSpringRequestMappingResolver.resolveRequestMapping(annotationType)
        if (requestMapping != null) {
            return BridgeSpringRequestMappingResolver(annotation.qualifiedName!!, requestMapping)
        }
        return NopSpringRequestMappingResolver
    }

    private object NopSpringRequestMappingResolver : SpringRequestMappingResolver {
        override fun resolveRequestMapping(psiElement: PsiElement): Map<String, Any?>? {
            return null
        }
    }

    private inner class BridgeSpringRequestMappingResolver(
        private val bridgeAnnName: String,
        private val mateData: Map<String, Any?>
    ) :
        SpringRequestMappingResolver {

        override fun resolveRequestMapping(psiElement: PsiElement): Map<String, Any?>? {
            val annAttributes = HashMap(mateData)
            annotationHelper.findAnnMap(psiElement, bridgeAnnName)?.let {
                annAttributes.merge(it)
            }
            return annAttributes
        }
    }
}
