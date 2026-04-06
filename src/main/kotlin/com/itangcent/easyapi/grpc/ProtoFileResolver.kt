package com.itangcent.easyapi.grpc

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.itangcent.easyapi.logging.IdeaLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves gRPC method descriptors by scanning the IntelliJ project VFS for `.proto` files,
 * parsing them with a lightweight text parser, and building `FileDescriptor` objects locally.
 */
class ProtoFileResolver(val project: Project) : DescriptorResolver, IdeaLog {

    private val cache = ConcurrentHashMap<String, Pair<Long, Any>>()

    override suspend fun resolve(
        classLoader: ClassLoader,
        serviceName: String,
        methodName: String,
        channel: Any?,
        sourceMethod: com.intellij.psi.PsiMethod?
    ): ResolvedDescriptor? {
        return try {
            LOG.info("ProtoFileResolver.resolve: serviceName=$serviceName, methodName=$methodName")
            val protoFiles = findProtoFiles()
            if (protoFiles.isEmpty()) {
                LOG.info("No proto files found in project")
                return null
            }

            for (file in protoFiles) {
                LOG.info("Trying proto file: ${file.path}")
                val fileDescriptor = buildFileDescriptorWithDeps(classLoader, file, protoFiles)
                if (fileDescriptor == null) {
                    LOG.info("  Failed to build file descriptor for ${file.path}")
                    continue
                }
                LOG.info("  Successfully built file descriptor for ${file.path}")
                val result = extractMethodDescriptors(fileDescriptor, serviceName, methodName)
                if (result != null) {
                    LOG.info("Found method descriptor in ${file.path}")
                    return result
                }
            }
            LOG.info("Method not found in any proto file")
            null
        } catch (e: Exception) {
            LOG.warn("ProtoFileResolver.resolve failed: ${e.message}")
            null
        }
    }

    internal fun findProtoFiles(): List<VirtualFile> {
        return try {
            val result = mutableListOf<VirtualFile>()
            val contentRoots = ProjectRootManager.getInstance(project).contentRoots
            LOG.info("Searching for proto files in ${contentRoots.size} content roots")
            for (root in contentRoots) {
                LOG.info("  Searching content root: ${root.path}")
                collectProtoFiles(root, result)
            }
            LOG.info("Found ${result.size} proto files total")
            result.forEach { LOG.info("  Proto file: ${it.path}") }
            result
        } catch (e: Exception) {
            LOG.warn("findProtoFiles failed: ${e.message}")
            emptyList()
        }
    }

    private fun collectProtoFiles(dir: VirtualFile, result: MutableList<VirtualFile>) {
        if (!dir.isValid) return
        if (dir.isDirectory) {
            for (child in dir.children) {
                collectProtoFiles(child, result)
            }
        } else if (dir.name.endsWith(".proto")) {
            result.add(dir)
        }
    }

