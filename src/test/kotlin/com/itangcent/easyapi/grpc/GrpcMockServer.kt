package com.itangcent.easyapi.grpc

import com.itangcent.easyapi.grpc.test.EchoServiceGrpc
import com.itangcent.easyapi.grpc.test.EchoServiceGrpc.EchoServiceImplBase
import com.itangcent.easyapi.grpc.test.*
import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.stub.StreamObserver
import java.util.concurrent.TimeUnit

/**
 * Mock gRPC server for integration testing.
 *
 * This server provides a test implementation of the EchoService defined in test_service.proto.
 * It supports gRPC reflection, allowing tools like grpcurl to discover and call methods
 * without needing proto files at runtime.
 *
 * Usage:
 * ```kotlin
 * // Start server on random available port
 * val server = GrpcMockServer.startOnRandomPort()
 * println("Server running at ${server.host()}")
 *
 * // Or start on specific port
 * val server = GrpcMockServer.startOnPort(50051)
 *
 * // Clean up when done
 * server.stop()
 * ```
 *
 * @param port The port to bind to. Use 0 (default) for automatic port allocation.
 */
class GrpcMockServer(private val port: Int = 0) {

    private var server: io.grpc.Server? = null

    /**
     * The actual port the server is listening on.
     * Useful when port is set to 0 for auto-allocation.
     */
    val actualPort: Int get() = server?.port ?: 0

    /**
     * Starts the gRPC server.
     *
     * @return This server instance for method chaining
     */
    fun start(): GrpcMockServer {
        val actualPort = if (port > 0) port else findFreePort()
        server = ServerBuilder.forPort(actualPort)
            .addService(EchoServiceImpl())
            .addService(ProtoReflectionService.newInstance())
            .build()
            .start()
        return this
    }

    /**
     * Stops the gRPC server gracefully, then forcefully if needed.
     */
    fun stop() {
        val s = server ?: return
        server = null
        s.shutdown()
        if (!s.awaitTermination(5, TimeUnit.SECONDS)) {
            s.shutdownNow()
            s.awaitTermination(2, TimeUnit.SECONDS)
        }
    }

    /**
     * Returns the host address string in format "localhost:port".
     * Can be used directly with gRPC clients.
     */
    fun host(): String = "localhost:${server?.port ?: port}"

    /**
     * Finds an available port on the system.
     * Used when port is set to 0 for automatic allocation.
     */
    private fun findFreePort(): Int {
        return java.net.ServerSocket(0).use { it.localPort }
    }

    /**
     * Implementation of the EchoService for testing purposes.
     *
     * Provides three RPC methods:
     * - **echo**: Echoes back the request message with additional metadata
     * - **echoEmpty**: Returns an empty echo response
     * - **reverse**: Reverses the input text and reports its length
     */
    internal class EchoServiceImpl : EchoServiceImplBase() {

        /**
         * Echoes the request message back with all fields preserved.
         * Sets status to "ok" to indicate successful processing.
         */
        override fun echo(request: EchoRequest, responseObserver: StreamObserver<EchoResponse>) {
            val response = EchoResponse.newBuilder()
                .setEchoed(request.message)
                .setReceivedCount(request.count)
                .setKey1(request.key1)
                .setValue1(request.value1)
                .setKey2(request.key2)
                .setValue2(request.value2)
                .setStatus("ok")
                .build()
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }

        /**
         * Returns an empty echo response with just the status field set.
         * Useful for testing empty request handling.
         */
        override fun echoEmpty(request: EmptyRequest, responseObserver: StreamObserver<EchoResponse>) {
            val response = EchoResponse.newBuilder()
                .setEchoed("")
                .setStatus("ok")
                .build()
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }

        /**
         * Reverses the input text and returns both the reversed string
         * and the original length. Handles Unicode characters correctly.
         */
        override fun reverse(request: ReverseRequest, responseObserver: StreamObserver<ReverseResponse>) {
            val reversed = request.text.reversed()
            val response = ReverseResponse.newBuilder()
                .setReversed(reversed)
                .setLength(request.text.length)
                .build()
            responseObserver.onNext(response)
            responseObserver.onCompleted()
        }
    }

    companion object {
        /**
         * Creates and starts a server on a random available port.
         * Convenience method for tests that don't need a specific port.
         */
        fun startOnRandomPort(): GrpcMockServer = GrpcMockServer().start()

        /**
         * Creates and starts a server on the specified port.
         *
         * @param port The port to bind to
         */
        fun startOnPort(port: Int): GrpcMockServer = GrpcMockServer(port).start()
    }
}
