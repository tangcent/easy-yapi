package com.itangcent.easyapi.framework.custom

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.export.*
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.helper.DocMetadataResolver
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.core.util.text.PathVariablePattern
import com.itangcent.easyapi.framework.spi.FrameworkRegistry
import kotlinx.coroutines.withContext

/**
 * Exports API endpoints from classes recognized by the Custom framework.
 *
 * Unlike Spring MVC / JAX-RS / Feign / gRPC exporters, this exporter performs
 * **no hard-coded annotation detection**. Every extraction decision — which
 * classes are APIs, which methods are endpoints, each endpoint's HTTP method,
 * path, and parameter bindings — is delegated to a unified `custom.*` rule
 * surface declared in [CustomRuleKeys].
 *
 * ## Output
 *
 * Produces standard [ApiEndpoint] / [HttpMetadata], so every existing output
 * channel (Markdown, Postman, YApi, cURL, IntelliJ HTTP Client, Hoppscotch,
 * OpenAPI) consumes its output natively with **zero channel-side changes**.
 *
 * ## Lifecycle
 *
 * Mirrors the JAX-RS exporter's lifecycle: class-level
 * [RuleKeys.API_CLASS_PARSE_BEFORE] / [RuleKeys.API_CLASS_PARSE_AFTER]
 * wrapping the method loop, method-level
 * [RuleKeys.API_METHOD_PARSE_BEFORE] / [RuleKeys.API_METHOD_PARSE_AFTER]
 * wrapping each method (in `finally`), [RuleKeys.EXPORT_AFTER] firing per
 * endpoint with `ctx.setExt("api", endpoint)`. All hooks are wrapped in
 * [IdeDispatchers.Background] (event rules may run Groovy scripts).
 *
 * Additionally, framework-scoped `custom.*` lifecycle hooks
 * ([CustomRuleKeys.CUSTOM_CLASS_PARSE_BEFORE] / [CustomRuleKeys.CUSTOM_CLASS_PARSE_AFTER],
 * [CustomRuleKeys.CUSTOM_METHOD_PARSE_BEFORE] / [CustomRuleKeys.CUSTOM_METHOD_PARSE_AFTER],
 * [CustomRuleKeys.CUSTOM_EXPORT_AFTER]) fire **immediately after** their shared
 * counterparts — the shared hook fires first, then the custom-specific hook.
 * Because the rule evaluation context does not expose the framework name to
 * user-written rules, these custom hooks give users a framework-scoped surface
 * for side-effect rules that must only run during Custom-framework extraction.
 *
 * ## Threading
 *
 * All PSI access occurs under a read action (per AGENTS.md PSI threading
 * rules) — either via [read] or [IdeDispatchers.ReadAction].
 *
 * @see CustomApiRecognizer
 * @see com.itangcent.easyapi.framework.spi.FrameworkRegistry
 */