    internal fun buildFileDescriptorWithDeps(
        classLoader: ClassLoader,
        targetFile: VirtualFile,
        allProtoFiles: List<VirtualFile>
    ): Any? {
        return try {
            val stamp = targetFile.modificationStamp
            val cached = cache[targetFile.path]
            if (cached != null && cached.first == stamp) {
                return cached.second
            }

            val text = String(targetFile.contentsToByteArray(), Charsets.UTF_8)
            val parseResult = ProtoUtils.parseProtoText(text)

            val deps = mutableListOf<Any>()
            for (importPath in parseResult.imports) {
                if (importPath.startsWith("google/protobuf/")) {
                    val builtInDescriptor = getBuiltInDescriptor(classLoader, importPath)
                    if (builtInDescriptor != null) {
                        deps.add(builtInDescriptor)
                        LOG.info("Using built-in descriptor for: $importPath")
                    } else {
                        LOG.info("No built-in descriptor for: $importPath")
                    }
                    continue
                }
                
                val depFile = allProtoFiles.find { it.path.endsWith(importPath) || it.path.endsWith("/$importPath") }
                if (depFile != null) {
                    val depDescriptor = buildFileDescriptorWithDeps(classLoader, depFile, allProtoFiles)
                    if (depDescriptor != null) {
                        deps.add(depDescriptor)
                    } else {
                        LOG.info("Could not resolve import '$importPath' for ${targetFile.path}")
                    }
                } else {
                    if (importPath.startsWith("google/")) {
                        LOG.info("Skipping Google proto import (not in project): $importPath")
                    } else {
                        LOG.info("Import '$importPath' not found in project for ${targetFile.path}")
                    }
                }
            }

            val fileDescriptorProto = buildFileDescriptorProto(classLoader, parseResult, targetFile.name)
                ?: return null

            val descriptorsClass = classLoader.loadClass("com.google.protobuf.Descriptors")
            val fileDescriptorClass = descriptorsClass.classes.find { it.simpleName == "FileDescriptor" }
                ?: classLoader.loadClass("com.google.protobuf.Descriptors\$FileDescriptor")

            val depsArray = java.lang.reflect.Array.newInstance(fileDescriptorClass, deps.size) as Array<Any?>
            deps.forEachIndexed { i, d -> depsArray[i] = d }

            val buildFromMethod = fileDescriptorClass.getMethod(
                "buildFrom",
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$FileDescriptorProto"),
                depsArray.javaClass
            )
            val fileDescriptor = buildFromMethod.invoke(null, fileDescriptorProto, depsArray)
                ?: return null

            cache[targetFile.path] = Pair(stamp, fileDescriptor)
            fileDescriptor
        } catch (e: Exception) {
            val rootCause = unwrapInvocationTargetException(e)
            if (rootCause.message != null) {
                LOG.info("buildFileDescriptorWithDeps failed for ${targetFile.path}: ${rootCause.message}")
            } else {
                LOG.info("buildFileDescriptorWithDeps failed for ${targetFile.path}: ${rootCause.javaClass.simpleName}")
                LOG.info("Stack trace:", rootCause)
            }
            null
        }
    }

    internal fun extractMethodDescriptors(
        fileDescriptor: Any,
        serviceName: String,
        methodName: String
    ): ResolvedDescriptor? {
        return try {
            val getServicesMethod = fileDescriptor.javaClass.getMethod("getServices")

            @Suppress("UNCHECKED_CAST")
            val services = getServicesMethod.invoke(fileDescriptor) as List<Any>
            
            LOG.info("File descriptor has ${services.size} services, looking for $serviceName")

            for (service in services) {
                val fullName = service.javaClass.getMethod("getFullName").invoke(service) as String
                val simpleName = service.javaClass.getMethod("getName").invoke(service) as String
                LOG.info("  Found service: fullName='$fullName' (len=${fullName.length}), looking for '$serviceName' (len=${serviceName.length})")
                LOG.info("  Comparison result: ${fullName == serviceName}, equalsIgnoreCase: ${fullName.equals(serviceName, ignoreCase = true)}")
                
                if (fullName == serviceName) {
                    LOG.info("  Service matched! Looking for method: $methodName")
                    val getMethodsMethod = service.javaClass.getMethod("getMethods")

                    @Suppress("UNCHECKED_CAST")
                    val methods = getMethodsMethod.invoke(service) as List<Any>
                    LOG.info("  Service has ${methods.size} methods")

                    for (method in methods) {
                        val name = method.javaClass.getMethod("getName").invoke(method) as String
                        LOG.info("    Method: $name")
                        if (name == methodName || name.equals(methodName, ignoreCase = true)) {
                            LOG.info("    Method matched! Actual name: $name")
                            val inputDescriptor = method.javaClass.getMethod("getInputType").invoke(method)
                            val outputDescriptor = method.javaClass.getMethod("getOutputType").invoke(method)
                            return ResolvedDescriptor(
                                inputDescriptor = inputDescriptor!!,
                                outputDescriptor = outputDescriptor!!,
                                source = DescriptorSource.PROTO_FILE,
                                actualMethodName = name  // Return the actual method name from proto
                            )
                        }
                    }
                    LOG.info("  Method $methodName not found in service")
                }
            }
            null
        } catch (e: Exception) {
            LOG.info("extractMethodDescriptors failed: ${e.message}")
            null
        }
    }

