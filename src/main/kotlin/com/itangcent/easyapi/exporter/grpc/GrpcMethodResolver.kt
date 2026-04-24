package com.itangcent.easyapi.exporter.grpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.exporter.model.GrpcStreamingType
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.core.threading.read

/**
 * Resolved information about a single gRPC RPC method.
 *
 * @param methodName The RPC method name (e.g., "SayHello")
 * @param serviceName The gRPC service name (e.g., "GreeterService")
 * @param packageName The protobuf package name (e.g., "com.example")
 * @param fullPath The full gRPC path (e.g., "/com.example.GreeterService/SayHello")
 * @param streamingType The gRPC communication pattern
 * @param requestType The request message PsiClass, or null if unresolvable
 * @param responseType The response message PsiClass, or null if unresolvable
 * @param psiMethod The underlying PSI method
 * @param description Optional method description from Javadoc
 */
data class GrpcMethodInfo(
    val methodName: String,
    val serviceName: String,
    val packageName: String,
    val fullPath: String,
    val streamingType: GrpcStreamingType,
    val requestType: PsiClass?,
    val responseType: PsiClass?,
    val psiMethod: PsiMethod,
    val description: String? = null
)

/**
 * Resolves RPC methods from a gRPC service implementation class.
 *
 * Analyzes method signatures to discover RPC methods and determine their
 * streaming types. Extracts service and package names from the generated
 * base class hierarchy (e.g., `XxxGrpc.XxxImplBase`).
 *
 * Streaming type detection is based on method signature patterns:
 * - `(Req, StreamObserver<Resp>) -> void` → UNARY (or SERVER_STREAMING)
 * - `(StreamObserver<Resp>) -> StreamObserver<Req>` → CLIENT_STREAMING (or BIDIRECTIONAL)
 *
 * @param docHelper Optional helper for extracting Javadoc descriptions
 */
@Service(Service.Level.PROJECT)
class GrpcMethodResolver(private val project: Project) {

    private val docHelper: DocHelper get() = UnifiedDocHelper.getInstance(project)
    private val annotationHelper = UnifiedAnnotationHelper()

    companion object : IdeaLog {
        fun getInstance(project: Project): GrpcMethodResolver = project.service()
        private const val STREAM_OBSERVER_PREFIX = "io.grpc.stub.StreamObserver"
        private const val RPC_METHOD_ANNOTATION = "io.grpc.stub.annotations.RpcMethod"

        /** Methods inherited from Object that should be excluded. */
        private val OBJECT_METHOD_NAMES = setOf(
            "equals", "hashCode", "toString", "getClass",
            "notify", "notifyAll", "wait", "clone", "finalize"
        )

        /** gRPC lifecycle/infrastructure methods to exclude. */
        private val LIFECYCLE_METHOD_NAMES = setOf(
            "bindService", "serviceImpl", "build",
            "getServiceDescriptor", "getMethodDescriptors"
        )
    }

    /**
     * Discovers all RPC methods in the given gRPC service class.
     *
     * Filters out non-public, Object, and lifecycle methods.
     * For instance methods: matches RPC signature patterns, falls back to @RpcMethod annotation.
     * For static methods: only processes if @RpcMethod annotation is present.
     *
     * @param psiClass A gRPC service implementation class
     * @return List of resolved RPC method info, empty if none found
     */
    suspend fun resolveRpcMethods(psiClass: PsiClass): List<GrpcMethodInfo> {
        return read {
            resolveRpcMethodsInternal(psiClass)
        }
    }

    private suspend fun resolveRpcMethodsInternal(psiClass: PsiClass): List<GrpcMethodInfo> {
        val serviceName = extractServiceName(psiClass)
        val packageName = extractPackageName(psiClass)
        val results = mutableListOf<GrpcMethodInfo>()

        for (method in psiClass.methods) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) continue
            if (isObjectMethod(method)) continue
            if (isLifecycleMethod(method)) continue

            val isStatic = method.hasModifierProperty(PsiModifier.STATIC)
            val description = try {
                docHelper.getAttrOfDocComment(method)
            } catch (_: Exception) {
                null
            }

