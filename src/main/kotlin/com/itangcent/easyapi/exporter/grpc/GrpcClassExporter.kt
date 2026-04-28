package com.itangcent.easyapi.exporter.grpc

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.EndpointBuilder
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.helper.ApiMetadataResolver
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.TypeResolver
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine

/**
 * Exports API endpoints from gRPC service implementation classes.
 *
 * Parses gRPC service classes and extracts complete API endpoint information including:
 * - gRPC service path (/<package>.<ServiceName>/<MethodName>)
 * - Streaming type (unary, server-streaming, client-streaming, bidirectional)
 * - Request/response protobuf message types
 *
 * Uses [GrpcServiceRecognizer] to identify gRPC classes, [GrpcMethodResolver] to
 * discover RPC methods, and [GrpcTypeParser] to parse protobuf message types.
 *
 * @param project The IntelliJ project
 * @see ClassExporter for the interface
 * @see GrpcServiceRecognizer for service detection
 * @see GrpcMethodResolver for method resolution
 * @see GrpcTypeParser for protobuf type parsing
 */
class GrpcClassExporter(
    private val project: Project
) : ClassExporter {

    override val frameworkName: String = "gRPC"

    private val engine = RuleEngine.getInstance(project)
    private val docHelper: DocHelper = UnifiedDocHelper.getInstance(project)
    private val metadataResolver = ApiMetadataResolver(engine, docHelper)
    private val endpointBuilder = EndpointBuilder.getInstance(project)
    private val recognizer = GrpcServiceRecognizer(engine)
    private val methodResolver = GrpcMethodResolver.getInstance(project)
    private val typeParser = GrpcTypeParser()

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isGrpcService(psiClass)) return emptyList()

        val className = read {
            psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        }
        LOG.info("before parse gRPC class:$className")

        engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, psiClass)

        val classDescription = metadataResolver.resolveClassDoc(psiClass)
        val folder = metadataResolver.resolveFolderName(null, psiClass)
            ?: classDescription?.lines()?.firstOrNull { it.isNotBlank() }
            ?: read { psiClass.name }
            ?: "Unknown"

        val rpcMethods = methodResolver.resolveRpcMethods(psiClass)
        val endpoints: List<ApiEndpoint>
        try {
            endpoints = rpcMethods.mapNotNull { methodInfo ->
                try {
                    val requestBody = buildRequestBody(methodInfo.requestType)
                    val responseBody = buildResponseBody(methodInfo.responseType, methodInfo.psiMethod)
                    val responseTypeName = read { methodInfo.responseType?.qualifiedName }

                    val apiName = metadataResolver.resolveApiName(methodInfo.psiMethod)
                    val methodDescription = metadataResolver.resolveMethodDoc(methodInfo.psiMethod)

                    ApiEndpoint(
                        name = apiName
                            ?: methodInfo.description
                            ?: methodInfo.methodName,
                        folder = folder,
                        description = methodDescription
                            ?: methodInfo.description,
                        tags = listOf("gRPC"),
                        sourceClass = psiClass,
                        sourceMethod = methodInfo.psiMethod,
                        className = className,
                        classDescription = classDescription,
                        metadata = GrpcMetadata(
                            path = methodInfo.fullPath,
                            serviceName = methodInfo.serviceName,
                            methodName = methodInfo.methodName,
                            packageName = methodInfo.packageName,
                            streamingType = methodInfo.streamingType,
                            body = requestBody,
                            responseBody = responseBody,
                            responseType = responseTypeName
                        )
                    )
                } catch (e: Exception) {
                    LOG.warn("Failed to export gRPC method: ${methodInfo.methodName}", e)
                    null
                }
            }

            for (endpoint in endpoints) {
                val method = endpoint.sourceMethod ?: continue
                engine.evaluate(RuleKeys.EXPORT_AFTER, method) { ctx ->
                    ctx.setExt("api", endpoint)
                }
            }
        } finally {
            engine.evaluate(RuleKeys.API_CLASS_PARSE_AFTER, psiClass)
        }

        LOG.info("after parse gRPC class:$className, found ${endpoints.size} endpoints")
        return endpoints
    }

    private suspend fun buildRequestBody(requestType: PsiClass?): ObjectModel? {
        if (requestType == null) return null
        return try {
            read { typeParser.parseMessageType(requestType) }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun buildResponseBody(responseType: PsiClass?, psiMethod: PsiMethod? = null): ObjectModel? {
        if (psiMethod != null) {
            val grpcModelBuilder = EndpointBuilder.ResponseModelBuilder { psiClass ->
                try {
                    read { typeParser.parseMessageType(psiClass) }
                } catch (_: Exception) {
                    null
                }
            }
            val resolvedReturnType = read { TypeResolver.resolve(psiMethod.returnType ?: return@read null) }
                ?: return null
            return endpointBuilder.buildResponseBody(
                method = psiMethod,
                resolvedReturnType = resolvedReturnType,
                modelBuilder = grpcModelBuilder
            )
        }

        if (responseType == null) return null
        return try {
            read { typeParser.parseMessageType(responseType) }
        } catch (_: Exception) {
            null
        }
    }

    companion object : IdeaLog
}
