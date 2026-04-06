package com.itangcent.easyapi.grpc

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.logging.IdeaLog
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.lang.reflect.Proxy
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

/**
 * A [GrpcClient] implementation that uses dynamic class loading to invoke gRPC methods.
 *
 * This application-level service loads gRPC runtime JARs dynamically (either from the
 * project classpath, local Maven/Gradle cache, or downloaded from Maven Central) and
 * uses reflection to:
 * 1. Create gRPC channels and stubs
 * 2. Convert JSON requests to protobuf binary format
 * 3. Invoke unary RPC methods
 * 4. Convert protobuf binary responses back to JSON
 *
 * This approach allows invoking any gRPC service without compile-time stubs,
 * making it ideal for IDE plugins and development tools.
 *
 * @see GrpcClient
 * @see GrpcRuntimeResolver for runtime JAR resolution
 * @see CompositeDescriptorResolver for descriptor resolution
 */
@Service(Service.Level.APP)
class DynamicJarClient(val project: Project) : GrpcClient, IdeaLog {

    private val runtimeResolver: GrpcRuntimeResolver by lazy {
        GrpcRuntimeResolver.getInstance(project)
    }

    private val descriptorResolver: CompositeDescriptorResolver by lazy {
        CompositeDescriptorResolver(project)
    }

    companion object {
        fun getInstance(project: Project): DynamicJarClient = project.service()
    }

    private var cachedClassLoader: ClassLoader? = null
    private var cachedRuntime: ResolvedRuntime? = null

    override fun isAvailable(): Boolean {
        return runtimeResolver.isAvailable()
    }

    fun getResolvedRuntime(): ResolvedRuntime? {
        if (cachedRuntime == null) {
            cachedRuntime = runtimeResolver.resolve()
        }
        return cachedRuntime
    }

    override suspend fun invoke(host: String, path: String, body: String?): GrpcResult {
        return invoke(host, path, body, null)
    }

    suspend fun invoke(host: String, path: String, body: String?, sourceMethod: PsiMethod?): GrpcResult {
        LOG.info("gRPC invoke started: host=$host, path=$path, body=${body?.take(200)}")
        val classLoader = cachedClassLoader ?: resolveClassLoader()
        ?: return GrpcResult(
            body = "Error: gRPC runtime jars not available. Please configure gRPC runtime in Settings.",
            isError = true
        )

        return tryReflectionCall(classLoader, host, path, body, sourceMethod)
    }

    private fun resolveClassLoader(): ClassLoader? {
        LOG.info("Resolving gRPC runtime classloader")
        val runtime = getResolvedRuntime() ?: run {
            LOG.warn("Failed to resolve gRPC runtime: getResolvedRuntime() returned null")
            return null
        }
        LOG.info("gRPC runtime resolved: version=${runtime.version}")
        runtime.jars.forEach { jar ->
            LOG.info("  JAR: ${jar.fileName}")
        }
        val urls = runtime.jars.map { it.toUri().toURL() }.toTypedArray()
        // Use parent-last classloader to ensure our gRPC JARs take precedence
        // over any gRPC classes that might be in the IDE's classpath
        cachedClassLoader = ParentLastClassLoader(urls, DynamicJarClient::class.java.classLoader)
        LOG.info("gRPC classloader created with ${urls.size} JARs (parent-last delegation)")
        return cachedClassLoader
    }

