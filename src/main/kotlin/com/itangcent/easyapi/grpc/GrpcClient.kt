package com.itangcent.easyapi.grpc

/**
 * Interface for gRPC client implementations.
 * 
 * Provides a unified API for invoking gRPC methods regardless of the underlying
 * implementation strategy (dynamic class loading, generated stubs, etc.).
 * 
 * Implementations:
 * - [DynamicJarClient]: Loads gRPC runtime JARs dynamically and uses reflection
 * 
 * Usage:
 * ```kotlin
 * val client = GrpcClientProvider.getInstance(project).getClient()
 * if (client.isAvailable()) {
 *     val result = client.invoke("localhost:50051", "/com.example.EchoService/echo", "{}")
 * }
 * ```
 * 
 * @see DynamicJarClient for the primary implementation
 * @see GrpcResult for the result type
 */
interface GrpcClient {
    /**
     * Invokes a gRPC method.
     * 
     * @param host The host address in "host:port" format (e.g., "localhost:50051")
     * @param path The full gRPC path in "/{package}.{Service}/{method}" format
     * @param body The request body as a JSON string (will be converted to protobuf)
     * @return [GrpcResult] containing the response or error
     */
    suspend fun invoke(host: String, path: String, body: String?): GrpcResult

    /**
     * Checks if the gRPC client is available for use.
     * 
     * @return true if the runtime dependencies are resolved and the client is ready
     */
    fun isAvailable(): Boolean
}
