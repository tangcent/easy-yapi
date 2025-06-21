package com.itangcent.idea.plugin.api.infer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.itangcent.ai.AIService
import com.itangcent.cache.CacheSwitcher
import com.itangcent.cache.withoutCache
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.utils.AIUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ThreadFlag
import com.itangcent.intellij.extend.isNotActive
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.jvm.PsiResolver
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.jvm.psi.PsiClassUtil 
import java.util.concurrent.ConcurrentHashMap

/**
 * AI-based implementation of MethodInferHelper
 * Uses AI to analyze method code and infer return types
 */
@Singleton
class AIMethodInferHelper : MethodInferHelper {

    companion object : Log() {
        // List of package prefixes to exclude from referenced classes
        private val EXCLUDED_PACKAGE_PREFIXES = listOf(
            "java.lang.",
            "java.util.",
            "kotlin.",
            "kotlin.collections.",
            "kotlin.jvm.",
            "java.time.",     // Exclude Java time classes (LocalDateTime, etc.)
            "org.joda.time.", // Exclude Joda time classes (DateTime, etc.)
            "java.sql.",      // Exclude SQL-related date/time classes
            "java.math."      // Exclude BigDecimal, BigInteger
        )

        // Maximum number of referenced classes to include in the prompt
        private const val MAX_REFERENCED_CLASSES = 30
    }

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var psiClassHelper: PsiClassHelper

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var psiResolver: PsiResolver

    @Inject
    private lateinit var psiInferenceCollector: PsiInferenceCollector

    @Inject
    private lateinit var aiPromptFormatter: AIPromptFormatter

    @Inject
    private lateinit var cacheSwitcher: CacheSwitcher

    @Inject
    private lateinit var aiService: AIService

    // Cache for storing AI inference results to avoid repeated API calls
    private val inferCache: ConcurrentHashMap<String, Any?> = ConcurrentHashMap()

    override fun inferReturn(psiMethod: PsiMethod, option: Int): Any? {
        return inferCache.computeIfAbsent(getCacheKey(psiMethod)) {
            inferReturnInternal(psiMethod, null, null, option)
        }
    }

    override fun inferReturn(psiMethod: PsiMethod, caller: Any?, args: Array<Any?>?, option: Int): Any? {
        // Use cache for methods with caller and args as well
        val cacheKey = getCacheKey(psiMethod, caller, args)
        return inferCache.computeIfAbsent(cacheKey) {
            inferReturnInternal(psiMethod, caller, args, option)
        }
    }