    private suspend fun tryReflectionCall(
        classLoader: ClassLoader,
        host: String,
        path: String,
        body: String?,
        sourceMethod: PsiMethod? = null
    ): GrpcResult {
        val previousContextLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = classLoader
        try {
            val (address, port) = parseHostPort(host)
            LOG.info("Parsed host: address=$address, port=$port")

            val channel = createChannel(classLoader, address, port)
            LOG.info("gRPC channel created for $address:$port")

            try {
                val servicePath = path.trimStart('/')
                val parts = servicePath.split("/")
                if (parts.size < 2) {
                    LOG.warn("Invalid service path format: $path (expected: /package.Service/Method)")
                    return GrpcResult(
                        body = "Error: Invalid service path format. Expected: /package.Service/Method",
                        isError = true
                    )
                }
                val serviceName = parts[0]
                val methodName = parts[1]
                LOG.info("Parsed service: serviceName=$serviceName, methodName=$methodName")

                val resolvedDescriptor =
                    descriptorResolver.resolve(classLoader, serviceName, methodName, channel, sourceMethod)
                if (resolvedDescriptor == null) {
                    LOG.warn("Failed to resolve descriptor for $serviceName/$methodName")
                    return GrpcResult(
                        body = "Error: Could not resolve service descriptor. Add .proto files to the project or enable server reflection.",
                        isError = true
                    )
                }
                
                val actualMethodName = resolvedDescriptor.actualMethodName ?: methodName
                LOG.info("Descriptor resolved: source=${resolvedDescriptor.source}, service=$serviceName, method=$actualMethodName")

                val jsonBody = body ?: "{}"
                LOG.info("Converting JSON to protobuf: ${jsonBody.take(200)}")
                val requestBytes = jsonToProtobuf(
                    classLoader,
                    resolvedDescriptor.inputDescriptor,
                    jsonBody
                )
                LOG.info("Request serialized to ${requestBytes.size} bytes")

                LOG.info("Calling gRPC method: $serviceName/$actualMethodName")
                val responseBytes = callUnaryMethod(
                    classLoader,
                    channel,
                    serviceName,
                    actualMethodName,
                    resolvedDescriptor.inputDescriptor,
                    resolvedDescriptor.outputDescriptor,
                    requestBytes
                )
                LOG.info("Response received: ${responseBytes.size} bytes")

                val jsonResponse = protobufToJson(classLoader, resolvedDescriptor.outputDescriptor, responseBytes)
                LOG.info("gRPC call completed successfully: $serviceName/$methodName")
                return GrpcResult(
                    body = jsonResponse,
                    isError = false,
                    statusCode = GrpcStatus.OK,
                    statusName = GrpcStatus.getName(GrpcStatus.OK)
                )
            } finally {
                shutdownChannel(channel, classLoader)
                LOG.info("gRPC channel shutdown")
            }
        } catch (e: Exception) {
            LOG.warn("gRPC call failed: host=$host, path=$path", e)
            val actualException = unwrapInvocationTargetException(e)
            val (statusCode, statusName, errorMessage) = extractStatusAndMessage(classLoader, actualException)
            return GrpcResult(
                body = "Error: $errorMessage",
                isError = true,
                statusCode = statusCode,
                statusName = statusName
            )
        } finally {
            Thread.currentThread().contextClassLoader = previousContextLoader
        }
    }

    private fun createChannel(classLoader: ClassLoader, host: String, port: Int): Any {
        try {
            LOG.info("Loading ManagedChannelBuilder class")
            val channelBuilderClass = classLoader.loadClass("io.grpc.ManagedChannelBuilder")
            LOG.info("Getting forAddress method")
            val forAddressMethod = channelBuilderClass.getMethod("forAddress", String::class.java, Int::class.java)
            LOG.info("Calling forAddress($host, $port)")
            val builder = forAddressMethod.invoke(null, host, port)
            LOG.info("Getting usePlaintext method")
            val usePlaintextMethod = builder.javaClass.getMethod("usePlaintext")
            LOG.info("Calling usePlaintext")
            val channelBuilder = usePlaintextMethod.invoke(builder)
            LOG.info("Getting build method")
            val buildMethod = channelBuilder.javaClass.getMethod("build")
            LOG.info("Calling build")
            return buildMethod.invoke(channelBuilder)
        } catch (e: Exception) {
            val rootCause = unwrapInvocationTargetException(e)
            LOG.warn("Failed to create gRPC channel: ${rootCause.javaClass.simpleName}: ${rootCause.message}")
            LOG.warn("Root cause stack trace:", rootCause)
            throw e
        }
    }

