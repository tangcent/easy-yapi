package com.itangcent.easyapi.exporter

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelUtils
import com.itangcent.easyapi.psi.type.GenericContext
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
 * val responseBody = builder.buildResponseBody(method, genericContext)
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
     * Builds the response body [ObjectModel] for the given method.
     *
     * Resolution order:
     * 1. **`method.return` rule** — if set, resolves the class by qualified name and delegates
     *    to [modelBuilder] to construct the model (e.g., `method.return = com.example.Result`).
     * 2. **Original return type** — uses [PsiClassHelper] to build from the method's declared
     *    return type, which respects `json.rule.convert` rules (e.g., `Mono<T>` → `T`).
     * 3. **Return type override** — if the original type yields no model and [returnTypeOverride]
     *    is provided, tries the unwrapped type (e.g., `ResponseEntity<T>` → `T` for SpringMVC).
     * 4. **`method.return.main` rule** — after building the model, applies the `@return` doc
     *    comment to a specific field within the response type (e.g., `data` in `Result<T>`).
     *
     * @param method The PSI method whose response body is being built
     * @param genericContext Generic type resolution context for the method's class
     * @param modelBuilder Strategy for constructing an ObjectModel from a PsiClass;
     *        defaults to [DefaultResponseModelBuilder] which uses [PsiClassHelper]
     * @param returnTypeOverride Optional function to unwrap container types (e.g., ResponseEntity, Mono);
     *        receives the original return type and returns the unwrapped type, or null if no unwrapping applies
     * @return The response body model, or null if the method returns void or no model could be built
     */
    suspend fun buildResponseBody(
        method: PsiMethod,
        genericContext: GenericContext = GenericContext.EMPTY,
        modelBuilder: ResponseModelBuilder = DefaultResponseModelBuilder(project, genericContext, method),
        returnTypeOverride: ((PsiType) -> PsiType?)? = null
    ): ObjectModel? {
        val returnTypeByRule = metadataResolver.resolveMethodReturn(method)
        if (!returnTypeByRule.isNullOrBlank()) {
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass(returnTypeByRule.trim(), GlobalSearchScope.allScope(project))
            if (psiClass != null) {
                return runCatching { modelBuilder.buildModel(psiClass) }.getOrNull()
            }
        }

        val returnType = method.returnType ?: return null
        if (returnType == PsiTypes.voidType()) return null

        var responseModel = runCatching {
            val helper = PsiClassHelper.getInstance(project)
            helper.buildObjectModelFromType(
                psiType = returnType,
                genericContext = genericContext,
                contextElement = method
            )
        }.getOrNull()

        if (responseModel == null && returnTypeOverride != null) {
            val unwrappedType = returnTypeOverride(returnType)
            if (unwrappedType != null && unwrappedType != PsiTypes.voidType() && unwrappedType !== returnType) {
                responseModel = runCatching {
                    val helper = PsiClassHelper.getInstance(project)
                    helper.buildObjectModelFromType(
                        psiType = unwrappedType,
                        genericContext = genericContext,
                        contextElement = method
                    )
                }.getOrNull()
            }
        }

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
     * Expands a body parameter into its [ObjectModel] representation.
     *
     * Skips primitive/wrapper types (java.lang.* and String) since they don't have
     * expandable fields. For complex types, uses [PsiClassHelper] which respects
     * `json.rule.convert` rules (e.g., `Mono<T>` → `T`, `RequestEntity<T>` → `T`).
     *
     * @param parameter The PSI parameter annotated as `@RequestBody`
     * @param genericContext Generic type resolution context
     * @return The expanded object model, or null for simple types or if expansion fails
     */
    suspend fun expandBodyParam(
        parameter: PsiParameter,
        genericContext: GenericContext = GenericContext.EMPTY
    ): ObjectModel? {
        val psiClass = PsiTypesUtil.getPsiClass(parameter.type) ?: return null
        val qualifiedName = psiClass.qualifiedName ?: return null
        if (qualifiedName.startsWith("java.lang.") || qualifiedName == "java.lang.String") return null
        return runCatching {
            val helper = PsiClassHelper.getInstance(project)
            helper.buildObjectModelFromType(
                psiType = parameter.type,
                genericContext = genericContext,
                contextElement = parameter
            )
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
        private val project: Project,
        private val genericContext: GenericContext,
        private val method: PsiMethod
    ) : ResponseModelBuilder {
        override suspend fun buildModel(psiClass: PsiClass): ObjectModel? {
            return runCatching {
                val helper = PsiClassHelper.getInstance(project)
                helper.buildObjectModel(psiClass)
            }.getOrNull()
        }
    }

    companion object {
        /**
         * Returns the [EndpointBuilder] instance for the given project.
         * The instance is managed by the IntelliJ service container.
         */
        fun getInstance(project: Project): EndpointBuilder = project.service()
    }
}
