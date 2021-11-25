package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiElement
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.utils.firstOrNull
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.order.Order
import com.itangcent.order.Ordered

/**
 * Support standard spring RequestMapping annotations [SpringClassName.SPRING_REQUEST_MAPPING_ANNOTATIONS]
 */
@Singleton
@Order(Ordered.HIGHEST_PRECEDENCE)
class StandardSpringRequestMappingResolver : SpringRequestMappingResolver {

    @Inject
    private lateinit var annotationHelper: AnnotationHelper

    /**
     * @param psiElement annotated element(PsiMethod/PsiClass)
     * @return annotation attributes
     */
    override fun resolveRequestMapping(psiElement: PsiElement): Map<String, Any?>? {
        val requestMapping: Pair<Map<String, Any?>, String> = findRequestMappingInAnn(psiElement) ?: return null
        val annotationName = requestMapping.second
        return if (annotationName == SpringClassName.REQUEST_MAPPING_ANNOTATION) {
            requestMapping.first
        } else {
            val requestMappingAttributes = HashMap(requestMapping.first)
            requestMappingAttributes["method"] = when (annotationName) {
                SpringClassName.GET_MAPPING -> HttpMethod.GET
                SpringClassName.POST_MAPPING -> HttpMethod.POST
                SpringClassName.DELETE_MAPPING -> HttpMethod.DELETE
                SpringClassName.PATCH_MAPPING -> HttpMethod.PATCH
                SpringClassName.PUT_MAPPING -> HttpMethod.PUT
                else -> HttpMethod.NO_METHOD
            }
            requestMappingAttributes
        }
    }

    private fun findRequestMappingInAnn(ele: PsiElement): Pair<Map<String, Any?>, String>? {
        return SpringClassName.SPRING_REQUEST_MAPPING_ANNOTATIONS
            .stream()
            .map { ann -> annotationHelper.findAnnMap(ele, ann)?.to(ann) }
            .firstOrNull { it != null }
    }
}