package com.itangcent.easyapi.exporter.springmvc

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.core.context.project
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelUtils
import com.itangcent.easyapi.psi.type.GenericContext
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.psi.type.SpecialTypeHandler
import com.itangcent.easyapi.psi.type.TypeResolver
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.util.PathVariablePattern
import kotlinx.coroutines.withContext

/**
 * Exports API endpoints from Spring MVC controller classes.
 *
 * Parses Spring MVC annotations (@RequestMapping, @GetMapping, @PostMapping, etc.)
 * and extracts complete API endpoint information including:
 * - HTTP method and path
 * - Request parameters and headers
 * - Request body structure
 * - Response type
 *
 * ## Features
 * - Support for class-level and method-level mapping annotations
 * - Generic type resolution for parameterized controllers
 * - Form parameter expansion for complex types
 * - Rule-based customization via [RuleEngine]
 * - Support for custom meta-annotations
 *
 * @param actionContext The action context for dependency injection
 * @see ClassExporter for the interface
 * @see SpringControllerRecognizer for controller detection
 * @see RequestMappingResolver for mapping resolution
 */
class SpringMvcClassExporter(
    private val actionContext: ActionContext
) : ClassExporter {
    private val annotationHelper = UnifiedAnnotationHelper()
    private val engine = RuleEngine.getInstance(actionContext)
    private val controllerRecognizer = SpringControllerRecognizer(engine)
    private val mappingResolver = RequestMappingResolver(annotationHelper, engine)
    private val bindingResolver = SpringParameterBindingResolver(annotationHelper, engine)
    private val docHelper = actionContext.instance(DocHelper::class)
    private val settings by lazy { actionContext.instance(SettingBinder::class).read() }
    private val metadataResolver by lazy { ApiMetadataResolver(engine, docHelper, settings) }
    private val project: Project = actionContext.project()

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!controllerRecognizer.isController(psiClass)) return emptyList()
        if (metadataResolver.isIgnored(psiClass)) return emptyList()

        val className = read {
            psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        }
        LOG.info("before parse class:$className")

        engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, psiClass)

        val resolvedType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethods = read { resolvedType.methods() }
        val endpoints = ArrayList<ApiEndpoint>()
        try {
            for (resolvedMethod in resolvedMethods) {
                if (metadataResolver.isIgnored(resolvedMethod.psiMethod)) continue
                endpoints.addAll(exportMethod(psiClass, resolvedMethod))
            }
        } finally {
            engine.evaluate(RuleKeys.API_CLASS_PARSE_AFTER, psiClass)
        }
        LOG.info("after parse class:$className")
        return endpoints
    }

    private suspend fun exportMethod(
        psiClass: PsiClass,
        resolvedMethod: com.itangcent.easyapi.psi.type.ResolvedMethod
    ): List<ApiEndpoint> {
        val method = resolvedMethod.psiMethod
        val methodKey = read {
            "${psiClass.qualifiedName ?: psiClass.name}#${method.name}"
        }
        LOG.info("before parse method:$methodKey")

        withContext(IdeDispatchers.Background) {
            engine.evaluate(RuleKeys.API_METHOD_PARSE_BEFORE, method)
        }

        try {
            val mappings = read { mappingResolver.resolve(resolvedMethod) }
            if (mappings.isEmpty()) {
                LOG.info("after parse method:$methodKey")
                return emptyList()
            }

            val endpoints: List<ApiEndpoint>

            withContext(IdeDispatchers.ReadAction) {
                val genericContext = GenericContext(TypeResolver.resolveGenericParams(psiClass, emptyList()))

                val apiName = metadataResolver.resolveApiName(method)
                val folder = metadataResolver.resolveFolderName(method, psiClass)
                val description = metadataResolver.resolveMethodDoc(method)
                val classDesc = metadataResolver.resolveClassDoc(psiClass)
                val tags = metadataResolver.resolveApiTag(method)
                    ?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() }
                    ?: emptyList()

                val resolvedBindings = resolveParameterBindings(resolvedMethod)
                val params = buildParameters(resolvedBindings)
                val paramHeaders = extractParamHeaders(resolvedBindings)
                val body = buildRequestBody(resolvedBindings, genericContext)
                val response = read { ReturnTypeUnwrapper.unwrap(method.returnType) }
                val responseBody = buildResponseBody(method, genericContext)

                val additionalHeaders = metadataResolver.resolveAdditionalHeaders(method)
                val additionalParams = metadataResolver.resolveAdditionalParams(method)
                val additionalResponseHeaders = metadataResolver.resolveAdditionalResponseHeaders(method)

                val defaultHttpMethod = metadataResolver.resolveDefaultHttpMethod(method)
                    ?.let { HttpMethod.fromSpring(it) }

                // Filter mappings by path.multi strategy before building endpoints
                val pathSelector = metadataResolver.resolvePathMulti(method)
                val selectedMappings = pathSelector.select(mappings) { it.path }

                LOG.info("after parse method:$methodKey")

                endpoints = selectedMappings.map { mapping ->
                    val contentType = resolveContentType(method, mapping, body != null, params)
                    val headers = buildHeaders(contentType, mapping, paramHeaders, additionalHeaders)
                    val inferredMethod = inferHttpMethod(mapping.method, body != null, params, defaultHttpMethod)

                    val pathVariablePatterns = PathVariablePattern.extractPathVariablesFromPath(mapping.path)
                    val normalizedPath = PathVariablePattern.normalizePath(mapping.path)

                    val pathParamsFromPath = pathVariablePatterns.map { pattern ->
                        ApiParameter(
                            name = pattern.name,
                            type = ParameterType.TEXT,
                            required = true,
                            binding = ParameterBinding.Path,
                            defaultValue = pattern.defaultValue,
                            description = "Path variable with possible values: ${pattern.possibleValues.joinToString(", ")}",
                            enumValues = pattern.possibleValues
                        )
                    }

                    val mergedParams = mergePathParameters(params + additionalParams, pathParamsFromPath)

                    ApiEndpoint(
                        name = apiName,
                        folder = folder,
                        description = description,
                        tags = tags,
                        sourceClass = psiClass,
                        sourceMethod = method,
                        className = psiClass.qualifiedName ?: psiClass.name,
                        classDescription = classDesc,
                        metadata = HttpMetadata(
                            path = normalizedPath,
                            method = inferredMethod,
                            parameters = mergedParams,
                            headers = headers + additionalResponseHeaders,
                            contentType = contentType,
                            body = body,
                            responseBody = responseBody,
                            responseType = response.toString()
                        )
                    )
                }
            }

            withContext(IdeDispatchers.Background) {
                // Fire export.after for each endpoint
                for (endpoint in endpoints) {
                    engine.evaluate(RuleKeys.EXPORT_AFTER, method) { ctx ->
                        ctx.setExt("api", endpoint)
                    }
                }
            }

            return endpoints
        } finally {
            withContext(IdeDispatchers.Background) {
                engine.evaluate(RuleKeys.API_METHOD_PARSE_AFTER, method)
            }
        }
    }

    private fun isFileTypeName(type: ParameterType): Boolean = type == ParameterType.FILE

    private suspend fun resolveContentType(
        method: PsiMethod,
        mapping: ResolvedMapping,
        hasBody: Boolean,
        params: List<ApiParameter>
    ): String? {
        val override = engine.evaluate(RuleKeys.METHOD_CONTENT_TYPE, method)
        if (!override.isNullOrBlank()) return override

        if (mapping.consumes.isNotEmpty()) return mapping.consumes.firstOrNull()

        if (hasBody) return "application/json"

        val hasFormParams = params.any { it.binding == ParameterBinding.Form }
        if (hasFormParams) {
            val hasFileParams = params.any { it.binding == ParameterBinding.Form && isFileTypeName(it.type) }
            return if (hasFileParams) "multipart/form-data" else "application/x-www-form-urlencoded"
        }

        return null
    }

    private suspend fun inferHttpMethod(
        resolvedMethod: HttpMethod,
        hasBody: Boolean,
        params: List<ApiParameter>,
        defaultHttpMethod: HttpMethod? = null
    ): HttpMethod {
        if (resolvedMethod != HttpMethod.NO_METHOD) return resolvedMethod

        if (hasBody) return HttpMethod.POST

        if (params.any { it.binding == ParameterBinding.Form }) return HttpMethod.POST

        return defaultHttpMethod ?: HttpMethod.GET
    }

    /**
     * Pre-resolved binding for a single method parameter.
     */
    private data class ResolvedParamBinding(
        val parameter: PsiParameter,
        val binding: ParameterBinding
    )

    /**
     * Resolves bindings for all parameters in a single pass.
     * Ignored parameters (HttpServletRequest, etc.) are excluded from the result.
     *
     * Uses [ResolvedMethod] to support annotation inheritance from super methods
     * (interfaces, base classes).
     */
    private suspend fun resolveParameterBindings(resolvedMethod: com.itangcent.easyapi.psi.type.ResolvedMethod): List<ResolvedParamBinding> {
        val method = resolvedMethod.psiMethod
        val parameters = read { method.parameterList.parameters.toList() }
        return parameters.mapIndexedNotNull { index, p ->
            val binding = bindingResolver.resolve(p, resolvedMethod, index) ?: ParameterBinding.Query
            if (binding == ParameterBinding.Ignored) return@mapIndexedNotNull null
            ResolvedParamBinding(p, binding)
        }
    }

    private suspend fun buildParameters(
        bindings: List<ResolvedParamBinding>
    ): List<ApiParameter> {
        val result = ArrayList<ApiParameter>()
        for ((p, binding) in bindings) {
            if (binding == ParameterBinding.Body) continue

            val paramName = p.name
            LOG.info("before parse param:$paramName")

            if (metadataResolver.isParamIgnored(p)) {
                LOG.info("after parse param:$paramName")
                continue
            }

            engine.evaluate(RuleKeys.API_PARAM_PARSE_BEFORE, p)

            try {
                if (binding == ParameterBinding.Form) {
                    val expandedParams = expandFormParameter(p)
                    if (expandedParams.isNotEmpty()) {
                        result.addAll(expandedParams)
                    } else {
                        result.add(buildSingleParameter(p, paramName, binding))
                    }
                } else if (binding == ParameterBinding.Query) {
                    val expandedParams = expandQueryParameter(p)
                    if (expandedParams.isNotEmpty()) {
                        result.addAll(expandedParams)
                    } else {
                        result.add(buildSingleParameter(p, paramName, binding))
                    }
                } else {
                    result.add(buildSingleParameter(p, paramName, binding))
                }
            } finally {
                engine.evaluate(RuleKeys.API_PARAM_PARSE_AFTER, p)
            }
            LOG.info("after parse param:$paramName")
        }
        return result
    }

    private suspend fun buildSingleParameter(
        p: PsiParameter,
        paramName: String,
        binding: ParameterBinding
    ): ApiParameter {
        val name = metadataResolver.resolveParamName(p, paramName)
        val required = metadataResolver.isParamRequired(p)
        val rawType = metadataResolver.resolveParamType(p, p.type.canonicalText)
        val type = ParameterType.fromTypeName(rawType)
        val doc = metadataResolver.resolveParamDoc(p)
        val defaultValue = metadataResolver.resolveParamDefaultValue(p)
        val demo = metadataResolver.resolveParamDemo(p)
        val mock = metadataResolver.resolveParamMock(p)
        return ApiParameter(
            name = name,
            type = type,
            required = required,
            binding = binding,
            defaultValue = defaultValue,
            description = doc,
            example = demo ?: mock
        )
    }


    private fun mergePathParameters(
        methodParams: List<ApiParameter>,
        pathParams: List<ApiParameter>
    ): List<ApiParameter> {
        if (pathParams.isEmpty()) return methodParams
        val pathParamMap = pathParams.associateBy { it.name }
        val result = mutableListOf<ApiParameter>()

        for (param in methodParams) {
            if (param.binding == ParameterBinding.Path) {
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

    private suspend fun expandFormParameter(parameter: PsiParameter): List<ApiParameter> {
        val formExpanded = settings.formExpanded
        if (!formExpanded) {
            return emptyList()
        }
        return expandComplexParameter(parameter, ParameterBinding.Form)
    }

    private suspend fun expandQueryParameter(parameter: PsiParameter): List<ApiParameter> {
        val queryExpanded = settings.queryExpanded
        if (!queryExpanded) {
            return emptyList()
        }
        return expandComplexParameter(parameter, ParameterBinding.Query)
    }

    private suspend fun expandComplexParameter(parameter: PsiParameter, binding: ParameterBinding): List<ApiParameter> {
        val psiClass = PsiTypesUtil.getPsiClass(parameter.type) ?: return emptyList()
        val qualifiedName = psiClass.qualifiedName ?: return emptyList()

        if (SpecialTypeHandler.isFileType(qualifiedName)) {
            return emptyList()
        }

        if (qualifiedName.startsWith("java.lang.") || qualifiedName == "java.lang.String") {
            return emptyList()
        }

        if (isCollectionType(psiClass)) {
            return emptyList()
        }

        // Use param.max.depth rule if configured
        val paramMaxDepth = engine.evaluate(RuleKeys.PARAM_MAX_DEPTH, parameter) ?: 5

        val helper = PsiClassHelper.getInstance(project)
        val objectModel = helper.buildObjectModel(psiClass, actionContext, maxDepth = paramMaxDepth)
            ?: return emptyList()

        val objectData = objectModel.asObject() ?: return emptyList()
        val result = ArrayList<ApiParameter>()

        val flattenedFields = objectData.flattenFields()

        for ((fieldPath, fieldModel) in flattenedFields) {
            val fieldType = resolveFieldType(fieldModel)
            val fieldDoc = fieldModel.comment
            val fieldRequired = fieldModel.required
            val fieldDefault = fieldModel.defaultValue

            result.add(
                ApiParameter(
                    name = fieldPath,
                    type = fieldType,
                    required = fieldRequired,
                    binding = binding,
                    defaultValue = fieldDefault,
                    description = fieldDoc
                )
            )
        }

        return result
    }

    private fun isCollectionType(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false
        return qualifiedName == "java.util.Collection" ||
                qualifiedName == "java.util.List" ||
                qualifiedName == "java.util.Set" ||
                qualifiedName == "java.util.ArrayList" ||
                qualifiedName == "java.util.HashSet" ||
                qualifiedName == "java.util.LinkedList" ||
                qualifiedName.startsWith("kotlin.collections.")
    }

    private fun resolveFieldType(fieldModel: FieldModel): ParameterType {
        val model = fieldModel.model
        return when {
            isFileType(model) || isFileArrayType(model) -> ParameterType.FILE
            else -> ParameterType.TEXT
        }
    }

    private fun isFileType(model: ObjectModel): Boolean {
        val single = model.asSingle() ?: return false
        return single.type == "__file__" ||
                single.type.contains("MultipartFile", ignoreCase = true) ||
                single.type.contains("Part", ignoreCase = true)
    }

    private fun isFileArrayType(model: ObjectModel): Boolean {
        val array = model.asArray() ?: return false
        return isFileType(array.item)
    }


    private suspend fun extractParamHeaders(bindings: List<ResolvedParamBinding>): List<ApiHeader> {
        val headers = ArrayList<ApiHeader>()
        for ((p, binding) in bindings) {
            if (binding != ParameterBinding.Header) continue
            val name = metadataResolver.resolveParamName(p, p.name)
            val defaultValue = metadataResolver.resolveParamDefaultValue(p)
            val example = metadataResolver.resolveParamDemo(p)
            headers.add(ApiHeader(name = name, value = defaultValue ?: example))
        }
        return headers
    }

    /**
     * Build the full header list from all sources:
     * 1. Content-Type from contentType resolution
     * 2. Headers from @RequestMapping(headers = {...})
     * 3. Headers from @RequestHeader parameters
     * 4. Additional headers from method.additional.header rule
     */
    private fun buildHeaders(
        contentType: String?,
        mapping: ResolvedMapping,
        paramHeaders: List<ApiHeader>,
        additionalHeaders: List<ApiHeader> = emptyList()
    ): List<ApiHeader> {
        val seen = mutableSetOf<String>()
        val result = ArrayList<ApiHeader>()

        // Content-Type header
        if (!contentType.isNullOrBlank()) {
            result.add(ApiHeader(name = "Content-Type", value = contentType))
            seen.add("content-type")
        }

        // Headers from @RequestMapping(headers = {...})
        for ((name, value) in mapping.headers) {
            val key = name.lowercase()
            if (key !in seen) {
                result.add(ApiHeader(name = name, value = value))
                seen.add(key)
            }
        }

        // Headers from @RequestHeader parameters
        for (h in paramHeaders) {
            val key = h.name.lowercase()
            if (key !in seen) {
                result.add(h)
                seen.add(key)
            }
        }

        // Additional headers from method.additional.header rule
        for (h in additionalHeaders) {
            val key = h.name.lowercase()
            if (key !in seen) {
                result.add(h)
                seen.add(key)
            }
        }

        return result
    }

    private suspend fun buildRequestBody(
        bindings: List<ResolvedParamBinding>,
        genericContext: GenericContext = GenericContext.EMPTY
    ): ObjectModel? {
        for ((p, binding) in bindings) {
            if (binding != ParameterBinding.Body) continue
            return expandBodyParam(p, genericContext)
        }
        return null
    }

    private suspend fun buildResponseBody(
        method: PsiMethod,
        genericContext: GenericContext = GenericContext.EMPTY
    ): ObjectModel? {
        // Check method.return rule for custom return type override
        val returnTypeByRule = metadataResolver.resolveMethodReturn(method)
        if (!returnTypeByRule.isNullOrBlank()) {
            val psiClass = com.intellij.psi.JavaPsiFacade.getInstance(method.project)
                .findClass(returnTypeByRule.trim(), com.intellij.psi.search.GlobalSearchScope.allScope(method.project))
            if (psiClass != null) {
                return runCatching {
                    val helper = PsiClassHelper.getInstance(project)
                    helper.buildObjectModel(psiClass, actionContext)
                }.getOrNull()
            }
        }

        val returnType = method.returnType ?: return null

        // First try with the original return type — json.rule.convert rules
        // (e.g., for ResponseEntity, Mono, Flux) may handle unwrapping.
        var responseModel = runCatching {
            val helper = PsiClassHelper.getInstance(project)
            helper.buildObjectModelFromType(
                psiType = returnType,
                actionContext = actionContext,
                genericContext = genericContext,
                contextElement = method
            )
        }.getOrNull()

        // If the original type didn't produce a model, try the unwrapped type as fallback
        if (responseModel == null) {
            val unwrappedType = ReturnTypeUnwrapper.unwrapPsiType(returnType)
            if (unwrappedType == null || unwrappedType == PsiTypes.voidType()) return null
            if (unwrappedType !== returnType) {
                responseModel = runCatching {
                    val helper = PsiClassHelper.getInstance(project)
                    helper.buildObjectModelFromType(
                        psiType = unwrappedType,
                        actionContext = actionContext,
                        genericContext = genericContext,
                        contextElement = method
                    )
                }.getOrNull()
            }
        }

        if (responseModel == null) return null

        // method.return.main rule — specifies a field name within the response type
        // where the @return doc comment should be placed.
        // e.g., rule: method.return.main[groovy:it.returnType().isExtend("Result")]=data
        // For Result<Void> with @return "processed result", this attaches "processed result" to the "data" field.
        val returnMain = metadataResolver.resolveMethodReturnMain(method)
        val mainField = if (!returnMain.isNullOrBlank()) {
            returnMain
        } else if (settings.inferReturnMain) {
            // Auto-detect: find the generic type parameter field (e.g., data: T in Result<T>)
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

    private suspend fun expandBodyParam(
        parameter: PsiParameter,
        genericContext: GenericContext = GenericContext.EMPTY
    ): ObjectModel? {
        // Use buildObjectModelFromType which evaluates json.rule.convert rules
        // (e.g., Mono<T> → T, RequestEntity<T> → T)
        return runCatching {
            val helper = PsiClassHelper.getInstance(project)
            helper.buildObjectModelFromType(
                psiType = parameter.type,
                actionContext = actionContext,
                genericContext = genericContext,
                contextElement = parameter
            )
        }.getOrNull()
    }

    companion object : IdeaLog
}
