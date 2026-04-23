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
 * - Return type overrides and main field hints
 * - Additional headers, parameters, and response headers
 * - Default HTTP methods and content types
 * - Path selection strategies
 *
 * ## Resolution Priority
 * For most metadata, the resolution order is:
 * 1. Rule-based value (e.g., `api.name`, `param.type`)
 * 2. Doc comment value
 * 3. Default value from PSI
 *
 * ## Framework Applicability
 *
 * ### All Frameworks (SpringMVC, JAX-RS, Feign, gRPC, Actuator)
 * - [resolveApiName] — API endpoint name
 * - [resolveClassDoc] — class-level description
 * - [resolveMethodDoc] — method-level description
 * - [resolveFolderName] — folder/group name
 * - [resolveMethodReturn] — override return type via rule
 * - [resolveMethodReturnMain] — specify which field receives @return doc
 * - [isIgnored] — skip element during export
 * - [resolveParamName], [resolveParamDoc], [resolveParamType],
 *   [resolveParamDefaultValue], [resolveParamDemo], [resolveParamMock],
 *   [isParamRequired], [isParamIgnored] — parameter metadata
 *
 * ### HTTP Frameworks (SpringMVC, JAX-RS, Feign, Actuator)
 * - [resolveAdditionalHeaders] — add extra request headers
 * - [resolveAdditionalParams] — add extra request parameters
 * - [resolveAdditionalResponseHeaders] — add extra response headers
 * - [resolveDefaultHttpMethod] — fallback HTTP method when not annotated
 * - [resolvePathMulti] — strategy for selecting paths when multiple exist
 *
 * ### Framework-Specific Notes
 * - **JAX-RS**: [resolveDefaultHttpMethod] is used as fallback when no HTTP method
 *   annotation is found. [resolvePathMulti] is not applicable since `@Path` only
 *   supports a single value.
 * - **Feign**: [resolvePathMulti] applies to Spring-style `@RequestMapping` with
 *   multiple paths. Native Feign uses `@RequestLine` which is always single-path.
 * - **gRPC**: HTTP-specific rules ([resolveAdditionalHeaders], [resolveAdditionalParams],
 *   [resolveAdditionalResponseHeaders], [resolveDefaultHttpMethod], [resolvePathMulti])
 *   are not applicable since gRPC uses its own protocol, not HTTP semantics.
 * - **Actuator**: [resolveDefaultHttpMethod] is not applicable since operation annotations
 *   (@ReadOperation, @WriteOperation, @DeleteOperation) always specify the method.
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
    /**
     * Resolves the API endpoint name.
     *
     * **Applicable to**: All frameworks (SpringMVC, JAX-RS, Feign, gRPC, Actuator)
     *
     * Resolution order: `api.name` rule → doc comment first line → `method.doc` rule → method name
     */
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

    /**
     * Resolves the class-level description.
     *
     * **Applicable to**: All frameworks (SpringMVC, JAX-RS, Feign, gRPC, Actuator)
     */
    suspend fun resolveClassDoc(psiClass: PsiClass): String {
        val docComment = docHelper.getAttrOfDocComment(psiClass)
        val ruleClassDesc = engine.evaluate(RuleKeys.CLASS_DOC, psiClass)
        val combined = docComment.append(ruleClassDesc)
        return combined.ifBlank { psiClass.name ?: "Unknown" }
    }

    /**
     * Resolves the method-level description.
     *
     * **Applicable to**: All frameworks (SpringMVC, JAX-RS, Feign, gRPC, Actuator)
     */
    suspend fun resolveMethodDoc(method: PsiMethod): String {
        val docComment = docHelper.getAttrOfDocComment(method)
        val ruleDoc = engine.evaluate(RuleKeys.METHOD_DOC, method)
        return docComment.append(ruleDoc)
    }

    /**
     * Resolves the folder/group name for an API endpoint.
     *
     * **Applicable to**: All frameworks (SpringMVC, JAX-RS, Feign, gRPC, Actuator)
     */
    suspend fun resolveFolderName(method: PsiMethod?, psiClass: PsiClass? = null): String? {
        // Try folder.name rule on the method first
        if (method != null) {
            val methodFolder = engine.evaluate(RuleKeys.FOLDER_NAME, method)
            if (!methodFolder.isNullOrBlank()) return methodFolder
        }

        // Try folder.name rule on the containing class
        val cls = psiClass ?: method?.containingClass ?: return null
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

    /**
     * Overrides the return type via the `method.return` rule.
     *
     * **Applicable to**: All frameworks (SpringMVC, JAX-RS, Feign, gRPC, Actuator)
     *
     * Allows specifying a custom return type class instead of the actual method return type.
     */
    suspend fun resolveMethodReturn(method: PsiMethod): String? {
        return engine.evaluate(RuleKeys.METHOD_RETURN, method)
    }

    /**
     * Specifies which field in the response type should receive the `@return` doc comment.
     *
     * **Applicable to**: All frameworks (SpringMVC, JAX-RS, Feign, gRPC, Actuator)
     *
     * For example, for `Result<T>` wrapper types, this rule can specify that the
     * `@return` comment should be attached to the `data` field.
     */
    suspend fun resolveMethodReturnMain(method: PsiMethod): String? {
        return engine.evaluate(RuleKeys.METHOD_RETURN_MAIN, method)
    }

    /**
     * Resolves the default HTTP method when not explicitly set by annotations.
     *
     * **Applicable to**: SpringMVC, JAX-RS (as fallback when no annotation found)
     *
     * **Not applicable to**: Feign (requires explicit method annotations),
     * gRPC (uses its own protocol), Actuator (operation annotations always specify method)
     */
    suspend fun resolveDefaultHttpMethod(method: PsiMethod): String? {
        return engine.evaluate(RuleKeys.METHOD_DEFAULT_HTTP, method)
    }

    /**
     * Resolves additional request headers to add to all endpoints.
     *
     * **Applicable to**: HTTP frameworks (SpringMVC, JAX-RS, Feign, Actuator)
     *
     * **Not applicable to**: gRPC (uses metadata, not HTTP headers)
     */
    suspend fun resolveAdditionalHeaders(method: PsiMethod): List<ApiHeader> {
        val raw = engine.evaluate(RuleKeys.METHOD_ADDITIONAL_HEADER, method) ?: return emptyList()
        return parseHeaderLines(raw)
    }

    /**
     * Resolves additional request parameters to add to all endpoints.
     *
     * **Applicable to**: HTTP frameworks (SpringMVC, JAX-RS, Feign, Actuator)
     *
     * **Not applicable to**: gRPC (fixed request message schema)
     */
    suspend fun resolveAdditionalParams(method: PsiMethod): List<ApiParameter> {
        val raw = engine.evaluate(RuleKeys.METHOD_ADDITIONAL_PARAM, method) ?: return emptyList()
        return parseParamLines(raw)
    }

    /**
     * Resolves additional response headers to add to all endpoints.
     *
     * **Applicable to**: HTTP frameworks (SpringMVC, JAX-RS, Feign, Actuator)
     *
     * **Not applicable to**: gRPC (uses trailing metadata)
     */
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

    /**
     * Resolves the path selection strategy when a method has multiple path mappings.
     *
     * **Applicable to**: SpringMVC, Feign (Spring-style @RequestMapping with multiple paths)
     *
     * **Not applicable to**: JAX-RS (@Path only supports single value),
     * gRPC (single path per method), Actuator (single path per operation)
     */
    suspend fun resolvePathMulti(method: PsiMethod): PathSelector {
        val raw = engine.evaluate(RuleKeys.PATH_MULTI, method)
        if (!raw.isNullOrBlank()) return PathSelector.fromRule(raw)
        return PathSelector.fromRule(settings.pathMulti)
    }
}
