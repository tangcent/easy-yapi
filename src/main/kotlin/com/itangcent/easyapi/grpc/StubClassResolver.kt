package com.itangcent.easyapi.grpc

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTypesUtil
import com.itangcent.easyapi.core.threading.read
import com.itangcent.easyapi.logging.IdeaLog

/**
 * Resolves gRPC method descriptors by analyzing generated stub classes via PSI.
 *
 * This resolver inspects the user's gRPC service implementation classes using
 * IntelliJ's PSI (Program Structure Interface) to extract message type information:
 *
 * 1. Derives the ImplBase class name from the service name (e.g., `AuditQueryServiceGrpc.AuditQueryServiceImplBase`)
 * 2. Finds the concrete implementation class that extends ImplBase in the project
 * 3. Reads method parameter types directly from the PSI method signature
 * 4. Walks the message PsiClasses to extract field metadata from `*_FIELD_NUMBER` constants
 * 5. Builds a FileDescriptorProto programmatically (delegating to [ProtoFileResolver])
 *
 * This approach requires no class loading or code execution - purely static PSI analysis.
 *
 * @see DescriptorResolver
 * @see ProtoFileResolver
 * @see CompositeDescriptorResolver
 */
class StubClassResolver(val project: Project) : DescriptorResolver, IdeaLog {

    override suspend fun resolve(
        classLoader: ClassLoader,
        serviceName: String,
        methodName: String,
        channel: Any?,
        sourceMethod: com.intellij.psi.PsiMethod?
    ): ResolvedDescriptor? {
        return try {
            LOG.info("StubClassResolver resolving $serviceName/$methodName, sourceMethod=${sourceMethod != null}")
            
            var actualMethodName = methodName
            val (inputClass, outputClass) = if (sourceMethod != null) {
                LOG.info("Using sourceMethod for message type extraction")
                actualMethodName = sourceMethod.name
                extractMessageTypes(sourceMethod) ?: run {
                    LOG.info("Failed to extract message types from sourceMethod")
                    return null
                }
            } else {
                LOG.info("Finding ImplBase class for service")
                val simpleServiceName = serviceName.substringAfterLast('.')
                val packageName = serviceName.substringBeforeLast('.', "")
                val implBaseClass = findImplBase(simpleServiceName, packageName) ?: run {
                    LOG.info("ImplBase class not found for $simpleServiceName")
                    return null
                }
                LOG.info("Found ImplBase: ${implBaseClass.qualifiedName}")
                val implClass = findImplClass(implBaseClass) ?: run {
                    LOG.info("Implementation class not found")
                    return null
                }
                LOG.info("Found impl class: ${implClass.qualifiedName}")
                val psiMethod = implClass.findMethodsByName(methodName, true)
                    .firstOrNull { isRpcMethod(it) }
                    ?: implClass.findMethodsByName(methodName, true)
                        .firstOrNull { isRpcMethod(it.name, methodName) }
                    ?: implClass.methods.firstOrNull { isRpcMethod(it) && it.name.equals(methodName, ignoreCase = true) }
                if (psiMethod == null) {
                    LOG.info("Method $methodName not found in ${implClass.qualifiedName}")
                    LOG.info("Available methods: ${implClass.methods.filter { isRpcMethod(it) }.map { it.name }}")
                    return null
                }
                LOG.info("Found method: ${psiMethod.name}")
                actualMethodName = psiMethod.name
                extractMessageTypes(psiMethod) ?: run {
                    LOG.info("Failed to extract message types from psiMethod")
                    return null
                }
            }

            val simpleServiceName = serviceName.substringAfterLast('.')
            val packageName = serviceName.substringBeforeLast('.', "")

            val allMessages = mutableListOf<MessageDef>()
            collectMessageDefs(inputClass, allMessages, mutableSetOf())
            collectMessageDefs(outputClass, allMessages, mutableSetOf())

            val rpcDef = RpcDef(
                name = actualMethodName,
                inputType = inputClass.name ?: return null,
                outputType = outputClass.name ?: return null
            )
            val serviceDef = ServiceDef(simpleServiceName, listOf(rpcDef))
            val parseResult = ProtoParseResult(
                packageName = packageName,
                imports = emptyList(),
                services = listOf(serviceDef),
                messages = allMessages
            )

            val protoFileResolver = ProtoFileResolver(project)
            val fileDescriptor = protoFileResolver.buildFileDescriptorFromParseResult(
                classLoader, parseResult, "$simpleServiceName.proto"
            ) ?: return null

            protoFileResolver.extractMethodDescriptors(fileDescriptor, serviceName, actualMethodName)
                ?.copy(source = DescriptorSource.STUB_CLASS, actualMethodName = actualMethodName)
        } catch (e: Exception) {
            LOG.info("StubClassResolver.resolve failed for $serviceName/$methodName: ${e.message}")
            null
        }
    }

    internal fun findImplBase(simpleServiceName: String, packageName: String): PsiClass? {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.allScope(project)

        val fqn = if (packageName.isNotEmpty()) "$packageName.${simpleServiceName}Grpc" else "${simpleServiceName}Grpc"
        val grpcOuter = facade.findClass(fqn, scope)
            ?: facade.findClass("${simpleServiceName}Grpc", scope)
            ?: return null

        return grpcOuter.innerClasses.find { it.name == "${simpleServiceName}ImplBase" }
    }

    internal fun findImplClass(implBase: PsiClass): PsiClass? {
        return ClassInheritorsSearch.search(implBase, GlobalSearchScope.allScope(project), false)
            .firstOrNull()
    }

