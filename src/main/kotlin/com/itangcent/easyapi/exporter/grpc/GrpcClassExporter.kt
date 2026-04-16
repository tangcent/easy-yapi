package com.itangcent.easyapi.exporter.grpc

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.exporter.ClassExporter
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.model.ObjectModel
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
    private val docHelper: DocHelper = StandardDocHelper.getInstance(project)
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

        val classDescription = resolveClassDescription(psiClass)
        val folder = classDescription?.lines()?.firstOrNull { it.isNotBlank() }
            ?: read { psiClass.name }
            ?: "Unknown"

        val rpcMethods = methodResolver.resolveRpcMethods(psiClass)
        val endpoints: List<ApiEndpoint>
        try {
            endpoints = rpcMethods.mapNotNull { methodInfo ->
                try {
                    val requestBody = buildRequestBody(methodInfo.requestType)
                    val responseBody = buildResponseBody(methodInfo.responseType)
                    val responseTypeName = read { methodInfo.responseType?.qualifiedName }

                    ApiEndpoint(
                        name = methodInfo.description
                            ?: methodInfo.methodName,
                        folder = folder,
                        description = methodInfo.description,
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

    private suspend fun resolveClassDescription(psiClass: PsiClass): String? {
        return try {
            docHelper.getAttrOfDocComment(psiClass)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun buildRequestBody(requestType: PsiClass?): ObjectModel? {
        if (requestType == null) return null
        return try {
            read { typeParser.parseMessageType(requestType) }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun buildResponseBody(responseType: PsiClass?): ObjectModel? {
        if (responseType == null) return null
        return try {
            read { typeParser.parseMessageType(responseType) }
        } catch (_: Exception) {
            null
        }
    }

    companion object : IdeaLog
}
