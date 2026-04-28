package com.itangcent.easyapi.exporter

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelUtils
import com.itangcent.easyapi.psi.type.PrimitiveKind
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings

/**
 * Shared utility for building API endpoint components across all framework exporters.
 *
 * Centralizes common logic that was previously duplicated in SpringMVC, JAX-RS, Feign,
 * gRPC, and Actuator exporters, including:
 * - Response body construction with rule-based overrides (`method.return`, `method.return.main`)
 * - Header deduplication and merging from multiple sources
 * - Request body parameter expansion
 * - Path parameter merging with enum/default/description enrichment
 *
 * Registered as a IntelliJ project-level service, so it can resolve dependencies
 * (RuleEngine, DocHelper, Settings, ApiMetadataResolver) from the project automatically.
 *
 * Usage:
 * ```
 * val builder = EndpointBuilder.getInstance(project)
 * val headers = builder.buildHeaders(contentType, paramHeaders, additionalHeaders)
 * val responseBody = builder.buildResponseBody(method, resolvedReturnType)
 * ```
 */
class EndpointBuilder(private val project: Project) {

    private val settings: Settings by lazy { SettingBinder.getInstance(project).read() }
    private val docHelper: DocHelper by lazy { UnifiedDocHelper.getInstance(project) }
    private val metadataResolver: ApiMetadataResolver by lazy {
        ApiMetadataResolver(
            RuleEngine.getInstance(project),
            UnifiedDocHelper.getInstance(project),
            settings
        )
    }

    /**
     * Strategy interface for building an [ObjectModel] from a [PsiClass].
     *
     * Allows framework-specific model construction (e.g., gRPC uses protobuf parsing,
     * Actuator uses [JsonType], while standard SpringMVC/JAX-RS/Feign use [PsiClassHelper]).
     */
    fun interface ResponseModelBuilder {
        suspend fun buildModel(psiClass: PsiClass): ObjectModel?
    }

    /**
     * Builds the response body model from an already-resolved return type.
     *
     * Resolution order:
     * 1. **`method.return` rule** — if set, resolves the class by qualified name and delegates
     *    to [modelBuilder] to construct the model.
     * 2. **Resolved return type** — uses [PsiClassHelper.buildObjectModel] with the
     *    already-resolved type (generics already substituted by the caller).
     * 3. **`method.return.main` rule** — after building the model, applies the `@return` doc
     *    comment to a specific field within the response type.
     *
     * @param method The PSI method (for rule evaluation and doc extraction)
     * @param resolvedReturnType The already-resolved return type from [ResolvedMethod.returnType]
     * @param modelBuilder Strategy for constructing an ObjectModel from a PsiClass
     * @return The response body model, or null if void or no model could be built
     */
    suspend fun buildResponseBody(
        method: PsiMethod,
        resolvedReturnType: ResolvedType,
        modelBuilder: ResponseModelBuilder = DefaultResponseModelBuilder(project)
    ): ObjectModel? {
        LOG.info("buildResponseBody: method=${method.name}, resolvedReturnType=${resolvedReturnType.qualifiedName()}")

        val returnTypeByRule = metadataResolver.resolveMethodReturn(method)
        if (!returnTypeByRule.isNullOrBlank()) {
            LOG.info("buildResponseBody: method.return rule override: $returnTypeByRule")
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass(returnTypeByRule.trim(), GlobalSearchScope.allScope(project))
            if (psiClass != null) {
                return runCatching { modelBuilder.buildModel(psiClass) }.getOrNull()
            }
        }

        if (resolvedReturnType is ResolvedType.PrimitiveType && resolvedReturnType.kind == PrimitiveKind.VOID) {
            LOG.info("buildResponseBody: void return type, returning null")
            return null
        }

        val responseModel = runCatching {
            PsiClassHelper.getInstance(project).buildObjectModel(resolvedReturnType)
        }.getOrNull()
        LOG.info("buildResponseBody: buildObjectModel result: $responseModel")

        if (responseModel == null) return null
        return applyReturnMain(method, responseModel)
    }

    /**
     * Applies the `method.return.main` rule to a response model.
     *
     * The `method.return.main` rule specifies a field name within the response type where
     * the `@return` doc comment should be placed. For example, with `Result<T>`:
     * - Rule: `method.return.main[groovy:it.returnType().isExtend("Result")]=data`
     * - `@return "processed result"` is attached to the `data` field instead of the root
     *
     * If no explicit rule is set and `settings.inferReturnMain` is true, auto-detects
     * the generic type parameter field (e.g., `data: T` in `Result<T>`).
     *
     * @param method The PSI method to resolve the rule from
     * @param responseModel The response model to apply the rule to
     * @return The updated model with the field comment applied, or the original model unchanged
     */
    suspend fun applyReturnMain(
        method: PsiMethod,
        responseModel: ObjectModel
    ): ObjectModel {
        val returnMain = metadataResolver.resolveMethodReturnMain(method)
        val mainField = if (!returnMain.isNullOrBlank()) {
            returnMain
        } else if (settings.inferReturnMain) {
            ObjectModelUtils.findGenericFieldName(responseModel)
        } else null

        if (!mainField.isNullOrBlank()) {
            val descOfReturn = docHelper.findDocByTag(method, "return")
            if (!descOfReturn.isNullOrBlank()) {
                val updated = ObjectModelUtils.addFieldComment(responseModel, mainField, descOfReturn)
                if (updated != null) return updated
            }
        }

        return responseModel
    }