    fun invalidateCache() {
        cache.clear()
    }

    internal fun buildFileDescriptorFromParseResult(
        classLoader: ClassLoader,
        parsed: ProtoParseResult,
        fileName: String
    ): Any? {
        return try {
            val fileDescriptorProto = buildFileDescriptorProto(classLoader, parsed, fileName)
                ?: return null

            val descriptorsClass = classLoader.loadClass("com.google.protobuf.Descriptors")
            val fileDescriptorClass = descriptorsClass.classes.find { it.simpleName == "FileDescriptor" }
                ?: classLoader.loadClass("com.google.protobuf.Descriptors\$FileDescriptor")

            @Suppress("UNCHECKED_CAST")
            val depsArray = java.lang.reflect.Array.newInstance(fileDescriptorClass, 0) as Array<Any?>

            val buildFromMethod = fileDescriptorClass.getMethod(
                "buildFrom",
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$FileDescriptorProto"),
                depsArray.javaClass
            )
            buildFromMethod.invoke(null, fileDescriptorProto, depsArray)
        } catch (e: Exception) {
            LOG.info("buildFileDescriptorFromParseResult failed: ${e.message}")
            null
        }
    }

    private fun buildFileDescriptorProto(
        classLoader: ClassLoader,
        parsed: ProtoParseResult,
        fileName: String
    ): Any? {
        return try {
            val fdpClass = classLoader.loadClass("com.google.protobuf.DescriptorProtos\$FileDescriptorProto")
            val newBuilderMethod = fdpClass.getMethod("newBuilder")
            val builder = newBuilderMethod.invoke(null)
            val builderClass = builder.javaClass

            builderClass.getMethod("setName", String::class.java).invoke(builder, fileName)

            if (parsed.packageName.isNotEmpty()) {
                builderClass.getMethod("setPackage", String::class.java).invoke(builder, parsed.packageName)
            }

            val addDependencyMethod = builderClass.getMethod("addDependency", String::class.java)
            for (imp in parsed.imports) {
                addDependencyMethod.invoke(builder, imp)
            }

            val descriptorProtoClass = classLoader.loadClass("com.google.protobuf.DescriptorProtos\$DescriptorProto")
            val addMessageTypeMethod = builderClass.getMethod("addMessageType", descriptorProtoClass)
            for (msg in parsed.messages) {
                val msgProto = buildDescriptorProto(classLoader, msg, parsed) ?: continue
                addMessageTypeMethod.invoke(builder, msgProto)
            }

            val enumDescriptorProtoClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$EnumDescriptorProto")
            val addEnumTypeMethod = builderClass.getMethod("addEnumType", enumDescriptorProtoClass)
            for (enumDef in parsed.enums) {
                val enumProto = buildEnumDescriptorProto(classLoader, enumDef) ?: continue
                addEnumTypeMethod.invoke(builder, enumProto)
            }

            val serviceDescriptorProtoClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$ServiceDescriptorProto")
            val addServiceMethod = builderClass.getMethod("addService", serviceDescriptorProtoClass)
            for (svc in parsed.services) {
                val svcProto = buildServiceDescriptorProto(classLoader, svc, parsed.packageName) ?: continue
                addServiceMethod.invoke(builder, svcProto)
            }

            builderClass.getMethod("build").invoke(builder)
        } catch (e: Exception) {
            LOG.info("buildFileDescriptorProto failed: ${e.message}")
            null
        }
    }

