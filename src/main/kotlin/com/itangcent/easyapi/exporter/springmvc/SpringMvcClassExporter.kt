package com.itangcent.easyapi.exporter.springmvc

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiTypes
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.EndpointBuilder
import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.GenericContext
import com.itangcent.easyapi.psi.type.InheritanceHelper
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
 * @param project The IntelliJ project
 * @see ClassExporter for the interface
 * @see SpringControllerRecognizer for controller detection
 * @see RequestMappingResolver for mapping resolution
 */
class SpringMvcClassExporter(
    private val project: Project
) : ClassExporter {

    override val frameworkName: String = "SpringMVC"

    private val annotationHelper = UnifiedAnnotationHelper()
    private val engine = RuleEngine.getInstance(project)
    private val controllerRecognizer = SpringControllerRecognizer(engine)
    private val mappingResolver = RequestMappingResolver(annotationHelper, engine)
    private val bindingResolver = SpringParameterBindingResolver(annotationHelper, engine)
    private val docHelper = UnifiedDocHelper.getInstance(project)
    private val settings by lazy { SettingBinder.getInstance(project).read() }
    private val metadataResolver by lazy { ApiMetadataResolver(engine, docHelper, settings) }
    private val endpointBuilder = EndpointBuilder.getInstance(project)

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
                    ?.split(",", "\n")?.map { it.trim() }?.distinct()?.filter { it.isNotBlank() }
                    ?: emptyList()

                val resolvedBindings = resolveParameterBindings(resolvedMethod)
                val params = buildParameters(resolvedBindings, genericContext)
                val paramHeaders = extractParamHeaders(resolvedBindings)
                val body = buildRequestBody(resolvedBindings, genericContext)
                val response = read { ReturnTypeUnwrapper.unwrap(method.returnType) }
                val responseBody = buildResponseBody(method, genericContext)

                val additionalHeaders = metadataResolver.resolveAdditionalHeaders(method)
                val additionalParams = metadataResolver.resolveAdditionalParams(method)
                val additionalResponseHeaders = metadataResolver.resolveAdditionalResponseHeaders(method)
                val apiOpen = metadataResolver.isApiOpen(method)
                val apiStatus = metadataResolver.resolveApiStatus(method)

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

                    val mergedParams = endpointBuilder.mergePathParameters(params + additionalParams, pathParamsFromPath)

                    ApiEndpoint(
                        name = apiName,
                        folder = folder,
                        description = description,
                        tags = tags,
                        status = apiStatus,
                        open = apiOpen,
                        sourceClass = psiClass,
                        sourceMethod = method,
                        className = psiClass.qualifiedName ?: psiClass.name,
                        classDescription = classDesc,
                        metadata = httpMetadata(
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
        bindings: List<ResolvedParamBinding>,
        genericContext: GenericContext = GenericContext.EMPTY
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
                    val expandedParams = expandFormParameter(p, genericContext)
                    if (expandedParams.isNotEmpty()) {
                        result.addAll(expandedParams)
                    } else {
                        result.add(buildSingleParameter(p, paramName, binding))
                    }
                } else if (binding == ParameterBinding.Query) {
                    val expandedParams = expandQueryParameter(p, genericContext)
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
            example = demo ?: mock,
            jsonType = rawType
        )
    }

    private suspend fun expandFormParameter(
        parameter: PsiParameter,
        genericContext: GenericContext = GenericContext.EMPTY
    ): List<ApiParameter> {
        val formExpanded = SettingBinder.getInstance(project).read().formExpanded
        if (!formExpanded) {
            return emptyList()
        }
        return expandComplexParameter(parameter, ParameterBinding.Form, genericContext)
    }

    private suspend fun expandQueryParameter(
        parameter: PsiParameter,
        genericContext: GenericContext = GenericContext.EMPTY
    ): List<ApiParameter> {
        val queryExpanded = SettingBinder.getInstance(project).read().queryExpanded
        if (!queryExpanded) {
            return emptyList()
        }
        return expandComplexParameter(parameter, ParameterBinding.Query, genericContext)
    }

    private suspend fun expandComplexParameter(
        parameter: PsiParameter,
        binding: ParameterBinding,
        genericContext: GenericContext = GenericContext.EMPTY
    ): List<ApiParameter> {
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
        val objectModel = helper.buildObjectModelFromType(
            psiType = parameter.type,
            genericContext = genericContext,
            contextElement = parameter,
            maxDepth = paramMaxDepth
        ) ?: return emptyList()

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

    private fun isCollectionType(psiClass: PsiClass): Boolean = InheritanceHelper.isCollection(psiClass)

    private fun resolveFieldType(fieldModel: FieldModel): ParameterType {
        val model = fieldModel.model
        return when {
            isFileType(model) || isFileArrayType(model) -> ParameterType.FILE
            else -> ParameterType.TEXT
        }
    }

    private fun isFileType(model: ObjectModel): Boolean {
        val single = model.asSingle() ?: return false
        return single.type == "file" ||
                single.type == "__file__" ||
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
    ): List<ApiHeader> = endpointBuilder.buildHeaders(
        contentType = contentType,
        paramHeaders = paramHeaders,
        additionalHeaders = additionalHeaders,
        mappingHeaders = mapping.headers.toList()
    )

    private suspend fun buildRequestBody(
        bindings: List<ResolvedParamBinding>,
        genericContext: GenericContext = GenericContext.EMPTY
    ): ObjectModel? {
        for ((p, binding) in bindings) {
            if (binding != ParameterBinding.Body) continue
            return endpointBuilder.expandBodyParam(p, genericContext)
        }
        return null
    }

    private suspend fun buildResponseBody(
        method: PsiMethod,
        genericContext: GenericContext = GenericContext.EMPTY
    ): ObjectModel? = endpointBuilder.buildResponseBody(
        method = method,
        genericContext = genericContext,
        returnTypeOverride = { returnType ->
            val unwrappedType = ReturnTypeUnwrapper.unwrapPsiType(returnType)
            if (unwrappedType != null && unwrappedType != PsiTypes.voidType() && unwrappedType !== returnType) {
                unwrappedType
            } else {
                null
            }
        }
    )

    companion object : IdeaLog
}
