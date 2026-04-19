package com.itangcent.easyapi.psi.helper

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.ParameterType
import com.itangcent.easyapi.exporter.model.PathSelector
import com.itangcent.easyapi.psi.type.JsonType
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.util.GsonUtils
import com.itangcent.easyapi.util.append

/**
 * Resolves API metadata from PSI elements using rules and doc comments.
 *
 * Provides a unified interface for extracting:
 * - API names and descriptions
 * - Parameter names, types, and defaults
 * - Folder/group names
 * - Required status
 *
 * ## Resolution Priority
 * For most metadata, the resolution order is:
 * 1. Rule-based value (e.g., `api.name`, `param.type`)
 * 2. Doc comment value
 * 3. Default value from PSI
 *
 * ## Usage
 * ```kotlin
 * val resolver = ApiMetadataResolver(ruleEngine, docHelper, settings)
 * val apiName = resolver.resolveApiName(method)
 * val paramType = resolver.resolveParamType(parameter, "java.lang.String")
 * ```
 *
 * @see RuleEngine for rule evaluation
 * @see DocHelper for doc comment extraction
 */
class ApiMetadataResolver(
    private val engine: RuleEngine,
    private val docHelper: DocHelper,
    private val settings: Settings = Settings()
) {
    suspend fun resolveApiName(method: PsiMethod): String {
        // 1. api.name rule
        val ruleApiName = engine.evaluate(RuleKeys.API_NAME, method)
        if (!ruleApiName.isNullOrBlank()) return ruleApiName

        // 2. First line of doc comment
        val docComment = docHelper.getAttrOfDocComment(method)
        val headLine = docComment?.lines()?.firstOrNull { it.isNotBlank() }
        if (!headLine.isNullOrBlank()) return headLine

        // 3. First line of method.doc rule
        val methodDoc = engine.evaluate(RuleKeys.METHOD_DOC, method)
        val docHeadLine = methodDoc?.lines()?.firstOrNull { it.isNotBlank() }
        if (!docHeadLine.isNullOrBlank()) return docHeadLine

        // 4. Method name
        return method.name
    }

    suspend fun resolveClassDoc(psiClass: PsiClass): String {
        val docComment = docHelper.getAttrOfDocComment(psiClass)
        val ruleClassDesc = engine.evaluate(RuleKeys.CLASS_DOC, psiClass)
        val combined = docComment.append(ruleClassDesc)
        return combined.ifBlank { psiClass.name ?: "Unknown" }
    }

    suspend fun resolveMethodDoc(method: PsiMethod): String {
        val docComment = docHelper.getAttrOfDocComment(method)
        val ruleDoc = engine.evaluate(RuleKeys.METHOD_DOC, method)
        return docComment.append(ruleDoc)
    }

    suspend fun resolveFolderName(method: PsiMethod, psiClass: PsiClass? = null): String? {
        // Try folder.name rule on the method first
        val methodFolder = engine.evaluate(RuleKeys.FOLDER_NAME, method)
        if (!methodFolder.isNullOrBlank()) return methodFolder

        // Try folder.name rule on the containing class
        val cls = psiClass ?: method.containingClass ?: return null
        val classFolder = engine.evaluate(RuleKeys.FOLDER_NAME, cls)
        if (!classFolder.isNullOrBlank()) return classFolder

        // Fall back to first line of class doc comment
        val classDoc = docHelper.getAttrOfDocComment(cls)
        if (!classDoc.isNullOrBlank()) {
            return classDoc.lines().firstOrNull { it.isNotBlank() }
        }

        // Fall back to class name
        return cls.name
    }

    suspend fun resolveParamName(parameter: PsiParameter, defaultName: String): String {
        val ruleParamName = engine.evaluate(RuleKeys.PARAM_NAME, parameter)
        return ruleParamName?.takeIf { it.isNotBlank() } ?: defaultName
    }

    suspend fun resolveParamDoc(parameter: PsiParameter): String {
        val method = parameter.declarationScope as? PsiMethod
        val paramName = parameter.name

        val javaDocComment = if (method != null) {
            docHelper.findDocsByTagAndName(method, "param", paramName)
        } else null

        val ruleDoc = engine.evaluate(RuleKeys.PARAM_DOC, parameter)

        return javaDocComment.append(ruleDoc)
    }

    suspend fun isParamRequired(parameter: PsiParameter): Boolean {
        return engine.evaluate(RuleKeys.PARAM_REQUIRED, parameter)
    }

    suspend fun isParamIgnored(parameter: PsiParameter): Boolean {
        return engine.evaluate(RuleKeys.PARAM_IGNORE, parameter)
    }

    suspend fun resolveParamType(parameter: PsiParameter, defaultType: String): String {
        val ruleType = engine.evaluate(RuleKeys.PARAM_TYPE, parameter)?.takeIf { it.isNotBlank() }
        if (ruleType != null) {
            return if (JsonType.isValid(ruleType)) ruleType else JsonType.fromJavaType(ruleType)
        }
        return JsonType.fromJavaType(defaultType)
    }

    suspend fun resolveParamDefaultValue(parameter: PsiParameter): String? {
        return engine.evaluate(RuleKeys.PARAM_DEFAULT_VALUE, parameter)
    }

    suspend fun resolveParamDemo(parameter: PsiParameter): String? {
        return engine.evaluate(RuleKeys.PARAM_DEMO, parameter)
    }

    suspend fun resolveParamMock(parameter: PsiParameter): String? {
        return engine.evaluate(RuleKeys.PARAM_MOCK, parameter)
    }

    suspend fun resolveApiTag(method: PsiMethod): String? {
        return engine.evaluate(RuleKeys.API_TAG, method)
    }

    suspend fun resolveApiStatus(method: PsiMethod): String? {
        return engine.evaluate(RuleKeys.API_STATUS, method)
    }

    suspend fun isApiOpen(method: PsiMethod): Boolean {
        return engine.evaluate(RuleKeys.API_OPEN, method)
    }
    
    suspend fun resolveYapiProject(method: PsiMethod): String? {
        return engine.evaluate(RuleKeys.YAPI_PROJECT, method)
    }

    suspend fun resolveYapiProject(psiClass: PsiClass): String? {
        return engine.evaluate(RuleKeys.YAPI_PROJECT, psiClass)
    }

    suspend fun isIgnored(element: PsiElement): Boolean {
        return engine.evaluate(RuleKeys.IGNORE, element)
    }

    suspend fun resolveMethodReturn(method: PsiMethod): String? {
        return engine.evaluate(RuleKeys.METHOD_RETURN, method)
    }

    suspend fun resolveMethodReturnMain(method: PsiMethod): String? {
        return engine.evaluate(RuleKeys.METHOD_RETURN_MAIN, method)
    }

    suspend fun resolveDefaultHttpMethod(method: PsiMethod): String? {
        return engine.evaluate(RuleKeys.METHOD_DEFAULT_HTTP, method)
    }

    suspend fun resolveAdditionalHeaders(method: PsiMethod): List<ApiHeader> {
        val raw = engine.evaluate(RuleKeys.METHOD_ADDITIONAL_HEADER, method) ?: return emptyList()
        return parseHeaderLines(raw)
    }

    suspend fun resolveAdditionalParams(method: PsiMethod): List<ApiParameter> {
        val raw = engine.evaluate(RuleKeys.METHOD_ADDITIONAL_PARAM, method) ?: return emptyList()
        return parseParamLines(raw)
    }

    suspend fun resolveAdditionalResponseHeaders(method: PsiMethod): List<ApiHeader> {
        val raw = engine.evaluate(RuleKeys.METHOD_ADDITIONAL_RESPONSE_HEADER, method) ?: return emptyList()
        return parseHeaderLines(raw)
    }

    private fun parseHeaderLines(raw: String): List<ApiHeader> {
        val result = ArrayList<ApiHeader>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            try {
                val map = GsonUtils.fromJson<Map<String, Any?>>(trimmed)
                val name = map["name"]?.toString() ?: continue
                result.add(
                    ApiHeader(
                        name = name,
                        value = map["value"]?.toString(),
                        description = map["desc"]?.toString(),
                        required = map["required"]?.toString()?.toBooleanStrictOrNull() ?: false
                    )
                )
            } catch (_: Exception) {
            }
        }
        return result
    }

    private fun parseParamLines(raw: String): List<ApiParameter> {
        val result = ArrayList<ApiParameter>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            try {
                val map = GsonUtils.fromJson<Map<String, Any?>>(trimmed)
                val name = map["name"]?.toString() ?: continue
                result.add(
                    ApiParameter(
                        name = name,
                        type = ParameterType.fromTypeName(map["type"]?.toString()),
                        required = map["required"]?.toString()?.toBooleanStrictOrNull() ?: false,
                        description = map["desc"]?.toString(),
                        defaultValue = map["value"]?.toString()
                    )
                )
            } catch (_: Exception) {
            }
        }
        return result
    }

    suspend fun resolvePathMulti(method: PsiMethod): PathSelector {
        val raw = engine.evaluate(RuleKeys.PATH_MULTI, method)
        if (!raw.isNullOrBlank()) return PathSelector.fromRule(raw)
        return PathSelector.fromRule(settings.pathMulti)
    }
}