    /**
     * Internal implementation of inferReturn that performs the actual inference
     */
    private fun inferReturnInternal(psiMethod: PsiMethod, caller: Any?, args: Array<Any?>?, option: Int): Any? {
        try {
            if (ThreadFlag.ASYNC.isNotActive()) {
                LOG.warn(
                    "AI inference detected in UI thread (READ/WRITE/SWING/AWT) for method ${psiMethod.name}. " +
                            "This may block the UI and cause poor user experience."
                )
            }
            // 1. Extract method information
            val methodInfo = actionContext.callInReadUI { extractMethodInfo(psiMethod) } ?: return null

            // 2. Call AI service to infer return type
            var inferredReturnInfo = callAIService(methodInfo)

            // 3. If the AI requested additional classes, collect them and try again
            val additionalClassesNeeded = inferredReturnInfo.additionalClassesNeeded
            if (!additionalClassesNeeded.isNullOrEmpty()) {
                logger.trace(
                    "AI requested additional classes: ${
                        additionalClassesNeeded.joinToString(
                            ", "
                        )
                    }"
                )

                // Store the best inference so far
                val bestInferenceResult = inferredReturnInfo

                // Collect information about the requested classes
                val additionalClasses = psiInferenceCollector.collectAdditionalClassInfo(
                    additionalClassesNeeded,
                    psiMethod
                )

                // Only make a second API call if we successfully collected at least one additional class
                if (additionalClasses.isNotEmpty()) {
                    // Create an enhanced method info with the additional classes
                    val enhancedMethodInfo = methodInfo.copy(
                        additionalRequestedClasses = additionalClasses
                    )

                    // Try inference again with the additional class information
                    logger.trace("Retrying inference with additional class information")
                    try {
                        val enhancedInferredReturnInfo = callAIService(enhancedMethodInfo)

                        // If the second inference attempt was successful (has a structure), use it
                        if (enhancedInferredReturnInfo.structure != null) {
                            inferredReturnInfo = enhancedInferredReturnInfo
                        } else {
                            // Otherwise, keep the best inference so far but preserve the additional classes needed
                            inferredReturnInfo = bestInferenceResult
                            logger.trace("Second inference attempt failed to produce a structure, using best inference from first attempt")
                        }
                    } catch (e: Exception) {
                        // If the second inference attempt fails, use the best inference so far
                        logger.traceError("Error in second inference attempt", e)
                        inferredReturnInfo = bestInferenceResult
                    }
                } else {
                    logger.trace("No additional classes were successfully collected, skipping second inference attempt")
                }
            }

            // 4. Convert the inferred type to an actual object
            return createReturnObject(inferredReturnInfo, psiMethod)
        } catch (e: Exception) {
            logger.traceError("Error in AI method inference", e)
            return null
        }
    }

    /**
     * Extracts relevant information from the method for AI analysis
     */
    private fun extractMethodInfo(psiMethod: PsiMethod): MethodInfo {
        val containingClass = psiMethod.containingClass
        val className = containingClass?.qualifiedName ?: "Unknown"
        val methodName = psiMethod.name
        val returnTypeName = psiMethod.returnType?.presentableText ?: "void"

        // Extract method parameters
        val parameters = psiMethod.parameterList.parameters.map { param ->
            ParameterInfo(
                name = param.name,
                type = param.type.presentableText,
                annotations = param.annotations.map { it.qualifiedName ?: it.text }
            )
        }

        // Extract method annotations
        val methodAnnotations = psiMethod.annotations.map { it.qualifiedName ?: it.text }

        // Extract method body if available
        val methodCode = psiMethod.text

        // Extract class context (fields, other methods) if needed
        val classContext = containingClass?.fields?.map { field ->
            FieldInfo(
                name = field.name,
                type = field.type.presentableText
            )
        } ?: emptyList()

        // Collect referenced classes
        val referencedClasses = collectReferencedClasses(psiMethod)

        return MethodInfo(
            className = className,
            methodName = methodName,
            returnTypeName = returnTypeName,
            parameters = parameters,
            methodAnnotations = methodAnnotations,
            methodCode = methodCode,
            classContext = classContext,
            referencedClasses = referencedClasses
        )
    }

    /**
     * Collects information about classes referenced in the method
     */
    private fun collectReferencedClasses(psiMethod: PsiMethod): List<ClassInfo> {
        val referencedClasses = HashSet<PsiClass>()
        val processedClasses = HashSet<String>()

        // Add the return type class
        psiMethod.returnType?.let { returnType ->
            PsiUtil.resolveClassInType(returnType)?.let { psiClass ->
                if (shouldIncludeClass(psiClass)) {
                    referencedClasses.add(psiClass)
                    processedClasses.add(psiClass.qualifiedName ?: psiClass.name ?: "")
                }
            }
        }

        // Add parameter type classes
        psiMethod.parameterList.parameters.forEach { param ->
            PsiUtil.resolveClassInType(param.type)?.let { psiClass ->
                if (shouldIncludeClass(psiClass) && !processedClasses.contains(
                        psiClass.qualifiedName ?: psiClass.name ?: ""
                    )
                ) {
                    referencedClasses.add(psiClass)
                    processedClasses.add(psiClass.qualifiedName ?: psiClass.name ?: "")
                }
            }
        }

        // Add classes referenced in the method body
        psiMethod.body?.let { body ->
            collectReferencedClassesFromElement(body, referencedClasses, processedClasses)
        }

        // Limit the number of classes to avoid overly large prompts
        return referencedClasses.take(MAX_REFERENCED_CLASSES).map { psiClass ->
            ClassInfo(
                name = psiClass.qualifiedName ?: psiClass.name ?: "Unknown",
                fields = psiClass.fields.map { field ->
                    FieldInfo(
                        name = field.name,
                        type = field.type.presentableText,
                        annotations = field.annotations.map { it.qualifiedName ?: it.text }
                    )
                },
                methods = psiClass.methods
                    .filter { it.name != "equals" && it.name != "hashCode" && it.name != "toString" }
                    .take(5) // Limit the number of methods to include
                    .map { method ->
                        MethodSummary(
                            name = method.name,
                            returnType = method.returnType?.presentableText ?: "void",
                            parameters = method.parameterList.parameters.map {
                                "${it.name}: ${it.type.presentableText}"
                            }
                        )
                    }
            )
        }
    }

    /**
     * Recursively collects referenced classes from a PsiElement
     */
    private fun collectReferencedClassesFromElement(
        element: PsiElement,
        referencedClasses: HashSet<PsiClass>,
        processedClasses: HashSet<String>
    ) {
        if (referencedClasses.size >= MAX_REFERENCED_CLASSES) {
            return
        }

        when (element) {
            is PsiReferenceExpression -> {
                val resolved = element.resolve()
                if (resolved is PsiClass && shouldIncludeClass(resolved)) {
                    val qualifiedName = resolved.qualifiedName ?: resolved.name ?: ""
                    if (!processedClasses.contains(qualifiedName)) {
                        referencedClasses.add(resolved)
                        processedClasses.add(qualifiedName)
                    }
                } else if (resolved is PsiField) {
                    PsiUtil.resolveClassInType(resolved.type)?.let { psiClass ->
                        val qualifiedName = psiClass.qualifiedName ?: psiClass.name ?: ""
                        if (shouldIncludeClass(psiClass) && !processedClasses.contains(qualifiedName)) {
                            referencedClasses.add(psiClass)
                            processedClasses.add(qualifiedName)
                        }
                    }
                } else if (resolved is PsiMethod) {
                    resolved.returnType?.let { returnType ->
                        PsiUtil.resolveClassInType(returnType)?.let { psiClass ->
                            val qualifiedName = psiClass.qualifiedName ?: psiClass.name ?: ""
                            if (shouldIncludeClass(psiClass) && !processedClasses.contains(qualifiedName)) {
                                referencedClasses.add(psiClass)
                                processedClasses.add(qualifiedName)
                            }
                        }
                    }
                }
            }

            is PsiLocalVariable -> {
                PsiUtil.resolveClassInType(element.type)?.let { psiClass ->
                    val qualifiedName = psiClass.qualifiedName ?: psiClass.name ?: ""
                    if (shouldIncludeClass(psiClass) && !processedClasses.contains(qualifiedName)) {
                        referencedClasses.add(psiClass)
                        processedClasses.add(qualifiedName)
                    }
                }
            }

            is PsiNewExpression -> {
                element.classReference?.resolve()?.let { resolved ->
                    if (resolved is PsiClass && shouldIncludeClass(resolved)) {
                        val qualifiedName = resolved.qualifiedName ?: resolved.name ?: ""
                        if (!processedClasses.contains(qualifiedName)) {
                            referencedClasses.add(resolved)
                            processedClasses.add(qualifiedName)
                        }
                    }
                }
            }
        }

        // Process children
        element.children.forEach { child ->
            collectReferencedClassesFromElement(child, referencedClasses, processedClasses)
        }
    }

    /**
     * Determines if a class should be included in the referenced classes list
     */
    private fun shouldIncludeClass(psiClass: PsiClass): Boolean {
        val qualifiedName = psiClass.qualifiedName ?: return false

        // Skip primitive types, common Java classes, and library classes
        return EXCLUDED_PACKAGE_PREFIXES.none { qualifiedName.startsWith(it) } &&
                !psiClass.isEnum &&
                !psiClass.isAnnotationType
    }

    /**
     * Calls the AI service to infer the return type
     */
    private fun callAIService(methodInfo: MethodInfo): ReturnTypeInfo {
        // Format the method info as a prompt for the AI
        val prompt = aiPromptFormatter.formatMethodInfoPrompt(methodInfo)

        // Maximum number of retry attempts
        val maxRetries = 3
        var currentRetry = 0
        var lastException: Exception? = null

        while (currentRetry < maxRetries) {
            try {
                // Call the AI API with the system message and prompt
                val aiResponse = if (currentRetry > 0) {
                    cacheSwitcher.withoutCache {
                        AIUtils.getGeneralContent(
                            aiService.sendPrompt(AIPromptFormatter.METHOD_RETURN_TYPE_INFERENCE_MESSAGE, prompt)
                        )
                    }
                } else {
                    AIUtils.getGeneralContent(
                        aiService.sendPrompt(AIPromptFormatter.METHOD_RETURN_TYPE_INFERENCE_MESSAGE, prompt)
                    )
                }

                // Check if the response is valid before parsing
                if (aiResponse.isEmpty()) {
                    logger.warn("Empty AI response received for method ${methodInfo.className}.${methodInfo.methodName}, attempt ${currentRetry + 1}/$maxRetries")
                    currentRetry++
                    continue
                }

                // Try to parse the AI response
                try {
                    return parseAIResponse(aiResponse, methodInfo)
                } catch (e: Exception) {
                    logger.warn("Failed to parse AI response for method ${methodInfo.className}.${methodInfo.methodName}, attempt ${currentRetry + 1}/$maxRetries: ${e.message}")
                    lastException = e
                    currentRetry++
                    continue
                }
            } catch (e: Exception) {
                logger.warn("AI service call failed for method ${methodInfo.className}.${methodInfo.methodName}, attempt ${currentRetry + 1}/$maxRetries: ${e.message}")
                lastException = e
                currentRetry++

                // Add a small delay before retrying to avoid overwhelming the service
                try {
                    Thread.sleep(500L * (currentRetry)) // Exponential backoff: 500ms, 1000ms, 1500ms
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        // If all retries failed, log the error and return a fallback
        logger.error("All $maxRetries attempts to call AI service failed for method ${methodInfo.className}.${methodInfo.methodName}")
        lastException?.let { logger.traceError(it) }
        return createFallbackReturnInfo(methodInfo)
    }

    /**
     * Formats the method information as a prompt for the AI
     * @deprecated Use aiPromptFormatter.formatMethodInfoPrompt instead
     */
    @Deprecated(
        "Use aiPromptFormatter.formatMethodInfoPrompt instead",
        ReplaceWith("aiPromptFormatter.formatMethodInfoPrompt(methodInfo)")
    )
    private fun formatPrompt(methodInfo: MethodInfo): String {
        return aiPromptFormatter.formatMethodInfoPrompt(methodInfo)
    }

    /**
     * Parses the AI response to extract the inferred return type information
     */
    private fun parseAIResponse(aiResponse: String, methodInfo: MethodInfo): ReturnTypeInfo {
        try {
            // Extract JSON from the response (in case there's any extra text)
            val jsonStart = aiResponse.indexOf("{")
            val jsonEnd = aiResponse.lastIndexOf("}") + 1

            if (jsonStart == -1 || jsonEnd == 0 || jsonStart >= jsonEnd) {
                logger.warn("Invalid AI response format for method ${methodInfo.className}.${methodInfo.methodName}")
                return createFallbackReturnInfo(methodInfo)
            }

            val jsonResponse = aiResponse.substring(jsonStart, jsonEnd)

            // Parse the JSON response using GsonUtils
            val jsonElement = GsonUtils.parseToJsonTree(jsonResponse)

            if (jsonElement == null || !jsonElement.isJsonObject) {
                logger.warn("Invalid JSON response for method ${methodInfo.className}.${methodInfo.methodName}")
                return createFallbackReturnInfo(methodInfo)
            }

            val jsonObject = jsonElement.asJsonObject
            val type = if (jsonObject.has("type")) jsonObject.get("type").asString else methodInfo.returnTypeName
            val structure = if (jsonObject.has("structure") && jsonObject.get("structure").isJsonObject)
                jsonObject.getAsJsonObject("structure")
            else
                null

            // Extract additional classes needed from the JSON response
            val additionalClassesNeeded = if (jsonObject.has("additionalClassesNeeded") &&
                jsonObject.get("additionalClassesNeeded").isJsonArray
            ) {
                val classesArray = jsonObject.getAsJsonArray("additionalClassesNeeded")
                val classes = mutableListOf<String>()
                for (i in 0 until classesArray.size()) {
                    val classElement = classesArray.get(i)
                    if (classElement.isJsonPrimitive) {
                        classes.add(classElement.asString)
                    }
                }
                classes
            } else null

            return ReturnTypeInfo(
                type = type,
                structure = structure,
                additionalClassesNeeded = additionalClassesNeeded
            )
        } catch (e: Exception) {
            logger.traceError("Error parsing AI response", e)
            return createFallbackReturnInfo(methodInfo)
        }
    }

    /**
     * Creates a fallback return type info when AI inference fails
     */
    private fun createFallbackReturnInfo(methodInfo: MethodInfo): ReturnTypeInfo {
        return ReturnTypeInfo(
            type = methodInfo.returnTypeName,
            structure = null
        )
    }

    /**
     * Creates an object based on the inferred return type information
     */
    private fun createReturnObject(returnTypeInfo: ReturnTypeInfo, psiMethod: PsiMethod): Any? {
        // If additional classes are needed, return a special object indicating this
        // but also include the best inference so far
        if (returnTypeInfo.additionalClassesNeeded?.isNotEmpty() == true) {
            // Create a map with the additional classes needed information
            val resultMap = mutableMapOf<String, Any?>(
                "_additionalClassesNeeded" to returnTypeInfo.additionalClassesNeeded,
                "_methodName" to "${psiMethod.containingClass?.qualifiedName}.${psiMethod.name}"
            )

            // Also include the best inference so far if available
            if (returnTypeInfo.structure != null) {
                try {
                    // Try to create an object based on the inferred type and structure
                    val baseObject = createBaseObject(returnTypeInfo, psiMethod)

                    if (baseObject != null) {
                        resultMap["_bestInferenceResult"] = baseObject
                    }
                } catch (e: Exception) {
                    logger.traceError("Error creating best inference result", e)
                }
            }

            return resultMap
        }

        return createBaseObject(returnTypeInfo, psiMethod)
    }

    /**
     * Creates a base object from the return type information
     */
    private fun createBaseObject(returnTypeInfo: ReturnTypeInfo, psiMethod: PsiMethod): Any? {
        try {
            // Get the original return type from the method
            val originalReturnType = psiMethod.returnType ?: return null

            // Try to resolve the type using PsiResolver.resolveClassOrType
            var resolvedType = psiResolver.resolveClassOrType(returnTypeInfo.type, psiMethod)

            // If we couldn't resolve with the fully qualified name, try with simplified names
            if (resolvedType == null) {
                val simplifiedGenericType = simplifyGenericType(returnTypeInfo.type)
                if (simplifiedGenericType != returnTypeInfo.type) {
                    logger.trace("Could not resolve type with fully qualified name or simple name. Trying with simplified generic type: $simplifiedGenericType")
                    resolvedType = psiResolver.resolveClassOrType(simplifiedGenericType, psiMethod)
                }
            }

            if (resolvedType != null) {
                // If we resolved the type, create an object using the PsiClassHelper
                val baseObject = when (resolvedType) {
                    is PsiType -> psiClassHelper.getTypeObject(resolvedType, psiMethod, 0)
                    is PsiClass -> psiClassHelper.getFields(resolvedType, psiMethod, 0)
                    else -> null
                }

                // If we have structure information, try to populate the object
                if (returnTypeInfo.structure != null && baseObject != null) {
                    populateObject(baseObject, returnTypeInfo.structure)
                }

                return baseObject
            } else {
                // If we couldn't resolve the type, fall back to using the original return type
                return psiClassHelper.getTypeObject(originalReturnType, psiMethod, 0)
            }
        } catch (e: Exception) {
            logger.traceError("Error creating return object", e)
            return null
        }
    }

    /**
     * Simplifies a generic type by extracting simple class names from fully qualified names
     * For example: com.example.Result<com.example.model.User> becomes Result<User>
     */
    private fun simplifyGenericType(fullType: String): String {
        if (!fullType.contains("<") || !fullType.contains(">")) {
            return fullType.substringAfterLast('.')
        }

        try {
            // Use regex to replace all fully qualified names with simple names
            // This pattern matches package names (sequences ending with a dot followed by a capital letter)
            val packagePattern = "([a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*\\.)([A-Z][A-Za-z0-9_]*)"
            val simplifiedType = fullType.replace(packagePattern.toRegex()) { matchResult ->
                // Keep only the class name part (group 3)
                matchResult.groupValues[3]
            }

            return simplifiedType
        } catch (e: Exception) {
            logger.traceError("Error simplifying generic type with regex: $fullType", e)
            return fullType
        }
    }

    /**
     * Recursively populates an object with the structure from the AI response
     */
    private fun populateObject(obj: Any, structure: JsonElement) {
        // This is a simplified implementation
        // In a real implementation, you would use reflection to set fields

        // For maps, add entries based on the structure
        if (obj is MutableMap<*, *> && structure.isJsonObject) {
            @Suppress("UNCHECKED_CAST")
            val map = obj as MutableMap<String, Any?>
            val jsonObject = structure.asJsonObject

            for (entry in jsonObject.entrySet()) {
                val key = entry.key
                val value = entry.value

                when {
                    value.isJsonObject -> {
                        // For nested objects, create a new map and populate it recursively
                        val nestedMap = mutableMapOf<String, Any?>()
                        populateObject(nestedMap, value)
                        map[key] = nestedMap
                    }

                    value.isJsonArray -> {
                        // For arrays/lists, create a new list
                        val list = mutableListOf<Any?>()
                        val jsonArray = value.asJsonArray
                        if (jsonArray.size() > 0) {
                            // Add a sample item based on the first element
                            val firstItem = jsonArray.get(0)
                            if (firstItem.isJsonObject) {
                                val nestedMap = mutableMapOf<String, Any?>()
                                populateObject(nestedMap, firstItem)
                                list.add(nestedMap)
                            } else {
                                list.add(createPrimitiveValue(firstItem.asString))
                            }
                        }
                        map[key] = list
                    }

                    else -> {
                        // For primitive types, create a sample value
                        map[key] = createPrimitiveValue(value.asString)
                    }
                }
            }
        }

        // For other object types, you would use reflection to set fields
        // This is omitted for simplicity
    }

    /**
     * Creates a primitive value based on a type string
     */
    private fun createPrimitiveValue(typeStr: String): Any? {
        return when (typeStr.lowercase()) {
            "string" -> "sample"
            "integer", "int" -> 0
            "long" -> 0L
            "short" -> 0.toShort()
            "byte" -> 0.toByte()
            "double" -> 0.0
            "float" -> 0.0f
            "boolean" -> false
            else -> null
        }
    }

    /**
     * Generates a cache key for a method
     */
    private fun getCacheKey(psiMethod: PsiMethod): String {
        return PsiClassUtil.fullNameOfMethod(psiMethod)
    }

    /**
     * Generates a cache key for a method with caller and args
     */
    private fun getCacheKey(psiMethod: PsiMethod, caller: Any?, args: Array<Any?>?): String {
        val baseKey = PsiClassUtil.fullNameOfMethod(psiMethod)

        // If no caller or args, just use the method name
        if (caller == null && args == null) {
            return baseKey
        }

        // Create a hash of the caller and args to include in the cache key
        val callerHash = caller?.hashCode() ?: 0
        val argsHash = args?.contentHashCode() ?: 0

        return "$baseKey:$callerHash:$argsHash"
    }

    /**
     * Data classes for method information
     */
    data class MethodInfo(
        val className: String,
        val methodName: String,
        val returnTypeName: String,
        val parameters: List<ParameterInfo>,
        val methodAnnotations: List<String>,
        val methodCode: String,
        val classContext: List<FieldInfo>,
        val referencedClasses: List<ClassInfo> = emptyList(),
        val additionalRequestedClasses: List<ClassInfo> = emptyList()
    )

    data class ParameterInfo(
        val name: String,
        val type: String,
        val annotations: List<String> = emptyList()
    )

    data class FieldInfo(
        val name: String,
        val type: String,
        val annotations: List<String> = emptyList()
    )

    data class ClassInfo(
        val name: String,
        val fields: List<FieldInfo>,
        val methods: List<MethodSummary>
    )

    data class MethodSummary(
        val name: String,
        val returnType: String,
        val parameters: List<String>
    )

    data class ReturnTypeInfo(
        val type: String,
        val structure: JsonObject?,
        val additionalClassesNeeded: List<String>? = null
    )
} 