package com.itangcent.easyapi.grpc

/**
 * Strategy interface for resolving gRPC method descriptors.
 *
 * Implementations provide different approaches to obtain protobuf message descriptors
 * for gRPC service methods, enabling dynamic invocation without compile-time stubs.
 *
 * @see CompositeDescriptorResolver for the default chained implementation
 */
interface DescriptorResolver {
    /**
     * Attempt to resolve the input and output message descriptors for the given method.
     * Returns null if this resolver cannot satisfy the request.
     *
     * @param classLoader The gRPC runtime classloader (for building FileDescriptor)
     * @param serviceName Fully-qualified gRPC service name (e.g. "audit.AuditQueryService")
     * @param methodName  RPC method name (e.g. "queryByRequestId")
     * @param channel     Live ManagedChannel — only needed by ServerReflectionResolver
     * @param sourceMethod PSI method from the ApiEndpoint — allows StubClassResolver to
     *                    read types directly without any project-wide search
     */
    suspend fun resolve(
        classLoader: ClassLoader,
        serviceName: String,
        methodName: String,
        channel: Any?,
        sourceMethod: com.intellij.psi.PsiMethod? = null
    ): ResolvedDescriptor?
}

/**
 * Holds the resolved input and output message descriptors for a gRPC method.
 *
 * @param inputDescriptor The protobuf descriptor for the request message type
 * @param outputDescriptor The protobuf descriptor for the response message type
 * @param source The resolution strategy that successfully resolved this descriptor
 */
data class ResolvedDescriptor(
    val inputDescriptor: Any,   // com.google.protobuf.Descriptors.Descriptor
    val outputDescriptor: Any,  // com.google.protobuf.Descriptors.Descriptor
    val source: DescriptorSource,
    val actualMethodName: String? = null  // The actual method name from proto (may differ in case from requested)
)

/**
 * Indicates the source strategy used to resolve a gRPC method descriptor.
 */
enum class DescriptorSource {
    /** Resolved from .proto files in the project */
    PROTO_FILE,

    /** Resolved from generated stub classes via PSI analysis */
    STUB_CLASS,

    /** Resolved via gRPC server reflection at runtime */
    SERVER_REFLECTION
}