    private fun shutdownChannel(channel: Any, classLoader: ClassLoader) {
        try {
            LOG.info("Shutting down gRPC channel")
            // Use the ManagedChannel interface to access methods
            val managedChannelClass = classLoader.loadClass("io.grpc.ManagedChannel")
            
            val shutdownMethod = managedChannelClass.getMethod("shutdown")
            shutdownMethod.invoke(channel)
            
            val isShutdownMethod = managedChannelClass.getMethod("isShutdown")
            val isTerminatedMethod = managedChannelClass.getMethod("isTerminated")
            val awaitTerminationMethod = managedChannelClass.getMethod(
                "awaitTermination",
                Long::class.java,
                TimeUnit::class.java
            )
            
            // Wait up to 5 seconds for termination
            awaitTerminationMethod.invoke(channel, 5L, TimeUnit.SECONDS)
            
            val isTerminated = isTerminatedMethod.invoke(channel) as Boolean
            if (!isTerminated) {
                LOG.warn("Channel did not terminate gracefully, forcing shutdown")
                val shutdownNowMethod = managedChannelClass.getMethod("shutdownNow")
                shutdownNowMethod.invoke(channel)
                awaitTerminationMethod.invoke(channel, 2L, TimeUnit.SECONDS)
            }
            
            LOG.info("gRPC channel shutdown complete (isShutdown=${isShutdownMethod.invoke(channel)}, isTerminated=${isTerminatedMethod.invoke(channel)})")
        } catch (e: Exception) {
            LOG.warn("Failed to shutdown gRPC channel: ${e.message}")
            try {
                // Fallback: try direct method access
                val shutdownNowMethod = channel.javaClass.getMethod("shutdownNow")
                shutdownNowMethod.invoke(channel)
            } catch (e2: Exception) {
                LOG.warn("Failed to force shutdown gRPC channel: ${e2.message}")
            }
        }
    }

    private fun jsonToProtobuf(
        classLoader: ClassLoader,
        descriptor: Any,
        json: String
    ): ByteArray {
        return try {
            val cleanedJson = cleanJsonForProtobuf(json)
            LOG.info("Cleaned JSON for protobuf: $cleanedJson")

            val dynamicMessageClass = classLoader.loadClass("com.google.protobuf.DynamicMessage")
            val jsonFormatClass = classLoader.loadClass("com.google.protobuf.util.JsonFormat")

            val parserMethod = jsonFormatClass.getMethod("parser")
            val parser = parserMethod.invoke(null)

            val newBuilderMethod = dynamicMessageClass.getMethod("newBuilder", descriptor.javaClass)
            val builder = newBuilderMethod.invoke(null, descriptor)

            val mergeMethod = parser.javaClass.methods
                .firstOrNull { m ->
                    m.name == "merge"
                            && m.parameterCount == 2
                            && m.parameterTypes[0] == String::class.java
                            && m.parameterTypes[1].isAssignableFrom(builder.javaClass)
                }
                ?: parser.javaClass.getMethod("merge", String::class.java, builder.javaClass.superclass)
            mergeMethod.invoke(parser, cleanedJson, builder)

            val buildMethod = builder.javaClass.getMethod("build")
            val message = buildMethod.invoke(builder)

            val toByteArrayMethod = message.javaClass.getMethod("toByteArray")
            toByteArrayMethod.invoke(message) as ByteArray
        } catch (e: Exception) {
            val rootCause = unwrapInvocationTargetException(e)
            LOG.warn("Failed to convert JSON to protobuf: ${rootCause.message}", rootCause)
            throw e
        }
    }

    private fun cleanJsonForProtobuf(json: String): String {
        var cleaned = json.trim()

        val nullPattern = Regex(""""[^"\\]*(?:\\.[^"\\]*)*"\s*:\s*null\s*(,?)""")
        while (nullPattern.containsMatchIn(cleaned)) {
            cleaned = nullPattern.replace(cleaned) { matchResult ->
                if (matchResult.groupValues[1] == ",") "" else ""
            }
            cleaned = cleaned.replace(Regex(""",\s*}"""), "}")
            cleaned = cleaned.replace(Regex(""",\s*]"""), "]")
        }

        return cleaned
    }

