package com.itangcent.idea.plugin.api.export.quarkus

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.itangcent.common.constant.HttpMethod
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.AnnotationHelper
import java.util.concurrent.ConcurrentHashMap

/**
 * Support custom annotation which annotated with [QuarkusClassName.HTTP_METHOD_ANNOTATION]
 */
@Singleton
class QuarkusCustomHttpMethodResolver {

    private val resolvedAnnotations = ConcurrentHashMap<String, String>()

    @Inject
    private lateinit var annotationHelper: AnnotationHelper

    @Inject
    private lateinit var actionContext: ActionContext

    fun findCustomAnnotatedHttpMethod(psiMethod: PsiMethod): String? {
        return actionContext.callInReadUI {
            val annotations = psiMethod.annotations
            for (annotation in annotations) {
                getHttpMethodFroAnnotation(annotation)?.let { return@callInReadUI it }
            }
            return@callInReadUI null
        }
    }

    private fun getHttpMethodFroAnnotation(annotation: PsiAnnotation): String? {
        val annotationName = annotation.qualifiedName ?: return null
        return resolvedAnnotations.computeIfAbsent(annotationName) {
            resolveHttpMethodFroAnnotation(annotation)
        }.takeIf { it != HttpMethod.NO_METHOD }
    }

    private fun resolveHttpMethodFroAnnotation(annotation: PsiAnnotation): String {
        val annotationType = annotation.nameReferenceElement?.resolve() ?: return HttpMethod.NO_METHOD
        annotationHelper.findAttrAsString(annotationType, QuarkusClassName.HTTP_METHOD_ANNOTATION)?.let {
            return it
        }
        for (mappingAnnotation in QuarkusClassName.QUARKUS_SINGLE_MAPPING_ANNOTATIONS) {
            if (annotationHelper.hasAnn(annotationType, mappingAnnotation)) {
                return mappingAnnotation.substringAfterLast('.')
            }
        }
        return HttpMethod.NO_METHOD
    }
}