    private fun buildDescriptorProto(classLoader: ClassLoader, msg: MessageDef, parsed: ProtoParseResult): Any? {
        return try {
            val dpClass = classLoader.loadClass("com.google.protobuf.DescriptorProtos\$DescriptorProto")
            val builder = dpClass.getMethod("newBuilder").invoke(null)
            val builderClass = builder.javaClass

            val simpleName = msg.name.substringAfterLast('.')
            builderClass.getMethod("setName", String::class.java).invoke(builder, simpleName)

            val fieldDescriptorProtoClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$FieldDescriptorProto")
            val addFieldMethod = builderClass.getMethod("addField", fieldDescriptorProtoClass)

            for (field in msg.fields) {
                val fieldProto = buildFieldDescriptorProto(classLoader, field, parsed, msg) ?: continue
                addFieldMethod.invoke(builder, fieldProto)
            }

            val nestedTypeMethod = builderClass.getMethod("addNestedType", dpClass)
            for (nestedMsg in msg.nestedMessages) {
                val nestedProto = buildDescriptorProto(classLoader, nestedMsg, parsed) ?: continue
                nestedTypeMethod.invoke(builder, nestedProto)
            }

            val enumDescriptorProtoClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$EnumDescriptorProto")
            val addEnumTypeMethod = builderClass.getMethod("addEnumType", enumDescriptorProtoClass)
            for (nestedEnum in msg.nestedEnums) {
                val enumProto = buildEnumDescriptorProto(classLoader, nestedEnum) ?: continue
                addEnumTypeMethod.invoke(builder, enumProto)
            }

            builderClass.getMethod("build").invoke(builder)
        } catch (e: Exception) {
            LOG.info("buildDescriptorProto failed for ${msg.name}: ${e.message}")
            null
        }
    }

    private fun buildEnumDescriptorProto(classLoader: ClassLoader, enumDef: EnumDef): Any? {
        return try {
            val edpClass = classLoader.loadClass("com.google.protobuf.DescriptorProtos\$EnumDescriptorProto")
            val builder = edpClass.getMethod("newBuilder").invoke(null)
            val builderClass = builder.javaClass

            val simpleName = enumDef.name.substringAfterLast('.')
            builderClass.getMethod("setName", String::class.java).invoke(builder, simpleName)

            val enumValueDescriptorProtoClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$EnumValueDescriptorProto")
            val addValueMethod = builderClass.getMethod("addValue", enumValueDescriptorProtoClass)

            for (value in enumDef.values) {
                val valueProto = buildEnumValueDescriptorProto(classLoader, value) ?: continue
                addValueMethod.invoke(builder, valueProto)
            }

            builderClass.getMethod("build").invoke(builder)
        } catch (e: Exception) {
            LOG.info("buildEnumDescriptorProto failed for ${enumDef.name}: ${e.message}")
            null
        }
    }

    private fun buildEnumValueDescriptorProto(classLoader: ClassLoader, value: EnumValueDef): Any? {
        return try {
            val evdpClass = classLoader.loadClass("com.google.protobuf.DescriptorProtos\$EnumValueDescriptorProto")
            val builder = evdpClass.getMethod("newBuilder").invoke(null)
            val builderClass = builder.javaClass

            builderClass.getMethod("setName", String::class.java).invoke(builder, value.name)
            builderClass.getMethod("setNumber", Int::class.java).invoke(builder, value.number)

            builderClass.getMethod("build").invoke(builder)
        } catch (e: Exception) {
            LOG.info("buildEnumValueDescriptorProto failed for ${value.name}: ${e.message}")
            null
        }
    }

