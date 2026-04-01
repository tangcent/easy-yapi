package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.psi.type.SpecialTypeHandler
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Resolves the Content-Type for Spring MVC request methods.
 *
 * Determines the appropriate Content-Type header based on:
 * 1. Rule override (`method.content.type` rule key)
 * 2. Annotation `consumes` attribute
 * 3. Parameter annotations (@RequestBody, @RequestPart)
 * 4. File upload detection
 *
 * @param annotationHelper Helper for reading annotations
 * @param ruleEngine Rule engine for custom content type resolution
 */
class ContentTypeResolver(
    private val annotationHelper: AnnotationHelper,
    private val ruleEngine: RuleEngine
) {
    suspend fun resolve(method: PsiMethod, mapping: ResolvedMapping): String? {
        val override = ruleEngine.evaluate(RuleKeys.METHOD_CONTENT_TYPE, method)
        if (!override.isNullOrBlank()) return override

        if (mapping.consumes.isNotEmpty()) return mapping.consumes.firstOrNull()

        val params = method.parameterList.parameters.toList()
        if (params.any { annotationHelper.hasAnn(it, "org.springframework.web.bind.annotation.RequestBody") }) {
            return "application/json"
        }
        if (params.any { annotationHelper.hasAnn(it, "org.springframework.web.bind.annotation.RequestPart") }) {
            return "multipart/form-data"
        }
        if (params.any { hasFileUpload(it) }) {
            return "multipart/form-data"
        }
        return null
    }
    
    private fun hasFileUpload(parameter: com.intellij.psi.PsiParameter): Boolean {
        val typeText = parameter.type.canonicalText
        if (SpecialTypeHandler.isFileTypeCanonical(typeText)) return true
        if (typeText.contains("MultipartFile") || typeText.contains("Part")) return true
        return false
    }
}