class CustomClassExporter(
    private val project: Project
) : ClassExporter {

    override val frameworkName: String = CustomApiRecognizer.FRAMEWORK_NAME

    override suspend fun isEnabled(): Boolean =
        FrameworkRegistry.getInstance(project).isEnabled(frameworkName)

    private val engine = RuleEngine.getInstance(project)
    private val recognizer = CustomApiRecognizer(engine)
    private val metadataResolver = DocMetadataResolver.getInstance(project)
    private val endpointBuilder = EndpointBuilder.getInstance(project)

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isApiClass(psiClass)) return emptyList()
        if (metadataResolver.isIgnored(psiClass)) return emptyList()

        val className = read {
            psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        }
        LOG.info("before parse class:$className")

        withContext(IdeDispatchers.Background) {
            engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, psiClass)
            engine.evaluate(CustomRuleKeys.CUSTOM_CLASS_PARSE_BEFORE, psiClass)
        }

        val resolvedType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethods = read { resolvedType.suitableMethods() }
        val endpoints = ArrayList<ApiEndpoint>()
        try {
            for (resolvedMethod in resolvedMethods) {
                if (read { metadataResolver.isIgnored(resolvedMethod.psiMethod) }) continue
                endpoints.addAll(exportMethod(psiClass, resolvedMethod))
            }
        } finally {
            withContext(IdeDispatchers.Background) {
                engine.evaluate(RuleKeys.API_CLASS_PARSE_AFTER, psiClass)
                engine.evaluate(CustomRuleKeys.CUSTOM_CLASS_PARSE_AFTER, psiClass)
            }
        }
        LOG.info("after parse class:$className")
        return endpoints
    }

    private suspend fun exportMethod(
        psiClass: PsiClass,
        resolvedMethod: ResolvedMethod
    ): List<ApiEndpoint> {
        val method = resolvedMethod.psiMethod
        val methodKey = read {
            "${psiClass.qualifiedName ?: psiClass.name}#${method.name}"
        }
        LOG.info("before parse method:$methodKey")

        withContext(IdeDispatchers.Background) {
            engine.evaluate(RuleKeys.API_METHOD_PARSE_BEFORE, resolvedMethod)
            engine.evaluate(CustomRuleKeys.CUSTOM_METHOD_PARSE_BEFORE, resolvedMethod)
        }

        val classQualifiedName = read { psiClass.qualifiedName ?: psiClass.name }

        try {
            // Opt-in via custom.method.is.api; skip non-endpoint methods.
            val isApi = runCatching {
                engine.evaluate(CustomRuleKeys.CUSTOM_METHOD_IS_API, method)
            }.onFailure {
                LOG.warn("custom.method.is.api rule threw for $methodKey", it)
            }.getOrDefault(false)
            if (!isApi) {
                LOG.info("after parse method:$methodKey")
                return emptyList()
            }

            val endpoints: List<ApiEndpoint>
            withContext(IdeDispatchers.ReadAction) {
                val basePath = resolveClassPath(psiClass)
                val methodPath = resolveMethodPath(method)
                val path = composePath(basePath, methodPath, psiClass, method)

                val httpMethod = resolveHttpMethod(method, methodKey)

                val name = metadataResolver.resolveApiName(method)
                val description = metadataResolver.resolveMethodDoc(method)
                val classFolder = metadataResolver.resolveFolder(psiClass)
                val methodFolderName = metadataResolver.resolveFolderName(method)
                val folder = methodFolderName.takeIf { it.isNotBlank() } ?: classFolder.name

                val bindings = resolveParameterBindings(resolvedMethod)
                val params = buildParameters(bindings)
                val paramHeaders = extractParamHeaders(bindings)
                val body = buildRequestBody(bindings)
                val responseBody = endpointBuilder.buildResponseBody(method, resolvedMethod.returnType)
                val responseTypeName = method.returnType?.canonicalText

                val additionalHeaders = metadataResolver.resolveAdditionalHeaders(method)
                val additionalParams = metadataResolver.resolveAdditionalParams(method)
                val additionalResponseHeaders = metadataResolver.resolveAdditionalResponseHeaders(method)
                val contentTypeOverride = engine.evaluate(RuleKeys.METHOD_CONTENT_TYPE, method)
                val contentType = contentTypeOverride?.takeIf { it.isNotBlank() }
                    ?: inferContentType(body != null, params)

                val headers = endpointBuilder.buildHeaders(
                    contentType = contentType,
                    paramHeaders = paramHeaders,
                    additionalHeaders = additionalHeaders,
                    additionalResponseHeaders = additionalResponseHeaders
                )

                val pathParamsFromPath = PathVariablePattern.extractPathVariablesFromPath(path).map { pattern ->
                    ApiParameter(
                        name = pattern.name,
                        type = ParameterType.TEXT,
                        required = true,
                        binding = ParameterBinding.Path,
                        defaultValue = pattern.defaultValue,
                        enumValues = pattern.possibleValues
                    )
                }
                val normalizedPath = PathVariablePattern.normalizePath(path)
                val mergedParams = endpointBuilder.mergePathParameters(
                    params + additionalParams, pathParamsFromPath
                )

                LOG.info("after parse method:$methodKey")

                endpoints = listOf(
                    ApiEndpoint(
                        name = name,
                        folder = folder,
                        description = description,
                        sourceClass = psiClass,
                        sourceMethod = method,
                        className = classQualifiedName,
                        classDescription = classFolder.description,
                        metadata = httpMetadata(
                            path = normalizedPath,
                            method = httpMethod,
                            parameters = mergedParams,
                            headers = headers,
                            contentType = contentType,
                            body = body,
                            responseBody = responseBody,
                            responseType = responseTypeName
                        )
                    )
                )
            }

            withContext(IdeDispatchers.Background) {
                for (endpoint in endpoints) {
                    engine.evaluate(RuleKeys.EXPORT_AFTER, resolvedMethod) { ctx ->
                        ctx.setExt("api", endpoint)
                    }
                    engine.evaluate(CustomRuleKeys.CUSTOM_EXPORT_AFTER, resolvedMethod) { ctx ->
                        ctx.setExt("api", endpoint)
                    }
                }
            }

            return endpoints
        } finally {
            withContext(IdeDispatchers.Background) {
                engine.evaluate(RuleKeys.API_METHOD_PARSE_AFTER, resolvedMethod)
                engine.evaluate(CustomRuleKeys.CUSTOM_METHOD_PARSE_AFTER, resolvedMethod)
            }
        }
    }

    // ── Path resolution ──────────────────────────────────────────

    /** `custom.path` on PsiClass returns the base path. */
    private suspend fun resolveClassPath(psiClass: PsiClass): String =
        engine.evaluate(CustomRuleKeys.CUSTOM_PATH, psiClass)?.trim() ?: ""

    /** `custom.path` on PsiMethod returns the method path. */
    private suspend fun resolveMethodPath(method: PsiMethod): String =
        engine.evaluate(CustomRuleKeys.CUSTOM_PATH, method)?.trim() ?: ""

    /**
     * Compose base + method with single `/` separator, collapsing duplicate
     * slashes. Honors `class.prefix.path` and `endpoint.prefix.path` as
     * additional outermost prefixes.
     */
    private suspend fun composePath(
        basePath: String,
        methodPath: String,
        psiClass: PsiClass,
        method: PsiMethod
    ): String {
        val classPrefix = engine.evaluate(RuleKeys.CLASS_PREFIX_PATH, psiClass)?.trim() ?: ""
        val endpointPrefix = engine.evaluate(RuleKeys.ENDPOINT_PREFIX_PATH, method)?.trim() ?: ""
        val joined = joinAndNormalize(
            joinAndNormalize(joinAndNormalize(classPrefix, basePath), methodPath),
            endpointPrefix
        )
        return if (joined.isBlank()) "/" else joined
    }

    private fun joinAndNormalize(a: String, b: String): String {
        if (a.isBlank()) return b
        if (b.isBlank()) return a
        val merged = a.trimEnd('/') + "/" + b.trimStart('/')
        return if (merged.startsWith("/")) merged.replace(Regex("/+"), "/")
        else "/" + merged.replace(Regex("/+"), "/")
    }

    // ── HTTP method resolution ───────────────────────────────────

    /**
     * `custom.http.method` → `method.default.http.method` → `POST`.
     * Logs an info note when falling back to the default POST.
     */
    private suspend fun resolveHttpMethod(method: PsiMethod, methodKey: String): HttpMethod {
        val custom = runCatching {
            engine.evaluate(CustomRuleKeys.CUSTOM_HTTP_METHOD, method)
        }.onFailure { LOG.warn("custom.http.method rule threw for $methodKey", it) }.getOrNull()

        if (!custom.isNullOrBlank()) {
            HttpMethod.fromSpring(custom)?.let { return it }
            LOG.info("custom.http.method returned unrecognized value '$custom' for $methodKey; falling back")
        }

        val defaultByRule = metadataResolver.resolveDefaultHttpMethod(method)
        if (!defaultByRule.isNullOrBlank()) {
            HttpMethod.fromSpring(defaultByRule)?.let { return it }
        }

        LOG.info("No HTTP method resolved for $methodKey; defaulting to POST")
        return HttpMethod.POST
    }

    // ── Parameter binding ────────────────────────────────────────

    private data class ResolvedParamBinding(
        val param: com.itangcent.easyapi.core.psi.type.ResolvedParam,
        val binding: ParameterBinding,
        val nameOverride: String? = null
    )

    /**
     * Boolean classifiers take precedence (most-specific wins); `param.http.type`
     * is the coarser fallback. `param.ignore` skips the parameter entirely.
     * Default binding is [ParameterBinding.Query].
     */
    private suspend fun resolveParameterBindings(
        resolvedMethod: ResolvedMethod
    ): List<ResolvedParamBinding> = resolvedMethod.params.mapIndexedNotNull { _, param ->
        val p = param.psiParameter
        if (metadataResolver.isParamIgnored(p)) return@mapIndexedNotNull null

        val binding = resolveSingleBinding(p) ?: ParameterBinding.Query
        if (binding == ParameterBinding.Ignored) return@mapIndexedNotNull null
        ResolvedParamBinding(param, binding)
    }

    private suspend fun resolveSingleBinding(p: PsiParameter): ParameterBinding? {
        // Boolean classifiers take precedence (most-specific wins).
        if (engine.evaluate(CustomRuleKeys.CUSTOM_PARAM_AS_JSON_BODY, p)) return ParameterBinding.Body
        if (engine.evaluate(CustomRuleKeys.CUSTOM_PARAM_AS_FORM_BODY, p)) return ParameterBinding.Form
        if (engine.evaluate(CustomRuleKeys.CUSTOM_PARAM_AS_PATH_VAR, p)) return ParameterBinding.Path
        if (engine.evaluate(CustomRuleKeys.CUSTOM_PARAM_AS_COOKIE, p)) return ParameterBinding.Cookie

        // param.http.type as coarser classifier.
        val httpType = engine.evaluate(RuleKeys.PARAM_HTTP_TYPE, p)?.trim()?.lowercase()
        return when (httpType) {
            "body" -> ParameterBinding.Body
            "form" -> ParameterBinding.Form
            "path" -> ParameterBinding.Path
            "header" -> ParameterBinding.Header
            "cookie" -> ParameterBinding.Cookie
            "query" -> ParameterBinding.Query
            else -> {
                // Default is Query; log at info (debug is filtered out of idea.log by default).
                LOG.info("param ${p.name} matched no classifier; defaulting to query")
                null
            }
        }
    }

    private suspend fun buildParameters(
        bindings: List<ResolvedParamBinding>
    ): List<ApiParameter> {
        val result = ArrayList<ApiParameter>()
        for ((param, binding) in bindings) {
            if (binding == ParameterBinding.Body) continue  // body is built separately
            val p = param.psiParameter
            val paramName = param.name
            LOG.info("before parse param:$paramName")

            withContext(IdeDispatchers.Background) {
                engine.evaluate(RuleKeys.API_PARAM_PARSE_BEFORE, p)
            }
            try {
                val name = resolveParamName(p, paramName, binding)
                val required = metadataResolver.isParamRequired(p)
                val rawType = metadataResolver.resolveParamType(p, p.type.canonicalText)
                val type = ParameterType.fromTypeName(rawType)
                val doc = metadataResolver.resolveParamDoc(p)
                val defaultValue = metadataResolver.resolveParamDefaultValue(p)
                val demo = metadataResolver.resolveParamDemo(p)
                val mock = metadataResolver.resolveParamMock(p)
                result.add(
                    ApiParameter(
                        name = name,
                        type = type,
                        required = required,
                        binding = binding,
                        defaultValue = defaultValue,
                        description = doc,
                        example = demo ?: mock
                    )
                )
            } finally {
                withContext(IdeDispatchers.Background) {
                    engine.evaluate(RuleKeys.API_PARAM_PARSE_AFTER, p)
                }
            }
            LOG.info("after parse param:$paramName")
        }
        return result
    }

    /**
     * Per-binding name override via the corresponding `custom.param.*` key,
     * falling back to the shared `param.name` rule, then to the PSI parameter name.
     */
    private suspend fun resolveParamName(
        p: PsiParameter,
        defaultName: String,
        binding: ParameterBinding
    ): String {
        val customName = when (binding) {
            ParameterBinding.Path -> engine.evaluate(CustomRuleKeys.CUSTOM_PARAM_PATH_VAR, p)
            ParameterBinding.Header -> engine.evaluate(CustomRuleKeys.CUSTOM_PARAM_HEADER, p)
            ParameterBinding.Cookie -> engine.evaluate(CustomRuleKeys.CUSTOM_PARAM_COOKIE, p)
            else -> engine.evaluate(CustomRuleKeys.CUSTOM_PARAM_NAME, p)
        }?.takeIf { it.isNotBlank() }
        if (customName != null) return customName
        return metadataResolver.resolveParamName(p, defaultName)
    }

    private suspend fun extractParamHeaders(
        bindings: List<ResolvedParamBinding>
    ): List<ApiHeader> {
        val headers = ArrayList<ApiHeader>()
        for ((param, binding) in bindings) {
            if (binding != ParameterBinding.Header) continue
            val p = param.psiParameter
            val name = resolveParamName(p, param.name, binding)
            val defaultValue = metadataResolver.resolveParamDefaultValue(p)
            val example = metadataResolver.resolveParamDemo(p)
            headers.add(
                ApiHeader(
                    name = name,
                    value = defaultValue ?: example,
                    // Honour `param.required` for header-bound parameters instead of
                    // always emitting false.
                    required = metadataResolver.isParamRequired(p)
                )
            )
        }
        return headers
    }

    /**
     * Serialize the json-body parameter's type into the request body
     * (ObjectModel) via [EndpointBuilder.expandBodyParam].
     */
    private suspend fun buildRequestBody(
        bindings: List<ResolvedParamBinding>
    ): ObjectModel? {
        for ((param, binding) in bindings) {
            if (binding != ParameterBinding.Body) continue
            return endpointBuilder.expandBodyParam(param.type)
        }
        return null
    }

    private fun inferContentType(hasBody: Boolean, params: List<ApiParameter>): String? {
        if (hasBody) return "application/json"
        val hasFormParams = params.any { it.binding == ParameterBinding.Form }
        if (hasFormParams) {
            val hasFileParams = params.any {
                it.binding == ParameterBinding.Form && it.type == ParameterType.FILE
            }
            return if (hasFileParams) "multipart/form-data" else "application/x-www-form-urlencoded"
        }
        return null
    }

    companion object : IdeaLog
}