            if (isStatic) {
                val annotationInfo = extractFromRpcMethodAnnotation(method, serviceName, packageName, description)
                if (annotationInfo != null) {
                    results.add(annotationInfo)
                }
            } else {
                val streamingType = resolveStreamingType(method)

                if (streamingType != null) {
                    val (requestType, responseType) = extractTypes(method, streamingType)
                    val fullPath = "/$packageName.$serviceName/${method.name}"

                    results.add(
                        GrpcMethodInfo(
                            methodName = method.name,
                            serviceName = serviceName,
                            packageName = packageName,
                            fullPath = fullPath,
                            streamingType = streamingType,
                            requestType = requestType,
                            responseType = responseType,
                            psiMethod = method,
                            description = description
                        )
                    )
                } else {
                    val annotationInfo = extractFromRpcMethodAnnotation(method, serviceName, packageName, description)
                    if (annotationInfo != null) {
                        results.add(annotationInfo)
                    }
                }
            }
        }

        return results
    }

    /**
     * Detects the gRPC streaming type from a method's signature.
     *
     * Pattern 1 — Unary / Server-streaming:
     *   `void methodName(RequestType request, StreamObserver<ResponseType> responseObserver)`
     *
     * Pattern 2 — Client-streaming / Bidirectional:
     *   `StreamObserver<RequestType> methodName(StreamObserver<ResponseType> responseObserver)`
     *
     * Unary vs server-streaming and client vs bidirectional share the same
     * Java signature at the impl level. We default to UNARY and CLIENT_STREAMING
     * respectively; callers may refine via base-class analysis if needed.
     *
     * @return The detected streaming type, or null if the method is not an RPC method
     */
    fun resolveStreamingType(method: PsiMethod): GrpcStreamingType? {
        val params = method.parameterList.parameters
        val returnType = method.returnType

        // Pattern 1: (Req, StreamObserver<Resp>) -> void
        if (params.size == 2
            && !isStreamObserver(params[0].type)
            && isStreamObserver(params[1].type)
        ) {
            // At the impl level, unary and server-streaming have the same signature.
            // Default to UNARY; could be refined by checking the generated base class.
            return GrpcStreamingType.UNARY
        }

        // Pattern 2: (StreamObserver<Resp>) -> StreamObserver<Req>
        if (params.size == 1
            && isStreamObserver(params[0].type)
            && returnType != null
            && isStreamObserver(returnType)
        ) {
            // Client-streaming and bidirectional share the same signature.
            // Default to CLIENT_STREAMING; could be refined by checking the generated base class.
            return GrpcStreamingType.CLIENT_STREAMING
        }

        return null
    }

    /**
     * Extracts the gRPC service name from the class hierarchy.
     *
     * gRPC generated code creates `XxxGrpc.XxxImplBase`. The service name
     * is derived by removing the "ImplBase" suffix from the superclass name.
     * Falls back to the class's own name if no ImplBase superclass is found.
     */
    fun extractServiceName(psiClass: PsiClass): String {
        // Look for a superclass ending in "ImplBase"
        for (superClass in psiClass.supers) {
            val name = superClass.name ?: continue
            if (name.endsWith("ImplBase")) {
                return name.removeSuffix("ImplBase")
            }
        }
        // Fallback: use the class name itself, stripping common suffixes
        val className = psiClass.name ?: "UnknownService"
        return className
            .removeSuffix("Impl")
            .removeSuffix("Service")
            .let { if (it.isBlank()) className else it + "Service" }
            .let { base ->
                // If we stripped nothing, just return the class name
                if (base == className + "Service" && !className.endsWith("Impl") && !className.endsWith("Service")) {
                    className
                } else {
                    base
                }
            }
    }

    /**
     * Extracts the protobuf package name from the class hierarchy.
     *
     * Looks at the ImplBase superclass's containing (outer) class package,
     * or falls back to the service class's own package.
     */
    fun extractPackageName(psiClass: PsiClass): String {
        // Look for ImplBase superclass and use its outer class's package
        for (superClass in psiClass.supers) {
            val name = superClass.name ?: continue
            if (name.endsWith("ImplBase")) {
                // The ImplBase is typically an inner class of XxxGrpc
                val containingClass = superClass.containingClass
                if (containingClass != null) {
                    val outerPackage = containingClass.qualifiedName
                        ?.substringBeforeLast('.', "")
                    if (!outerPackage.isNullOrBlank()) return outerPackage
                }
                // Fallback: use the ImplBase class's own package
                val implBasePackage = superClass.qualifiedName
                    ?.substringBeforeLast('.', "")
                if (!implBasePackage.isNullOrBlank()) return implBasePackage
            }
        }
        // Fallback: use the service class's own package
        return psiClass.qualifiedName
            ?.substringBeforeLast('.', "")
            ?: ""
    }

    /**
     * Extracts gRPC method info from the @RpcMethod annotation.
     *
     * The @RpcMethod annotation is generated by gRPC and contains:
     * - fullMethodName: SERVICE_NAME + '/' + "MethodName" (e.g., "GreeterService/SayHello")
     * - requestType: The request message class
     * - responseType: The response message class
     * - methodType: The gRPC method type (UNARY, CLIENT_STREAMING, etc.)
     *
     * @param method The PSI method to extract annotation from
     * @param fallbackServiceName Service name to use if not found in annotation
     * @param fallbackPackageName Package name to use if not found in annotation
     * @param description Optional method description
     * @return GrpcMethodInfo if annotation is found and valid, null otherwise
     */
    private suspend fun extractFromRpcMethodAnnotation(
        method: PsiMethod,
        fallbackServiceName: String,
        fallbackPackageName: String,
        description: String?
    ): GrpcMethodInfo? {
        val hasAnn = annotationHelper.hasAnn(method, RPC_METHOD_ANNOTATION)
        if (!hasAnn) return null

        val fullMethodName = annotationHelper.findAttrAsString(method, RPC_METHOD_ANNOTATION, "fullMethodName")
            ?: return null

        val parsedInfo = parseFullMethodName(fullMethodName)
        val serviceName = parsedInfo?.serviceName ?: fallbackServiceName
        val methodName = parsedInfo?.methodName ?: method.name
        val packageNameFromFullMethod = parsedInfo?.packageName

        val methodTypeStr = annotationHelper.findAttrAsString(method, RPC_METHOD_ANNOTATION, "methodType")
        val streamingType = mapMethodTypeToStreamingType(methodTypeStr) ?: GrpcStreamingType.UNARY

        val requestTypeFqn = annotationHelper.findAttr(method, RPC_METHOD_ANNOTATION, "requestType") as? String
        val responseTypeFqn = annotationHelper.findAttr(method, RPC_METHOD_ANNOTATION, "responseType") as? String

        val requestType = requestTypeFqn?.let { findClass(it) }
        val responseType = responseTypeFqn?.let { findClass(it) }

        val packageName = packageNameFromFullMethod
            ?: extractPackageNameFromFqn(requestTypeFqn, responseTypeFqn)
                .ifBlank { fallbackPackageName }

        val fullPath = "/$packageName.$serviceName/$methodName"

        return GrpcMethodInfo(
            methodName = methodName,
            serviceName = serviceName,
            packageName = packageName,
            fullPath = fullPath,
            streamingType = streamingType,
            requestType = requestType,
            responseType = responseType,
            psiMethod = method,
            description = description
        )
    }

    private data class ParsedMethodName(
        val packageName: String?,
        val serviceName: String,
        val methodName: String
    )

    /**
     * Parses the fullMethodName from @RpcMethod annotation.
     * Format: "ServiceName/MethodName" or "package.ServiceName/MethodName" or "/package.ServiceName/MethodName"
     *
     * @return ParsedMethodName with package (if present), service name, and method name, or null if parsing fails
     */
    private fun parseFullMethodName(fullMethodName: String): ParsedMethodName? {
        val parts = fullMethodName.split('/')
        return when {
            parts.size == 2 -> {
                val servicePart = parts[0]
                val methodName = parts[1]
                val lastDot = servicePart.lastIndexOf('.')
                if (lastDot > 0) {
                    ParsedMethodName(
                        packageName = servicePart.substring(0, lastDot),
                        serviceName = servicePart.substring(lastDot + 1),
                        methodName = methodName
                    )
                } else {
                    ParsedMethodName(
                        packageName = null,
                        serviceName = servicePart,
                        methodName = methodName
                    )
                }
            }

            parts.size == 3 && parts[0].isEmpty() -> {
                val serviceFqn = parts[1]
                val methodName = parts[2]
                val lastDot = serviceFqn.lastIndexOf('.')
                if (lastDot > 0) {
                    ParsedMethodName(
                        packageName = serviceFqn.substring(0, lastDot),
                        serviceName = serviceFqn.substring(lastDot + 1),
                        methodName = methodName
                    )
                } else {
                    ParsedMethodName(
                        packageName = null,
                        serviceName = serviceFqn,
                        methodName = methodName
                    )
                }
            }

            else -> null
        }
    }

    /**
     * Maps gRPC MethodType enum name to GrpcStreamingType.
     */
    private fun mapMethodTypeToStreamingType(methodType: String?): GrpcStreamingType? {
        return when (methodType) {
            "UNARY" -> GrpcStreamingType.UNARY
            "SERVER_STREAMING" -> GrpcStreamingType.SERVER_STREAMING
            "CLIENT_STREAMING" -> GrpcStreamingType.CLIENT_STREAMING
            "BIDIRECTIONAL_STREAMING" -> GrpcStreamingType.BIDIRECTIONAL
            else -> null
        }
    }

    /**
     * Extracts package name from request or response type FQN.
     */
    private fun extractPackageNameFromFqn(requestTypeFqn: String?, responseTypeFqn: String?): String {
        return requestTypeFqn?.substringBeforeLast('.', "")
            ?.ifBlank { responseTypeFqn?.substringBeforeLast('.', "") }
            ?: ""
    }

    /**
     * Finds a PsiClass by its fully qualified name.
     */
    private fun findClass(fqn: String): PsiClass? {
        return JavaPsiFacade.getInstance(project)
            .findClass(fqn, GlobalSearchScope.allScope(project))
    }

    // ── Private helpers ──────────────────────────────────────────────

    private fun isObjectMethod(method: PsiMethod): Boolean {
        return method.name in OBJECT_METHOD_NAMES
    }

    private fun isLifecycleMethod(method: PsiMethod): Boolean {
        return method.name in LIFECYCLE_METHOD_NAMES
    }

    /**
     * Checks if a [PsiType] is `io.grpc.stub.StreamObserver` (or a parameterized form of it).
     */
    private fun isStreamObserver(type: PsiType): Boolean {
        return type.canonicalText.startsWith(STREAM_OBSERVER_PREFIX)
    }

    /**
     * Extracts the request and response PsiClass types from a method based on its streaming type.
     *
     * @return Pair of (requestType, responseType), either may be null
     */
    private fun extractTypes(method: PsiMethod, streamingType: GrpcStreamingType): Pair<PsiClass?, PsiClass?> {
        val params = method.parameterList.parameters

        return when (streamingType) {
            GrpcStreamingType.UNARY, GrpcStreamingType.SERVER_STREAMING -> {
                // (Req, StreamObserver<Resp>) -> void
                val requestType = resolveClassFromType(params[0].type)
                val responseType = extractStreamObserverTypeArg(params[1].type)
                Pair(requestType, responseType)
            }

            GrpcStreamingType.CLIENT_STREAMING, GrpcStreamingType.BIDIRECTIONAL -> {
                // (StreamObserver<Resp>) -> StreamObserver<Req>
                val responseType = extractStreamObserverTypeArg(params[0].type)
                val requestType = method.returnType?.let { extractStreamObserverTypeArg(it) }
                Pair(requestType, responseType)
            }
        }
    }

    /**
     * Extracts the type argument from a `StreamObserver<T>` type and resolves it to a PsiClass.
     */
    private fun extractStreamObserverTypeArg(type: PsiType): PsiClass? {
        if (type !is com.intellij.psi.PsiClassType) return null
        val typeArgs = type.parameters
        if (typeArgs.isEmpty()) return null
        return resolveClassFromType(typeArgs[0])
    }

    /**
     * Resolves a PsiType to its PsiClass.
     */
    private fun resolveClassFromType(type: PsiType): PsiClass? {
        return com.intellij.psi.util.PsiTypesUtil.getPsiClass(type)
    }
}
