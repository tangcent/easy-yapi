package com.itangcent.easyapi.exporter.feign

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiHeader
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.ParameterType
import com.itangcent.easyapi.exporter.springmvc.RequestMappingResolver
import com.itangcent.easyapi.exporter.springmvc.SpringParameterBindingResolver
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.util.PathVariablePattern

/**
 * Exports API endpoints from Feign client interfaces.
 *
 * This exporter processes interfaces annotated with @FeignClient and supports:
 * - Spring MVC annotations (@RequestMapping, @GetMapping, etc.)
 * - Native Feign annotations (@RequestLine, @Headers, @Body)
 *
 * ## Supported Annotations
 * ### Feign Client
 * - `@FeignClient` - Marks the interface as a Feign client
 *
 * ### Spring MVC (on interface methods)
 * - `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc.
 * - `@RequestParam`, `@PathVariable`, `@RequestBody`, etc.
 *
 * ### Native Feign
 * - `@RequestLine` - HTTP method and path
 * - `@Headers` - Request headers
 * - `@Body` - Request body template
 * - `@Param` - Variable substitution
 *
 * @see FeignClientRecognizer for client interface detection
 * @see FeignPathResolver for path resolution
 * @see NativeFeignAnnotationParser for native Feign annotation parsing
 */
class FeignClassExporter(
    private val actionContext: ActionContext,
    feignEnable: Boolean = true
) : ClassExporter {
    private val annotationHelper = UnifiedAnnotationHelper()
    private val engine = RuleEngine.getInstance(actionContext)
    private val recognizer = FeignClientRecognizer(engine, feignEnable)
    private val pathResolver = FeignPathResolver(annotationHelper)
    private val nativeParser = NativeFeignAnnotationParser(annotationHelper)
    private val springMappingResolver = RequestMappingResolver(annotationHelper, engine)
    private val springParamResolver = SpringParameterBindingResolver(annotationHelper, engine)
    private val docHelper = actionContext.instance(DocHelper::class)
    private val metadataResolver = ApiMetadataResolver(engine, docHelper)
    private val project: Project? = actionContext.instanceOrNull(Project::class)

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isFeignClient(psiClass)) return emptyList()

        val className = psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        LOG.info("before parse class:$className")

        engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, psiClass)

        val info = pathResolver.resolve(psiClass)
        val basePath = normalizePath(info.path ?: "")

        val classType = ResolvedType.ClassType(psiClass, emptyList())
        val endpoints = ArrayList<ApiEndpoint>()
        try {
            for (resolvedMethod in classType.methods()) {
                if (resolvedMethod.psiMethod.isConstructor) continue
                endpoints.addAll(exportMethod(basePath, psiClass, resolvedMethod))
            }
        } finally {
            engine.evaluate(RuleKeys.API_CLASS_PARSE_AFTER, psiClass)
        }
        LOG.info("after parse class:$className")
        return endpoints
    }

    private suspend fun exportMethod(
        basePath: String,
        psiClass: PsiClass,
        resolvedMethod: com.itangcent.easyapi.psi.type.ResolvedMethod
    ): List<ApiEndpoint> {
        val method = resolvedMethod.psiMethod
        val methodKey = "${psiClass.qualifiedName ?: psiClass.name}#${method.name}"
        LOG.info("before parse method:$methodKey")

        engine.evaluate(RuleKeys.API_METHOD_PARSE_BEFORE, method)

        try {
            val requestLine = nativeParser.parseRequestLine(method)
            if (requestLine != null) {
                val endpoint = buildFromNativeFeign(basePath, psiClass, method, requestLine)
                LOG.info("after parse method:$methodKey")
                engine.evaluate(RuleKeys.EXPORT_AFTER, method) { ctx ->
                    ctx.setExt("api", endpoint)
                }
                return listOf(endpoint)
            }

            val mappings = springMappingResolver.resolve(resolvedMethod)
            if (mappings.isEmpty()) {
                LOG.info("after parse method:$methodKey")
                return emptyList()
            }

            val name = metadataResolver.resolveApiName(method)
            val folder = metadataResolver.resolveFolderName(method, psiClass)
            val description = metadataResolver.resolveMethodDoc(method)
            val classDesc = metadataResolver.resolveClassDoc(psiClass)

            val params = buildSpringParams(method)
            val body = buildSpringRequestBody(method)
            val responseBody = buildResponseBody(method)

            LOG.info("after parse method:$methodKey")

            val endpoints = mappings.map { m ->
                val contentType = m.consumes.firstOrNull()
                val headers = buildList {
                    if (!contentType.isNullOrBlank()) {
                        add(ApiHeader(name = "Content-Type", value = contentType))
                    }
                    for ((name, value) in m.headers) {
                        if (!name.equals("Content-Type", ignoreCase = true)) {
                            add(ApiHeader(name = name, value = value))
                        }
                    }
                }

                val rawPath = normalizePath(join(basePath, m.path))
                val pathVariablePatterns = PathVariablePattern.extractPathVariablesFromPath(rawPath)
                val normalizedPath = PathVariablePattern.normalizePath(rawPath)

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

                val mergedParams = mergePathParameters(params, pathParamsFromPath)

                ApiEndpoint(
                    name = name,
                    folder = folder,
                    description = description,
                    sourceClass = psiClass,
                    sourceMethod = method,
                    className = psiClass.qualifiedName ?: psiClass.name,
                    classDescription = classDesc,
                    metadata = HttpMetadata(
                        path = normalizedPath,
                        method = m.method,
                        parameters = mergedParams,
                        headers = headers,
                        contentType = contentType,
                        body = body,
                        responseBody = responseBody
                    )
                )
            }

            for (endpoint in endpoints) {
                engine.evaluate(RuleKeys.EXPORT_AFTER, method) { ctx ->
                    ctx.setExt("api", endpoint)
                }
            }

            return endpoints
        } finally {
            engine.evaluate(RuleKeys.API_METHOD_PARSE_AFTER, method)
        }
    }

    private suspend fun buildFromNativeFeign(
        basePath: String,
        psiClass: PsiClass,
        method: PsiMethod,
        requestLine: RequestLine
    ): ApiEndpoint {
        val headers = nativeParser.parseHeaders(method)
        val (finalHeaders, headerVars) = nativeParser.toHeaderParams(headers, method)
        val body = nativeParser.parseBodyTemplate(method)
        val rawPathTemplate = normalizePath(join(basePath, requestLine.path))
        val pathVariablePatterns = PathVariablePattern.extractPathVariablesFromPath(rawPathTemplate)
        val normalizedPathTemplate = PathVariablePattern.normalizePath(rawPathTemplate)
        val pathParams = nativeParser.toPathParams(normalizedPathTemplate, method)
        val reserved = (pathParams.map { it.name }.toSet() + headerVars)
        val queryParams = nativeParser.toQueryParams(method, reserved)

        val bodyParams = if (body != null) {
            val vars = nativeParser.extractTemplateVariables(body)
            vars.map { v ->
                ApiParameter(name = v, binding = ParameterBinding.Body)
            }
        } else emptyList()

        val expandedBody = buildNativeFeignBody(method, bodyParams)
        val responseBody = buildResponseBody(method)

        val name = metadataResolver.resolveApiName(method)
        val classDesc = metadataResolver.resolveClassDoc(psiClass)

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

        val mergedPathParams = mergePathParameters(pathParams, pathParamsFromPath)

        return ApiEndpoint(
            name = name,
            folder = null,
            sourceClass = psiClass,
            sourceMethod = method,
            className = psiClass.qualifiedName ?: psiClass.name,
            classDescription = classDesc,
            metadata = HttpMetadata(
                path = normalizedPathTemplate,
                method = requestLine.method,
                parameters = mergedPathParams + queryParams + bodyParams,
                headers = finalHeaders,
                contentType = finalHeaders.firstOrNull { it.name.equals("Content-Type", true) }?.value,
                body = expandedBody,
                responseBody = responseBody
            )
        )
    }

    private suspend fun buildSpringParams(method: PsiMethod): List<ApiParameter> {
        val result = ArrayList<ApiParameter>()
        for (p in method.parameterList.parameters) {
            if (metadataResolver.isParamIgnored(p)) continue
            val binding = springParamResolver.resolve(p)
                ?: ParameterBinding.Query

            if (binding == ParameterBinding.Ignored) continue

            if (binding == ParameterBinding.Body) continue

            val paramName = p.name ?: "param"
            LOG.info("before parse param:$paramName")

            val name = metadataResolver.resolveParamName(p, paramName)
            val required = metadataResolver.isParamRequired(p)
            val rawType = metadataResolver.resolveParamType(p, p.type.canonicalText)
            val type = ParameterType.fromTypeName(rawType)
            val doc = metadataResolver.resolveParamDoc(p)
            result.add(
                ApiParameter(
                    name = name,
                    type = type,
                    required = required,
                    binding = binding,
                    description = doc
                )
            )
            LOG.info("after parse param:$paramName")
        }
        return result
    }

    private suspend fun buildSpringRequestBody(method: PsiMethod): ObjectModel? {
        for (p in method.parameterList.parameters) {
            val binding = springParamResolver.resolve(p) ?: continue
            if (binding == ParameterBinding.Ignored) continue
            if (binding != ParameterBinding.Body) continue
            return expandBodyParam(p)
        }
        return null
    }

    private suspend fun buildNativeFeignBody(method: PsiMethod, bodyParams: List<ApiParameter>): ObjectModel? {
        if (bodyParams.isEmpty()) return null
        for (p in method.parameterList.parameters) {
            val psiClass = PsiTypesUtil.getPsiClass(p.type) ?: continue
            val qualifiedName = psiClass.qualifiedName ?: continue
            if (qualifiedName.startsWith("java.lang.") || qualifiedName == "java.lang.String") continue
            return expandBodyParam(p)
        }
        return null
    }

    private suspend fun expandBodyParam(parameter: PsiParameter): ObjectModel? {
        val psiClass = PsiTypesUtil.getPsiClass(parameter.type) ?: return null
        val qualifiedName = psiClass.qualifiedName ?: return null
        if (qualifiedName.startsWith("java.lang.") || qualifiedName == "java.lang.String") return null
        return runCatching {
            val helper = PsiClassHelper.getInstance(project!!)
            helper.buildObjectModel(psiClass, actionContext)
        }.getOrNull()
    }

    private suspend fun buildResponseBody(method: PsiMethod): ObjectModel? {
        val returnType = method.returnType ?: return null
        if (returnType == com.intellij.psi.PsiTypes.voidType()) return null
        return runCatching {
            val helper = PsiClassHelper.getInstance(project!!)
            helper.buildObjectModelFromType(
                psiType = returnType,
                contextElement = method,
                actionContext = actionContext
            )
        }.getOrNull()
    }

    private fun join(a: String, b: String): String {
        if (a.isBlank()) return b
        if (b.isBlank()) return a
        return a.trimEnd('/') + "/" + b.trimStart('/')
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank()) return "/"
        val p = if (path.startsWith("/")) path else "/$path"
        return p.replace(Regex("/+"), "/")
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

    companion object : IdeaLog
}