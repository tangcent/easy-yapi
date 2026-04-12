package com.itangcent.easyapi.exporter.jaxrs

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.core.context.project
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.PsiClassHelper
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.psi.type.searchAnnotation
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Exports API endpoints from JAX-RS resource classes.
 *
 * This exporter processes classes annotated with @Path and extracts:
 * - HTTP methods from @GET, @POST, @PUT, @DELETE, etc.
 * - Paths from @Path annotations
 * - Parameters from @PathParam, @QueryParam, @FormParam, etc.
 * - Content types from @Consumes and @Produces
 *
 * ## Supported JAX-RS Annotations
 * - `@Path` - Resource path
 * - `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS` - HTTP methods
 * - `@PathParam` - Path parameters
 * - `@QueryParam` - Query parameters
 * - `@FormParam` - Form parameters
 * - `@HeaderParam` - Header parameters
 * - `@CookieParam` - Cookie parameters
 * - `@MatrixParam` - Matrix parameters
 * - `@Consumes` - Content-Type
 * - `@Produces` - Accept
 *
 * @see JaxRsResourceRecognizer for resource class detection
 * @see JaxRsPathResolver for path resolution
 * @see JaxRsParameterResolver for parameter resolution
 */
class JaxRsClassExporter(
    private val actionContext: ActionContext,
    jaxrsEnable: Boolean = true
) : ClassExporter {
    private val annotationHelper = UnifiedAnnotationHelper()
    private val engine = RuleEngine.getInstance(actionContext)
    private val recognizer = JaxRsResourceRecognizer(engine, jaxrsEnable)
    private val methodResolver = JaxRsHttpMethodResolver(annotationHelper)
    private val pathResolver = JaxRsPathResolver(annotationHelper)
    private val parameterResolver = JaxRsParameterResolver(annotationHelper)
    private val contentTypeResolver = JaxRsContentTypeResolver(annotationHelper)
    private val docHelper = actionContext.instance(DocHelper::class)
    private val metadataResolver = ApiMetadataResolver(engine, docHelper)
    private val project: Project = actionContext.project()

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isResource(psiClass)) return emptyList()

        val className = psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        LOG.info("before parse class:$className")

        engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, psiClass)

        val resolvedType = ResolvedType.ClassType(psiClass, emptyList())
        val endpoints = ArrayList<ApiEndpoint>()
        try {
            for (resolvedMethod in resolvedType.methods()) {
                val httpMethodAnn = resolvedMethod.searchAnnotation(JAXRS_HTTP_METHOD_ANNOTATIONS)
                    ?: continue
                val annotatedMethod = (httpMethodAnn.owner as? PsiMethod) ?: resolvedMethod.psiMethod
                endpoints.addAll(exportMethod(psiClass, resolvedMethod.psiMethod, annotatedMethod))
            }
        } finally {
            engine.evaluate(RuleKeys.API_CLASS_PARSE_AFTER, psiClass)
        }
        LOG.info("after parse class:$className")
        return endpoints
    }

    private suspend fun exportMethod(
        psiClass: PsiClass,
        method: PsiMethod,
        annotatedMethod: PsiMethod
    ): List<ApiEndpoint> {
        val methodKey = "${psiClass.qualifiedName ?: psiClass.name}#${method.name}"
        LOG.info("before parse method:$methodKey")

        engine.evaluate(RuleKeys.API_METHOD_PARSE_BEFORE, method)

        try {
            val httpMethod = methodResolver.resolve(annotatedMethod)
            if (httpMethod == null) {
                LOG.info("after parse method:$methodKey")
                return emptyList()
            }
            val path = pathResolver.resolve(psiClass, annotatedMethod)
            val types = contentTypeResolver.resolve(psiClass, annotatedMethod)

            val name = metadataResolver.resolveApiName(method)
            val folder = metadataResolver.resolveFolderName(method, psiClass)
            val description = metadataResolver.resolveMethodDoc(method)
            val classDesc = metadataResolver.resolveClassDoc(psiClass)

            val params = buildParameters(method)
            val paramHeaders = extractParamHeaders(method)
            val contentType = types.consumes.firstOrNull()
            val headers = buildHeaders(contentType, paramHeaders)
            val body = buildRequestBody(method)
            val responseBody = buildResponseBody(method)

            LOG.info("after parse method:$methodKey")

            val endpoints = listOf(
                ApiEndpoint(
                    name = name,
                    folder = folder,
                    description = description,
                    sourceClass = psiClass,
                    sourceMethod = method,
                    className = psiClass.qualifiedName ?: psiClass.name,
                    classDescription = classDesc,
                    metadata = HttpMetadata(
                        path = path,
                        method = httpMethod,
                        parameters = params,
                        headers = headers,
                        contentType = contentType,
                        body = body,
                        responseBody = responseBody,
                        responseType = method.returnType?.canonicalText
                    )
                )
            )

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

    private suspend fun extractParamHeaders(method: PsiMethod): List<ApiHeader> {
        val headers = ArrayList<ApiHeader>()
        read {
            for (p in method.parameterList.parameters) {
                val resolved = parameterResolver.resolve(p)
                for (param in resolved) {
                    if (param.binding == ParameterBinding.Header) {
                        headers.add(ApiHeader(name = param.name, value = param.defaultValue ?: param.example))
                    }
                }
            }
        }
        return headers
    }

    private fun buildHeaders(contentType: String?, paramHeaders: List<ApiHeader>): List<ApiHeader> {
        val seen = mutableSetOf<String>()
        val result = ArrayList<ApiHeader>()

        if (!contentType.isNullOrBlank()) {
            result.add(ApiHeader(name = "Content-Type", value = contentType))
            seen.add("content-type")
        }

        for (h in paramHeaders) {
            val key = h.name.lowercase()
            if (key !in seen) {
                result.add(h)
                seen.add(key)
            }
        }

        return result
    }

    private suspend fun buildRequestBody(method: PsiMethod): ObjectModel? = read {
        for (p in method.parameterList.parameters) {
            val resolved = parameterResolver.resolve(p)
            if (resolved.any { it.binding == ParameterBinding.Body }) {
                return@read expandBodyParam(p)
            }
        }
        return@read null
    }

    private suspend fun expandBodyParam(parameter: PsiParameter): ObjectModel? {
        val psiClass = PsiTypesUtil.getPsiClass(parameter.type) ?: return null
        val qualifiedName = psiClass.qualifiedName ?: return null
        if (qualifiedName.startsWith("java.lang.") || qualifiedName == "java.lang.String") return null
        return runCatching {
            val helper = PsiClassHelper.getInstance(project)
            helper.buildObjectModel(psiClass, actionContext)
        }.getOrNull()
    }

    private suspend fun buildResponseBody(method: PsiMethod): ObjectModel? {
        val returnType = read { method.returnType } ?: return null
        if (returnType == com.intellij.psi.PsiTypes.voidType()) return null
        return runCatching {
            val helper = PsiClassHelper.getInstance(project)
            helper.buildObjectModelFromType(
                psiType = returnType,
                contextElement = method,
                actionContext = actionContext
            )
        }.getOrNull()
    }

    private suspend fun buildParameters(method: PsiMethod): List<ApiParameter> {
        val result = ArrayList<ApiParameter>()
        read {
            for (p in method.parameterList.parameters) {
                if (metadataResolver.isParamIgnored(p)) continue

                val paramName = p.name ?: "param"
                LOG.info("before parse param:$paramName")

                val resolved = parameterResolver.resolve(p)
                result.addAll(resolved.map { it.copy(required = metadataResolver.isParamRequired(p)) })

                LOG.info("after parse param:$paramName")
            }
        }
        return result
    }

    companion object : IdeaLog {
        val JAXRS_HTTP_METHOD_ANNOTATIONS = setOf(
            "javax.ws.rs.GET", "javax.ws.rs.POST", "javax.ws.rs.PUT",
            "javax.ws.rs.DELETE", "javax.ws.rs.PATCH", "javax.ws.rs.HEAD",
            "javax.ws.rs.OPTIONS",
            "jakarta.ws.rs.GET", "jakarta.ws.rs.POST", "jakarta.ws.rs.PUT",
            "jakarta.ws.rs.DELETE", "jakarta.ws.rs.PATCH", "jakarta.ws.rs.HEAD",
            "jakarta.ws.rs.OPTIONS"
        )
    }
}