    /**
     * Builds a deduplicated list of HTTP headers from multiple sources.
     *
     * Headers are added in priority order (first occurrence wins):
     * 1. **Content-Type** — from the resolved content type string
     * 2. **Mapping headers** — from annotation attributes (e.g., `@RequestMapping(headers=...)`);
     *    Content-Type in mapping headers is skipped since it's handled in step 1
     * 3. **Parameter headers** — from `@RequestHeader`-annotated method parameters
     * 4. **Additional headers** — from `method.additional.header` rule
     * 5. **Additional response headers** — from `method.additional.response.header` rule
     *
     * Deduplication is case-insensitive (e.g., "Content-Type" and "content-type" are the same).
     *
     * @param contentType The resolved Content-Type value, or null if not applicable
     * @param paramHeaders Headers from `@RequestHeader` parameters
     * @param additionalHeaders Headers from `method.additional.header` rule
     * @param additionalResponseHeaders Headers from `method.additional.response.header` rule
     * @param mappingHeaders Headers from annotation attributes as name-value pairs
     * @return Deduplicated list of headers in priority order
     */
    fun buildHeaders(
        contentType: String?,
        paramHeaders: List<ApiHeader> = emptyList(),
        additionalHeaders: List<ApiHeader> = emptyList(),
        additionalResponseHeaders: List<ApiHeader> = emptyList(),
        mappingHeaders: List<Pair<String, String>> = emptyList()
    ): List<ApiHeader> {
        val seen = mutableSetOf<String>()
        val result = ArrayList<ApiHeader>()

        if (!contentType.isNullOrBlank()) {
            result.add(ApiHeader(name = "Content-Type", value = contentType))
            seen.add("content-type")
        }

        for ((name, value) in mappingHeaders) {
            if (!name.equals("Content-Type", ignoreCase = true) && name.lowercase() !in seen) {
                result.add(ApiHeader(name = name, value = value))
                seen.add(name.lowercase())
            }
        }

        for (h in paramHeaders) {
            val key = h.name.lowercase()
            if (key !in seen) {
                result.add(h)
                seen.add(key)
            }
        }

        for (h in additionalHeaders) {
            val key = h.name.lowercase()
            if (key !in seen) {
                result.add(h)
                seen.add(key)
            }
        }

        for (h in additionalResponseHeaders) {
            val key = h.name.lowercase()
            if (key !in seen) {
                result.add(h)
                seen.add(key)
            }
        }

        return result
    }

    /**
     * Expands a body parameter using an already-resolved type.
     *
     * Skips primitive/wrapper types since they don't have expandable fields.
     * For complex types, uses [PsiClassHelper.buildObjectModel] with the
     * already-resolved type.
     *
     * @param resolvedParamType The already-resolved parameter type
     * @return The expanded object model, or null for simple types
     */
    suspend fun expandBodyParam(resolvedParamType: ResolvedType): ObjectModel? {
        if (resolvedParamType is ResolvedType.PrimitiveType) return null
        if (resolvedParamType is ResolvedType.UnresolvedType) {
            val text = resolvedParamType.canonicalText
            if (text.startsWith("java.lang.") || text == "java.lang.String" || text == "String") return null
        }
        return runCatching {
            PsiClassHelper.getInstance(project).buildObjectModel(resolvedParamType)
        }.getOrNull()
    }

    /**
     * Merges method parameters with path parameters extracted from the URL pattern.
     *
     * Path parameters from the URL pattern (e.g., `{id}` in `/users/{id}`) may carry
     * additional metadata (enum values, default values, descriptions) that should enrich
     * the corresponding method parameters. This method:
     *
     * 1. For each method parameter with `ParameterBinding.Path`, if a matching path param
     *    exists (by name), merges the path param's metadata:
     *    - `enumValues` from path param takes priority if present (path enums are authoritative)
     *    - `defaultValue` from method param takes priority; path param used as fallback if blank
     *    - `description` from method param takes priority; path param used as fallback if blank
     * 2. Appends any path params not already present in the method params list
     *
     * @param methodParams Parameters resolved from the method signature
     * @param pathParams Parameters extracted from the URL path pattern
     * @return Merged parameter list with enriched path parameters
     */
    fun mergePathParameters(
        methodParams: List<ApiParameter>,
        pathParams: List<ApiParameter>
    ): List<ApiParameter> {
        if (pathParams.isEmpty()) return methodParams
        val pathParamMap = pathParams.associateBy { it.name }
        val result = mutableListOf<ApiParameter>()

        for (param in methodParams) {
            if (param.binding == com.itangcent.easyapi.exporter.model.ParameterBinding.Path) {
                val pathParam = pathParamMap[param.name]
                if (pathParam != null) {
                    result.add(
                        param.copy(
                            enumValues = pathParam.enumValues ?: param.enumValues,
                            defaultValue = param.defaultValue?.takeIf { it.isNotBlank() }
                                ?: pathParam.defaultValue,
                            description = param.description?.takeIf { it.isNotBlank() }
                                ?: pathParam.description
                        )
                    )
                } else {
                    result.add(param)
                }
            } else {
                result.add(param)
            }
        }

        val existingNames = result.map { it.name }.toSet()
        for (pathParam in pathParams) {
            if (pathParam.name !in existingNames) {
                result.add(pathParam)
            }
        }

        return result
    }

    /**
     * Default [ResponseModelBuilder] that uses [PsiClassHelper] to build models.
     */
    private class DefaultResponseModelBuilder(
        private val project: Project
    ) : ResponseModelBuilder {
        override suspend fun buildModel(psiClass: PsiClass): ObjectModel? {
            return runCatching {
                val helper = PsiClassHelper.getInstance(project)
                helper.buildObjectModel(psiClass)
            }.getOrNull()
        }
    }

    companion object : com.itangcent.easyapi.logging.IdeaLog {
        /**
         * Returns the [EndpointBuilder] instance for the given project.
         * The instance is managed by the IntelliJ service container.
         */
        fun getInstance(project: Project): EndpointBuilder = project.service()
    }
}
