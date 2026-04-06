package com.itangcent.easyapi.exporter.grpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.itangcent.easyapi.exporter.model.GrpcStreamingType
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper

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

    private val docHelper: DocHelper get() = StandardDocHelper.getInstance(project)

    companion object {
        fun getInstance(project: Project): GrpcMethodResolver = project.service()
        private const val STREAM_OBSERVER_PREFIX = "io.grpc.stub.StreamObserver"

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
     * Filters out non-public, static, Object, and lifecycle methods.
     * Only methods matching a recognized RPC signature pattern are included.
     *
     * @param psiClass A gRPC service implementation class
     * @return List of resolved RPC method info, empty if none found
     */
    suspend fun resolveRpcMethods(psiClass: PsiClass): List<GrpcMethodInfo> {
        val serviceName = extractServiceName(psiClass)
        val packageName = extractPackageName(psiClass)
        val results = mutableListOf<GrpcMethodInfo>()

        for (method in psiClass.methods) {
            if (!method.hasModifierProperty(PsiModifier.PUBLIC)) continue
            if (method.hasModifierProperty(PsiModifier.STATIC)) continue
            if (isObjectMethod(method)) continue
            if (isLifecycleMethod(method)) continue

            val streamingType = resolveStreamingType(method) ?: continue

            val (requestType, responseType) = extractTypes(method, streamingType)
            val fullPath = "/$packageName.$serviceName/${method.name}"

            val description = try {
                docHelper.getAttrOfDocComment(method)
            } catch (_: Exception) {
                null
            }

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
