package com.itangcent.easyapi.grpc

import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Resolves gRPC method descriptors via gRPC Server Reflection.
 *
 * This resolver uses the gRPC Server Reflection protocol to query a running
 * gRPC server for its service descriptors at runtime. This allows discovering
 * and invoking gRPC services without access to .proto files or generated stubs.
 *
 * The resolver supports both v1 (grpc-services >= 1.39) and v1alpha reflection APIs.
 * It requires an active [ManagedChannel] to communicate with the target server.
 *
 * @see DescriptorResolver
 * @see CompositeDescriptorResolver
 */
class ServerReflectionResolver : DescriptorResolver {

    override suspend fun resolve(
        classLoader: ClassLoader,
        serviceName: String,
        methodName: String,
        channel: Any?,
        sourceMethod: com.intellij.psi.PsiMethod?
    ): ResolvedDescriptor? {
        if (channel == null) return null
        return fetchReflectionData(classLoader, channel, serviceName, methodName)
    }

    private fun fetchReflectionData(
        classLoader: ClassLoader,
        channel: Any,
        serviceName: String,
        methodName: String
    ): ResolvedDescriptor? {
        println("[DEBUG] fetchReflectionData: serviceName=$serviceName, methodName=$methodName")
        // Try v1 first (grpc-services >= 1.39), fall back to v1alpha
        val reflectionPackage = listOf(
            "io.grpc.reflection.v1",
            "io.grpc.reflection.v1alpha"
        ).firstOrNull { pkg ->
            try { classLoader.loadClass("$pkg.ServerReflectionGrpc"); true } catch (_: ClassNotFoundException) { false }
        } ?: return null

        return try {
            val reflectionStubClass = classLoader.loadClass("$reflectionPackage.ServerReflectionGrpc")
            println("[DEBUG] Loaded reflection stub class: ${reflectionStubClass.name}")
            val newStubMethod = reflectionStubClass.getMethod("newStub", classLoader.loadClass("io.grpc.Channel"))
            val stub = newStubMethod.invoke(null, channel)
            println("[DEBUG] Created stub: ${stub.javaClass.name}")

            val serverReflectionRequestClass =
                classLoader.loadClass("$reflectionPackage.ServerReflectionRequest")
            println("[DEBUG] Loaded request class: ${serverReflectionRequestClass.name}")
            val newBuilderMethod = serverReflectionRequestClass.getMethod("newBuilder")
            val requestBuilder = newBuilderMethod.invoke(null)

            val setFileContainingSymbolMethod = requestBuilder.javaClass.getMethod(
                "setFileContainingSymbol",
                String::class.java
            )
            setFileContainingSymbolMethod.invoke(requestBuilder, serviceName)

            val buildMethod = requestBuilder.javaClass.getMethod("build")
            val request = buildMethod.invoke(requestBuilder)
            println("[DEBUG] Created request: $request")

            val streamObserverClass = classLoader.loadClass("io.grpc.stub.StreamObserver")
            println("[DEBUG] Loaded StreamObserver class")

            val resultRef = AtomicReference<ResolvedDescriptor?>(null)
            val errorRef = AtomicReference<Throwable?>(null)
            val latch = CountDownLatch(1)

            println("[DEBUG] Creating response observer proxy...")
            val responseObserver = Proxy.newProxyInstance(
                classLoader,
                arrayOf(streamObserverClass)
            ) { _, method, args ->
                println("[DEBUG] Proxy called: ${method.name}")
                when (method.name) {
                    "onNext" -> {
                        val response = args?.get(0) ?: return@newProxyInstance null
                        println("[DEBUG] onNext response type: ${response.javaClass.name}")
                        try {
                            val getMessageResponseMethod = response.javaClass.getMethod("getMessageResponseCase")
                            val messageResponseCase = getMessageResponseMethod.invoke(response)
                            val getNumberMethod = messageResponseCase.javaClass.getMethod("getNumber")
                            val caseNumber = getNumberMethod.invoke(messageResponseCase) as Int
                            println("[DEBUG] Message response case: $caseNumber")

                            if (caseNumber == 1) {
                                val getFileDescriptorResponseMethod =
                                    response.javaClass.getMethod("getFileDescriptorResponse")
                                val fdResponse = getFileDescriptorResponseMethod.invoke(response)
                                println("[DEBUG] File descriptor response: ${fdResponse?.javaClass?.name}")
                                if (fdResponse != null) {
                                    val getFileDescriptorProtoListMethod =
                                        fdResponse.javaClass.getMethod("getFileDescriptorProtoList")
                                    val protoBytesList =
                                        getFileDescriptorProtoListMethod.invoke(fdResponse) as List<*>
                                    println("[DEBUG] Proto bytes list size: ${protoBytesList.size}")

                                    for (protoBytes in protoBytesList) {
                                        // v1 returns ByteString, v1alpha returns byte[]
                                        val bytes: ByteArray = when (protoBytes) {
                                            is ByteArray -> protoBytes
                                            else -> {
                                                // ByteString — call toByteArray() via reflection
                                                try {
                                                    protoBytes!!.javaClass.getMethod("toByteArray").invoke(protoBytes) as? ByteArray
                                                } catch (_: Exception) { null }
                                            }
                                        } ?: continue
                                        val result = parseFileDescriptor(
                                            classLoader,
                                            bytes,
                                            serviceName,
                                            methodName
                                        )
                                        if (result != null) {
                                            println("[DEBUG] Found reflection data!")
                                            resultRef.set(result)
                                            break
                                        }
                                    }
                                }
                            } else if (caseNumber == 3 || caseNumber == 5) {
                                val getErrorResponseMethod = response.javaClass.getMethod("getErrorResponse")
                                val errorResponse = getErrorResponseMethod.invoke(response)
                                if (errorResponse != null) {
                                    val getErrorCodeMethod = errorResponse.javaClass.getMethod("getErrorCode")
                                    val errorCode = getErrorCodeMethod.invoke(errorResponse)
                                    val getErrorMessageMethod =
                                        errorResponse.javaClass.getMethod("getErrorMessage")
                                    val errorMsg = getErrorMessageMethod.invoke(errorResponse) as String
                                    println("[DEBUG] Reflection error: code=$errorCode, message=$errorMsg")
                                    errorRef.set(RuntimeException("Reflection error: code=$errorCode, message=$errorMsg"))
                                }
                            } else {
                                println("[DEBUG] Unknown message response case: $caseNumber")
                            }
                        } catch (e: Exception) {
                            errorRef.set(e)
                        }
                        null
                    }

                    "onError" -> {
                        errorRef.set(args?.get(0) as? Throwable)
                        latch.countDown()
                        null
                    }

                    "onCompleted" -> {
                        latch.countDown()
                        null
                    }

                    else -> null
                }
            }

            val serverReflectionMethod = stub.javaClass.getMethod(
                "serverReflectionInfo",
                streamObserverClass
            )
            val requestObserver = serverReflectionMethod.invoke(stub, responseObserver)

            val onNextMethod = requestObserver.javaClass.getDeclaredMethod("onNext", Any::class.java)
            onNextMethod.isAccessible = true
            onNextMethod.invoke(requestObserver, request)

            val onCompletedMethod = requestObserver.javaClass.getDeclaredMethod("onCompleted")
            onCompletedMethod.isAccessible = true
            onCompletedMethod.invoke(requestObserver)

            if (!latch.await(10, TimeUnit.SECONDS)) {
                return null
            }

            errorRef.get()?.let { return null }
            resultRef.get()
        } catch (e: Exception) {
            println("[DEBUG] Exception in fetchReflectionData: ${e.javaClass.name}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun parseFileDescriptor(
        classLoader: ClassLoader,
        protoBytes: ByteArray,
        serviceName: String,
        methodName: String
    ): ResolvedDescriptor? {
        return try {
            val fileDescriptorClass = classLoader.loadClass("com.google.protobuf.Descriptors\$FileDescriptor")
            val descriptorProtoClass = classLoader.loadClass("com.google.protobuf.FileDescriptorProto")

            val parseFromMethod = descriptorProtoClass.getMethod("parseFrom", ByteArray::class.java)
            val fileProto = parseFromMethod.invoke(null, protoBytes)

            val buildFromMethod = fileDescriptorClass.getMethod(
                "buildFrom",
                descriptorProtoClass,
                Array::class.java
            )
            @Suppress("UNCHECKED_CAST")
            val fileDescriptor = buildFromMethod.invoke(null, fileProto, null as Array<Any>?)

            val getServicesMethod = fileDescriptor.javaClass.getMethod("getServices")
            @Suppress("UNCHECKED_CAST")
            val services = getServicesMethod.invoke(fileDescriptor) as List<Any>

            for (service in services) {
                val getFullNameMethod = service.javaClass.getMethod("getFullName")
                val fullName = getFullNameMethod.invoke(service) as String

                if (fullName == serviceName) {
                    val getMethodsMethod = service.javaClass.getMethod("getMethods")
                    @Suppress("UNCHECKED_CAST")
                    val methods = getMethodsMethod.invoke(service) as List<Any>

                    for (method in methods) {
                        val getNameMethod = method.javaClass.getMethod("getName")
                        val name = getNameMethod.invoke(method) as String

                        if (name == methodName) {
                            val getInputTypeMethod = method.javaClass.getMethod("getInputType")
                            val inputDescriptor = getInputTypeMethod.invoke(method)

                            val getOutputTypeMethod = method.javaClass.getMethod("getOutputType")
                            val outputDescriptor = getOutputTypeMethod.invoke(method)

                            return ResolvedDescriptor(
                                inputDescriptor = inputDescriptor,
                                outputDescriptor = outputDescriptor,
                                source = DescriptorSource.SERVER_REFLECTION
                            )
                        }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