    private fun buildFieldDescriptorProto(
        classLoader: ClassLoader,
        field: FieldDef,
        parsed: ProtoParseResult,
        parentMessage: MessageDef? = null
    ): Any? {
        return try {
            val fdpClass = classLoader.loadClass("com.google.protobuf.DescriptorProtos\$FieldDescriptorProto")
            val builder = fdpClass.getMethod("newBuilder").invoke(null)
            val builderClass = builder.javaClass

            builderClass.getMethod("setName", String::class.java).invoke(builder, field.name)
            builderClass.getMethod("setNumber", Int::class.java).invoke(builder, field.number)

            val labelEnumClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$FieldDescriptorProto\$Label")
            val labelValue = when (field.label.lowercase()) {
                "repeated" -> labelEnumClass.getField("LABEL_REPEATED").get(null)
                "required" -> labelEnumClass.getField("LABEL_REQUIRED").get(null)
                else -> labelEnumClass.getField("LABEL_OPTIONAL").get(null)
            }
            builderClass.getMethod("setLabel", labelEnumClass).invoke(builder, labelValue)

            val typeEnumClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$FieldDescriptorProto\$Type")
            val protoType = ProtoUtils.mapProtoType(field.type)
            if (protoType != null) {
                val typeValue = typeEnumClass.getField(protoType).get(null)
                builderClass.getMethod("setType", typeEnumClass).invoke(builder, typeValue)
            } else {
                val qualifiedTypeName = resolveTypeName(field.type, parsed, parentMessage)
                builderClass.getMethod("setTypeName", String::class.java).invoke(builder, qualifiedTypeName)
            }

            builderClass.getMethod("build").invoke(builder)
        } catch (e: Exception) {
            LOG.info("buildFieldDescriptorProto failed for ${field.name}: ${e.message}")
            null
        }
    }

    private fun resolveTypeName(typeName: String, parsed: ProtoParseResult, parentMessage: MessageDef?): String {
        if (typeName.startsWith(".")) return typeName

        if (typeName.contains(".")) {
            val parts = typeName.split(".")
            if (parts.size == 2 && parentMessage != null) {
                val parentName = parts[0]
                val nestedName = parts[1]
                val parentSimpleName = parentMessage.name.substringAfterLast('.')
                
                if (parentName == parentSimpleName || parentName == parentMessage.name) {
                    val isNestedMessage = parentMessage.nestedMessages.any { 
                        it.name == nestedName || it.name.endsWith(".$nestedName") 
                    }
                    val isNestedEnum = parentMessage.nestedEnums.any { 
                        it.name == nestedName || it.name.endsWith(".$nestedName") 
                    }
                    
                    if (isNestedMessage || isNestedEnum) {
                        return nestedName
                    }
                }
            }
            
            return ProtoUtils.qualifyTypeName(typeName, parsed.packageName)
        }

        val isTopLevelEnum = parsed.enums.any { it.name == typeName }
        if (isTopLevelEnum) {
            return ProtoUtils.qualifyTypeName(typeName, parsed.packageName)
        }

        val isTopLevelMessage = parsed.messages.any { it.name == typeName }
        if (isTopLevelMessage) {
            return ProtoUtils.qualifyTypeName(typeName, parsed.packageName)
        }

        if (parentMessage != null) {
            val isNestedMessage = parentMessage.nestedMessages.any { 
                it.name == typeName || it.name.endsWith(".$typeName") 
            }
            val isNestedEnum = parentMessage.nestedEnums.any { 
                it.name == typeName || it.name.endsWith(".$typeName") 
            }
            
            if (isNestedMessage || isNestedEnum) {
                return typeName
            }
            
            val parentMessageName = parentMessage.name.substringAfterLast('.')
            return ".${parsed.packageName}.$parentMessageName.$typeName"
        }

        return ProtoUtils.qualifyTypeName(typeName, parsed.packageName)
    }