    private fun protobufToJson(
        classLoader: ClassLoader,
        descriptor: Any,
        bytes: ByteArray
    ): String {
        return try {
            val dynamicMessageClass = classLoader.loadClass("com.google.protobuf.DynamicMessage")
            val jsonFormatClass = classLoader.loadClass("com.google.protobuf.util.JsonFormat")

            val parseFromMethod =
                dynamicMessageClass.getMethod("parseFrom", descriptor.javaClass, ByteArray::class.java)
            val message = parseFromMethod.invoke(null, descriptor, bytes)

            val printerMethod = jsonFormatClass.getMethod("printer")
            val printer = printerMethod.invoke(null)

            val includingDefaultValueFieldsMethod = printer.javaClass.getMethod("includingDefaultValueFields")
            val configuredPrinter = includingDefaultValueFieldsMethod.invoke(printer)

            val printMethod = configuredPrinter.javaClass.methods
                .first { m ->
                    m.name == "print"
                            && m.parameterCount == 1
                            && m.parameterTypes[0].isAssignableFrom(message.javaClass)
                }
            printMethod.invoke(configuredPrinter, message) as String
        } catch (e: Exception) {
            LOG.warn("Failed to convert protobuf to JSON: ${e.message}", e)
            throw e
        }
    }

    private fun callUnaryMethod(
        classLoader: ClassLoader,
        channel: Any,
        serviceName: String,
        methodName: String,
        inputDescriptor: Any,
        outputDescriptor: Any,
        requestBytes: ByteArray
    ): ByteArray {
        return try {
            val methodDescriptorClass = classLoader.loadClass("io.grpc.MethodDescriptor")
            val methodTypeClass = classLoader.loadClass("io.grpc.MethodDescriptor\$MethodType")
            val marshallerClass = classLoader.loadClass("io.grpc.MethodDescriptor\$Marshaller")

            val inputMarshaller = createProtobufMarshaller(classLoader, inputDescriptor)
            val outputMarshaller = createProtobufMarshaller(classLoader, outputDescriptor)

            val unaryType = methodTypeClass.getField("UNARY").get(null)

            val newBuilderMethod = methodDescriptorClass.getMethod("newBuilder", marshallerClass, marshallerClass)
            val mdBuilder = newBuilderMethod.invoke(null, inputMarshaller, outputMarshaller)

            val setTypeMethod = mdBuilder.javaClass.getMethod("setType", methodTypeClass)
            setTypeMethod.invoke(mdBuilder, unaryType)

            val fullMethodName = "$serviceName/$methodName"
            val setFullMethodNameMethod = mdBuilder.javaClass.getMethod("setFullMethodName", String::class.java)
            setFullMethodNameMethod.invoke(mdBuilder, fullMethodName)

            val buildMdMethod = mdBuilder.javaClass.getMethod("build")
            val methodDescriptor = buildMdMethod.invoke(mdBuilder)

            val callOptionsClass = classLoader.loadClass("io.grpc.CallOptions")
            val defaultCallOptions = callOptionsClass.getField("DEFAULT").get(null)

            val clientCallsClass = classLoader.loadClass("io.grpc.stub.ClientCalls")
            val blockingUnaryCallMethod = clientCallsClass.getMethod(
                "blockingUnaryCall",
                classLoader.loadClass("io.grpc.Channel"),
                methodDescriptorClass,
                callOptionsClass,
                Any::class.java
            )

            val dynamicMessageClass = classLoader.loadClass("com.google.protobuf.DynamicMessage")
            val parseFromMethod =
                dynamicMessageClass.getMethod("parseFrom", inputDescriptor.javaClass, ByteArray::class.java)
            val requestMessage = parseFromMethod.invoke(null, inputDescriptor, requestBytes)

            LOG.info("Invoking blockingUnaryCall for $fullMethodName")
            val responseMessage = blockingUnaryCallMethod.invoke(
                null,
                channel,
                methodDescriptor,
                defaultCallOptions,
                requestMessage
            )

            val toByteArrayMethod = responseMessage.javaClass.getMethod("toByteArray")
            toByteArrayMethod.invoke(responseMessage) as ByteArray
        } catch (e: Exception) {
            val rootCause = unwrapInvocationTargetException(e)
            LOG.warn("Failed to call unary method $serviceName/$methodName: ${rootCause.message ?: rootCause.javaClass.simpleName}", rootCause)
            throw e
        }
    }