    internal suspend fun extractMessageTypes(method: PsiMethod): Pair<PsiClass, PsiClass>? = read {
        val params = method.parameterList.parameters

        val (inputClass, outputClass) = when {
            params.size == 2 && !params[0].type.canonicalText.contains("StreamObserver") -> {
                extractUnaryOrServerStreamingTypes(params)
            }

            params.size == 1 && params[0].type.canonicalText.contains("StreamObserver") -> {
                extractClientOrBidiStreamingTypes(method, params)
            }

            else -> return@read null
        }

        return@read if (inputClass != null && outputClass != null) {
            Pair(inputClass, outputClass)
        } else null
    }

    private fun extractUnaryOrServerStreamingTypes(
        params: Array<com.intellij.psi.PsiParameter>
    ): Pair<PsiClass?, PsiClass?> {
        val inputClass = PsiTypesUtil.getPsiClass(params[0].type)
            ?: run {
                val fqn = params[0].type.canonicalText.substringBefore('<')
                JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
            }

        val outputClass = extractTypeFromStreamObserver(params[1].type)
        return Pair(inputClass, outputClass)
    }

    private fun extractClientOrBidiStreamingTypes(
        method: PsiMethod,
        params: Array<com.intellij.psi.PsiParameter>
    ): Pair<PsiClass?, PsiClass?> {
        val inputClass = extractTypeFromStreamObserver(method.returnType)
        val outputClass = extractTypeFromStreamObserver(params[0].type)
        return Pair(inputClass, outputClass)
    }

    private fun extractTypeFromStreamObserver(type: PsiType?): PsiClass? {
        if (type == null) return null

        val classType = type as? com.intellij.psi.PsiClassType
        val typeArgs = classType?.parameters

        return if (typeArgs.isNullOrEmpty()) {
            val canonicalText = type.canonicalText
            val typeArgText = canonicalText.substringAfter('<', "").substringBefore('>', "").trim()
            if (typeArgText.isNotEmpty()) {
                JavaPsiFacade.getInstance(project).findClass(typeArgText, GlobalSearchScope.allScope(project))
            } else null
        } else {
            PsiTypesUtil.getPsiClass(typeArgs[0])
                ?: run {
                    val fqn = typeArgs[0].canonicalText.substringBefore('<')
                    JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
                }
        }
    }

    internal suspend fun buildMessageDef(psiClass: PsiClass): MessageDef? {
        return try {
            val fields = mutableListOf<FieldDef>()
            for (field in psiClass.fields) {
                val fieldName = field.name
                if (!fieldName.endsWith("_FIELD_NUMBER")) continue
                val fieldNumber = extractIntConstant(field) ?: continue
                val protoFieldName = fieldName.removeSuffix("_FIELD_NUMBER").lowercase()
                val getterName = "get" + fieldName.removeSuffix("_FIELD_NUMBER")
                    .split("_")
                    .joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
                val getter = psiClass.findMethodsByName(getterName, false).firstOrNull()
                val protoType = getter?.returnType?.let { mapPsiTypeToProto(it) } ?: "string"
                val label =
                    if (getter?.returnType?.canonicalText?.startsWith("java.util.List") == true) "repeated" else ""
                fields.add(FieldDef(protoFieldName, protoType, fieldNumber, label))
            }
            MessageDef(psiClass.name ?: return null, fields)
        } catch (e: Exception) {
            LOG.info("buildMessageDef failed for ${psiClass.name}: ${e.message}")
            null
        }
    }

    private suspend fun collectMessageDefs(
        psiClass: PsiClass,
        result: MutableList<MessageDef>,
        visited: MutableSet<String>
    ): Unit = read {
        val qualifiedName = psiClass.qualifiedName ?: return@read
        if (!visited.add(qualifiedName)) return@read
        val msgDef = buildMessageDef(psiClass) ?: return@read
        result.add(msgDef)

        for (field in psiClass.fields) {
            if (!field.name.endsWith("_FIELD_NUMBER")) continue
            val getterName = "get" + field.name.removeSuffix("_FIELD_NUMBER")
                .split("_")
                .joinToString("") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }
            val getter = psiClass.findMethodsByName(getterName, false).firstOrNull() ?: continue
            val returnPsiClass = PsiTypesUtil.getPsiClass(getter.returnType ?: continue) ?: continue
            if (isGeneratedMessage(returnPsiClass)) {
                collectMessageDefs(returnPsiClass, result, visited)
            }
        }
    }

    private fun isRpcMethod(method: PsiMethod): Boolean {
        val params = method.parameterList.parameters
        val hasStreamObserverParam = params.any {
            it.type.canonicalText.contains("StreamObserver") ||
                    it.type.canonicalText.contains("io.grpc.stub.StreamObserver")
        }

        val returnsStreamObserver = method.returnType?.canonicalText?.contains("StreamObserver") == true

        return when {
            params.size == 2 && hasStreamObserverParam -> true
            params.size == 1 && hasStreamObserverParam && returnsStreamObserver -> true
            else -> false
        }
    }

    private fun isRpcMethod(actualMethodName: String, targetMethodName: String): Boolean {
        return actualMethodName.equals(targetMethodName, ignoreCase = true)
    }

    private fun isGeneratedMessage(psiClass: PsiClass): Boolean {
        var current: PsiClass? = psiClass
        while (current != null) {
            if (current.qualifiedName == "com.google.protobuf.GeneratedMessageV3") return true
            current = current.superClass
        }
        return false
    }

    private fun extractIntConstant(field: PsiField): Int? {
        return try {
            val initializer = field.initializer ?: return null
            val value = JavaPsiFacade.getInstance(project)
                .constantEvaluationHelper
                .computeConstantExpression(initializer)
            (value as? Number)?.toInt()
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun mapPsiTypeToProto(type: PsiType): String {
        return ProtoUtils.mapJavaTypeToProto(read { type.canonicalText })
    }
}