    private fun buildServiceDescriptorProto(
        classLoader: ClassLoader,
        svc: ServiceDef,
        packageName: String
    ): Any? {
        return try {
            val sdpClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$ServiceDescriptorProto")
            val builder = sdpClass.getMethod("newBuilder").invoke(null)
            val builderClass = builder.javaClass

            builderClass.getMethod("setName", String::class.java).invoke(builder, svc.name)

            val methodDescriptorProtoClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$MethodDescriptorProto")
            val addMethodMethod = builderClass.getMethod("addMethod", methodDescriptorProtoClass)

            for (rpc in svc.methods) {
                val methodProto = buildMethodDescriptorProto(classLoader, rpc, packageName) ?: continue
                addMethodMethod.invoke(builder, methodProto)
            }

            builderClass.getMethod("build").invoke(builder)
        } catch (e: Exception) {
            LOG.info("buildServiceDescriptorProto failed for ${svc.name}: ${e.message}")
            null
        }
    }

    private fun buildMethodDescriptorProto(
        classLoader: ClassLoader,
        rpc: RpcDef,
        packageName: String
    ): Any? {
        return try {
            val mdpClass =
                classLoader.loadClass("com.google.protobuf.DescriptorProtos\$MethodDescriptorProto")
            val builder = mdpClass.getMethod("newBuilder").invoke(null)
            val builderClass = builder.javaClass

            builderClass.getMethod("setName", String::class.java).invoke(builder, rpc.name)

            val inputTypeName = ProtoUtils.qualifyTypeName(rpc.inputType, packageName)
            val outputTypeName = ProtoUtils.qualifyTypeName(rpc.outputType, packageName)

            builderClass.getMethod("setInputType", String::class.java).invoke(builder, inputTypeName)
            builderClass.getMethod("setOutputType", String::class.java).invoke(builder, outputTypeName)

            builderClass.getMethod("build").invoke(builder)
        } catch (e: Exception) {
            LOG.info("buildMethodDescriptorProto failed for ${rpc.name}: ${e.message}")
            null
        }
    }

    private fun getBuiltInDescriptor(classLoader: ClassLoader, importPath: String): Any? {
        return try {
            val protoFileName = importPath.substringAfterLast("/")
            val descriptorClassName = when (protoFileName) {
                "descriptor.proto" -> "com.google.protobuf.DescriptorProtos"
                "any.proto" -> "com.google.protobuf.AnyProto"
                "api.proto" -> "com.google.protobuf.ApiProto"
                "duration.proto" -> "com.google.protobuf.DurationProto"
                "empty.proto" -> "com.google.protobuf.EmptyProto"
                "field_mask.proto" -> "com.google.protobuf.FieldMaskProto"
                "source_context.proto" -> "com.google.protobuf.SourceContextProto"
                "struct.proto" -> "com.google.protobuf.StructProto"
                "timestamp.proto" -> "com.google.protobuf.TimestampProto"
                "type.proto" -> "com.google.protobuf.TypeProto"
                "wrappers.proto" -> "com.google.protobuf.WrappersProto"
                else -> null
            }
            
            if (descriptorClassName == null) {
                return null
            }
            
            val descriptorClass = classLoader.loadClass(descriptorClassName)
            val fileDescriptorField = descriptorClass.getDeclaredField("descriptor")
            fileDescriptorField.isAccessible = true
            val descriptor = fileDescriptorField.get(null)
            
            val descriptorsClass = classLoader.loadClass("com.google.protobuf.Descriptors")
            val fileDescriptorClass = descriptorsClass.classes.find { it.simpleName == "FileDescriptor" }
                ?: classLoader.loadClass("com.google.protobuf.Descriptors\$FileDescriptor")
            
            if (fileDescriptorClass.isInstance(descriptor)) {
                descriptor
            } else {
                null
            }
        } catch (e: Exception) {
            LOG.info("getBuiltInDescriptor failed for $importPath: ${e.message}")
            null
        }
    }

    private fun unwrapInvocationTargetException(e: Throwable): Throwable {
        var cause = e
        while (cause is java.lang.reflect.InvocationTargetException) {
            cause = cause.targetException ?: cause.cause ?: cause
        }
        return cause
    }
}
