package com.itangcent.easyapi.exporter.feign

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.exporter.core.ApiClassRecognizer
import com.itangcent.easyapi.exporter.core.MetaAnnotationResolver
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Recognizes Feign client interfaces.
 *
 * Supports both standard @FeignClient and custom meta-annotations
 * annotated with @FeignClient.
 */
class FeignClientRecognizer(
    private val ruleEngine: RuleEngine? = null,
    private val enabled: Boolean = true
) : ApiClassRecognizer {

    override val frameworkName: String = "Feign"

    override val targetAnnotations: Set<String> = FEIGN_ANNOTATIONS

    override suspend fun isApiClass(psiClass: PsiClass): Boolean {
        if (!enabled) return false
        if (ruleEngine?.evaluate(RuleKeys.CLASS_IS_FEIGN_CTRL, psiClass) == true) return true
        return MetaAnnotationResolver.hasMetaAnnotation(psiClass, FEIGN_ANNOTATIONS)
    }

    suspend fun isFeignClient(psiClass: PsiClass): Boolean = isApiClass(psiClass)

    companion object {
        val FEIGN_ANNOTATIONS = setOf(
            "org.springframework.cloud.openfeign.FeignClient"
        )
    }
}