    private fun createProtobufMarshaller(classLoader: ClassLoader, descriptor: Any): Any {
        val marshallerInterface = classLoader.loadClass("io.grpc.MethodDescriptor\$Marshaller")
        val dynamicMessageClass = classLoader.loadClass("com.google.protobuf.DynamicMessage")

        return Proxy.newProxyInstance(
            classLoader,
            arrayOf(marshallerInterface)
        ) { _, method, args ->
            when (method.name) {
                "stream" -> {
                    val msg = args?.get(0) ?: return@newProxyInstance ByteArrayInputStream(ByteArray(0))
                    val toByteArrayMethod = msg.javaClass.getMethod("toByteArray")
                    val bytes = toByteArrayMethod.invoke(msg) as ByteArray
                    ByteArrayInputStream(bytes)
                }

                "parse" -> {
                    val stream = args?.get(0) as? InputStream ?: return@newProxyInstance null
                    val bytes = stream.readBytes()
                    val parseFromMethod =
                        dynamicMessageClass.getMethod("parseFrom", descriptor.javaClass, ByteArray::class.java)
                    parseFromMethod.invoke(null, descriptor, bytes)
                }

                else -> null
            }
        }
    }

    private fun parseHostPort(host: String): Pair<String, Int> {
        val cleaned = host.removePrefix("http://").removePrefix("https://")
        val colonIdx = cleaned.lastIndexOf(':')
        return if (colonIdx > 0) {
            val addr = cleaned.substring(0, colonIdx)
            val port = cleaned.substring(colonIdx + 1).toIntOrNull() ?: 50051
            addr to port
        } else {
            cleaned to 50051
        }
    }

    private fun extractStatusAndMessage(classLoader: ClassLoader, e: Throwable): Triple<Int?, String?, String> {
        try {
            val statusRuntimeExceptionClass = classLoader.loadClass("io.grpc.StatusRuntimeException")
            if (statusRuntimeExceptionClass.isInstance(e)) {
                val getStatusMethod = statusRuntimeExceptionClass.getMethod("getStatus")
                val status = getStatusMethod.invoke(e)
                
                val statusClass = classLoader.loadClass("io.grpc.Status")
                val getCodeMethod = statusClass.getMethod("getCode")
                val getDescriptionMethod = statusClass.getMethod("getDescription")
                
                val codeObj = getCodeMethod.invoke(status)
                val description = getDescriptionMethod.invoke(status) as? String
                
                val codeValueMethod = codeObj?.javaClass?.getMethod("value")
                val statusCode = codeValueMethod?.invoke(codeObj) as? Int
                val statusName = codeObj?.toString()
                
                val errorMessage = if (!description.isNullOrBlank()) {
                    "$statusName: $description"
                } else {
                    statusName ?: e.message ?: e.javaClass.simpleName
                }
                
                return Triple(statusCode, statusName, errorMessage)
            }
        } catch (_: Exception) {
            // Fall through to default handling
        }
        
        return Triple(null, null, e.message ?: e.javaClass.simpleName)
    }

    private fun unwrapInvocationTargetException(e: Throwable): Throwable {
        var current: Throwable = e
        while (current is java.lang.reflect.InvocationTargetException && current.cause != null) {
            current = current.cause!!
        }
        return current
    }
}

/**
 * A parent-last (child-first) classloader that prefers classes from the child URLs
 * over the parent classloader. This ensures that the resolved gRPC JARs take
 * precedence over any gRPC classes that might be in the IDE's classpath.
 */
private class ParentLastClassLoader(
    urls: Array<java.net.URL>,
    parent: ClassLoader
) : ClassLoader(parent) {

    private val childClassLoader: URLClassLoader = URLClassLoader(urls, null)

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // First, try to load from child (our JARs)
        try {
            val loadedClass = childClassLoader.loadClass(name)
            if (resolve) {
                resolveClass(loadedClass)
            }
            return loadedClass
        } catch (_: ClassNotFoundException) {
            // Not found in child, delegate to parent
        }

        // Fall back to parent
        return super.loadClass(name, resolve)
    }
}
