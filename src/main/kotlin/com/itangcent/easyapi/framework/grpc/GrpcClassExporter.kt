package com.itangcent.easyapi.framework.grpc

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.export.ClassExporter
import com.itangcent.easyapi.core.export.EndpointBuilder
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.aspect.ApiParseAspects
import com.itangcent.easyapi.core.export.aspect.EndpointParseContext
import com.itangcent.easyapi.core.grpc.GrpcMethodResolver
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.helper.DocMetadataResolver
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.core.psi.type.TypeResolver
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.framework.spi.FrameworkRegistry

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

    override suspend fun isEnabled(): Boolean =
        FrameworkRegistry.getInstance(project).isEnabled("gRPC")

    private val engine = RuleEngine.getInstance(project)
    private val metadataResolver = DocMetadataResolver.getInstance(project)
    private val endpointBuilder = EndpointBuilder.getInstance(project)
    private val recognizer = GrpcServiceRecognizer(engine)
    private val methodResolver = GrpcMethodResolver.getInstance(project)
    private val typeParser = GrpcTypeParser(metadataResolver)
    private val parseAspects get() = ApiParseAspects.getInstance(project)

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isGrpcService(psiClass)) return emptyList()

        val className = read {
            psiClass.qualifiedName ?: psiClass.name ?: "Unknown"
        }
        LOG.info("before parse gRPC class:$className")

        return parseAspects.aroundClass(frameworkName, psiClass) {
            val classFolder = metadataResolver.resolveFolder(psiClass)

            val rpcMethods = methodResolver.resolveRpcMethods(psiClass)
            val endpoints: List<ApiEndpoint> = rpcMethods.mapNotNull { methodInfo ->
                try {
                    val requestBody = buildRequestBody(methodInfo.requestType)
                    val responseBody = buildResponseBody(methodInfo.responseType, methodInfo.psiMethod)
                    val responseTypeName = read { methodInfo.responseType?.qualifiedName }

                    val apiName = metadataResolver.resolveApiName(methodInfo.psiMethod)
                    val methodDescription = metadataResolver.resolveMethodDoc(methodInfo.psiMethod)
                    val methodFolderName = metadataResolver.resolveFolderName(methodInfo.psiMethod)
                    val folder = methodFolderName.takeIf { it.isNotBlank() } ?: classFolder.name

                    ApiEndpoint(
                        name = apiName
                            ?: methodInfo.methodName,
                        folder = folder,
                        description = methodDescription,
                        sourceClass = psiClass,
                        sourceMethod = methodInfo.psiMethod,
                        className = className,
                        classDescription = classFolder.description,
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

            // The exporting class is the owner, so inherited RPC methods still resolve
            // their generic types against this class's context.
            val ownerClassType = ResolvedType.ClassType(psiClass, emptyList())
            for (endpoint in endpoints) {
                val method = endpoint.sourceMethod ?: continue
                val resolvedMethod = ResolvedMethod.of(method, ownerClassType)
                parseAspects.afterEndpoint(EndpointParseContext(endpoint, frameworkName, resolvedMethod))
            }

            LOG.info("after parse gRPC class:$className, found ${endpoints.size} endpoints")
            endpoints
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
