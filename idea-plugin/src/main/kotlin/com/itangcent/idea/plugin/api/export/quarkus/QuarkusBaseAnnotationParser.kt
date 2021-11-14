package com.itangcent.idea.plugin.api.export.quarkus

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.jvm.AnnotationHelper

/**
 * Parse base annotations that indicates that the annotated class/method responds to HTTP POST requests.
 *
 * @author tangcent
 */
@Singleton
class QuarkusBaseAnnotationParser {

    @Inject
    private lateinit var annotationHelper: AnnotationHelper

    @Inject
    private lateinit var quarkusCustomHttpMethodResolver: QuarkusCustomHttpMethodResolver

    @Inject
    private lateinit var ruleComputer: RuleComputer

    fun hasApi(psiClass: PsiClass): Boolean {
        return annotationHelper.hasAnn(psiClass, QuarkusClassName.PATH_ANNOTATION)
                || (ruleComputer.computer(ClassExportRuleKeys.IS_QUARKUS_CTRL, psiClass) == true)
    }

    fun isApi(psiMethod: PsiMethod): Boolean {
        return findHttpMethod(psiMethod) != null
    }

    fun findHttpMethod(psiMethod: PsiMethod): String? {
        for (mappingAnnotation in QuarkusClassName.QUARKUS_SINGLE_MAPPING_ANNOTATIONS) {
            if (annotationHelper.hasAnn(psiMethod, mappingAnnotation)) {
                return mappingAnnotation.substringAfterLast('.')
            }
        }
        return quarkusCustomHttpMethodResolver.findCustomAnnotatedHttpMethod(psiMethod)
    }
}
