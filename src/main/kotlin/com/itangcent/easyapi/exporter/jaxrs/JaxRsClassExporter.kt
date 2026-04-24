package com.itangcent.easyapi.exporter.jaxrs

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.EndpointBuilder
import com.itangcent.easyapi.exporter.model.*
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.GenericContext
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.psi.type.TypeResolver
import com.itangcent.easyapi.psi.type.searchAnnotation
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import kotlinx.coroutines.withContext

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
    private val project: Project,
    jaxrsEnable: Boolean = true
) : ClassExporter {

    override val frameworkName: String = "JAX-RS"

    private val annotationHelper = UnifiedAnnotationHelper()
    private val engine = RuleEngine.getInstance(project)
    private val recognizer = JaxRsResourceRecognizer(engine, jaxrsEnable)
    private val methodResolver = JaxRsHttpMethodResolver(annotationHelper)
    private val pathResolver = JaxRsPathResolver(annotationHelper)
    private val parameterResolver = JaxRsParameterResolver(annotationHelper)
    private val contentTypeResolver = JaxRsContentTypeResolver(annotationHelper)
    private val docHelper = UnifiedDocHelper.getInstance(project)
    private val metadataResolver = ApiMetadataResolver(engine, docHelper)
    private val endpointBuilder = EndpointBuilder.getInstance(project)

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isResource(psiClass)) return emptyList()

        val className = read {
            psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        }
        LOG.info("before parse class:$className")

        withContext(IdeDispatchers.Background) {
            engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, psiClass)
        }

        val resolvedType = ResolvedType.ClassType(psiClass, emptyList())
        val resolvedMethods = read { resolvedType.methods() }
        val endpoints = ArrayList<ApiEndpoint>()
        try {
            for (resolvedMethod in resolvedMethods) {
                val httpMethodAnn = read { resolvedMethod.searchAnnotation(JAXRS_HTTP_METHOD_ANNOTATIONS) }
                    ?: continue
                val annotatedMethod = (httpMethodAnn.owner as? PsiMethod) ?: resolvedMethod.psiMethod
                endpoints.addAll(exportMethod(psiClass, resolvedMethod.psiMethod, annotatedMethod))
            }
        } finally {
            withContext(IdeDispatchers.Background) {
                engine.evaluate(RuleKeys.API_CLASS_PARSE_AFTER, psiClass)
            }
        }
        LOG.info("after parse class:$className")
        return endpoints
    }

    private suspend fun exportMethod(
        psiClass: PsiClass,
        method: PsiMethod,
        annotatedMethod: PsiMethod
    ): List<ApiEndpoint> {
        val methodKey = read {
            "${psiClass.qualifiedName ?: psiClass.name}#${method.name}"
        }
        LOG.info("before parse method:$methodKey")

        withContext(IdeDispatchers.Background) {
            engine.evaluate(RuleKeys.API_METHOD_PARSE_BEFORE, method)
        }

        val classQualifiedName = read { psiClass.qualifiedName ?: psiClass.name }

        try {
            var httpMethod = methodResolver.resolve(annotatedMethod)
            if (httpMethod == null) {
                // Try default HTTP method from rule
                val defaultHttpMethod = metadataResolver.resolveDefaultHttpMethod(method)
                if (!defaultHttpMethod.isNullOrBlank()) {
                    httpMethod = HttpMethod.fromSpring(defaultHttpMethod)
                }
            }
            if (httpMethod == null) {
                LOG.info("after parse method:$methodKey")
                return emptyList()
            }
            val endpoints: List<ApiEndpoint>

            withContext(IdeDispatchers.ReadAction) {
                val path = pathResolver.resolve(psiClass, annotatedMethod)
                val types = contentTypeResolver.resolve(psiClass, annotatedMethod)

                val name = metadataResolver.resolveApiName(method)
                val folder = metadataResolver.resolveFolderName(method, psiClass)
                val description = metadataResolver.resolveMethodDoc(method)
                val classDesc = metadataResolver.resolveClassDoc(psiClass)

                val params = buildParameters(method)
                val additionalParams = metadataResolver.resolveAdditionalParams(method)
                val paramHeaders = extractParamHeaders(method)
                val additionalHeaders = metadataResolver.resolveAdditionalHeaders(method)
                val additionalResponseHeaders = metadataResolver.resolveAdditionalResponseHeaders(method)
                val contentTypeOverride = engine.evaluate(RuleKeys.METHOD_CONTENT_TYPE, method)
                val contentType = if (!contentTypeOverride.isNullOrBlank()) {
                    contentTypeOverride
                } else {
                    types.consumes.firstOrNull()
                }
                val headers = endpointBuilder.buildHeaders(contentType, paramHeaders, additionalHeaders, additionalResponseHeaders)
                val genericContext = GenericContext(TypeResolver.resolveGenericParams(psiClass, emptyList()))
                val body = buildRequestBody(method, genericContext)
                val responseBody = endpointBuilder.buildResponseBody(method, genericContext)
                val responseTypeName = read { method.returnType?.canonicalText }

                LOG.info("after parse method:$methodKey")

                endpoints = listOf(
                    ApiEndpoint(
                        name = name,
                        folder = folder,
                        description = description,
                        sourceClass = psiClass,
                        sourceMethod = method,
                        className = classQualifiedName,
                        classDescription = classDesc,
                        metadata = httpMetadata(
                            path = path,
                            method = httpMethod,
                            parameters = params + additionalParams,
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



    private suspend fun buildRequestBody(method: PsiMethod, genericContext: GenericContext = GenericContext.EMPTY): ObjectModel? = read {
        for (p in method.parameterList.parameters) {
            val resolved = parameterResolver.resolve(p)
            if (resolved.any { it.binding == ParameterBinding.Body }) {
                return@read endpointBuilder.expandBodyParam(p, genericContext)
            }
        }
        return@read null
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

