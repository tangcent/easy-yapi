package com.itangcent.idea.plugin.api.export.feign

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiElement
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.plugin.api.export.spring.SpringRequestMappingResolver
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.jvm.psi.PsiClassUtil 
import com.itangcent.order.Order
import com.itangcent.order.Ordered
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Support @RequestLine
 */
@Singleton
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionOnClass(SpringFeignClassName.REQUEST_LINE_ANNOTATION)
@ConditionOnSetting("feignEnable")
class RequestLineRequestMappingResolver : SpringRequestMappingResolver {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var annotationHelper: AnnotationHelper

    /**
     * see [https://github.com/OpenFeign/feign/blob/f685f76002370413965f8aafea0ea96843b3c806/core/src/main/java/feign/Contract.java#L253-L270]
     *
     * @param psiElement annotated element(PsiMethod/PsiClass)
     * @return annotation attributes
     */
    override fun resolveRequestMapping(psiElement: PsiElement): Map<String, Any?>? {
        val requestLineValue =
            annotationHelper.findAttrAsString(psiElement, SpringFeignClassName.REQUEST_LINE_ANNOTATION) ?: return null

        if (requestLineValue.isEmpty()) {
            logger.error("RequestLine annotation was empty on method ${PsiClassUtil.fullNameOfMember(psiElement)}.")
            return null
        }
        val requestLineMatcher: Matcher = REQUEST_LINE_PATTERN.matcher(requestLineValue)
        if (!requestLineMatcher.find()) {
            logger.error(
                "RequestLine annotation didn't start with an HTTP verb on method ${
                    PsiClassUtil.fullNameOfMember(
                        psiElement
                    )
                }"
            )
            return null
        }
        return mapOf("method" to requestLineMatcher.group(1),
            "value" to requestLineMatcher.group(2))
    }

    companion object {
        val REQUEST_LINE_PATTERN = Pattern.compile("^([A-Z]+)[ ]*(.*)$")!!
    }
}