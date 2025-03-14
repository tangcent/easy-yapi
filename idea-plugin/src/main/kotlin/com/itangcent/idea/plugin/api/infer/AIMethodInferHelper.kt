package com.itangcent.idea.plugin.api.infer

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.ai.AIMessages
import com.itangcent.idea.plugin.api.ai.AIService
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.jvm.PsiResolver
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.spi.SpiCompositeLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * AI-based implementation of MethodInferHelper
 * Uses AI to analyze method code and infer return types
 */
@Singleton
class AIMethodInferHelper : MethodInferHelper {

    companion object {
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
        private const val MAX_REFERENCED_CLASSES = 20
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

    private val aiService: AIService by lazy {
        SpiCompositeLoader.load<AIService>(actionContext).first()
    }

    // Cache for storing AI inference results to avoid repeated API calls
    private val inferCache: ConcurrentHashMap<String, Any?> = ConcurrentHashMap()


    override fun inferReturn(psiMethod: PsiMethod, option: Int): Any? {
        return inferCache.computeIfAbsent(getCacheKey(psiMethod)) {
            inferReturn(psiMethod, null, null, option)
        }
    }

    override fun inferReturn(psiMethod: PsiMethod, caller: Any?, args: Array<Any?>?, option: Int): Any? {
        try {
            // 1. Extract method information
            val methodInfo = actionContext.callInReadUI { extractMethodInfo(psiMethod) } ?: return null

            // 2. Call AI service to infer return type
            var inferredReturnInfo = callAIService(methodInfo)

            // 3. If the AI requested additional classes, collect them and try again
            val additionalClassesNeeded = inferredReturnInfo.additionalClassesNeeded
            if (!additionalClassesNeeded.isNullOrEmpty()) {
                logger.info(
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

                if (additionalClasses.isNotEmpty()) {
                    // Create an enhanced method info with the additional classes
                    val enhancedMethodInfo = methodInfo.copy(
                        additionalRequestedClasses = additionalClasses
                    )

                    // Try inference again with the additional class information
                    logger.info("Retrying inference with additional class information")
                    try {
                        val enhancedInferredReturnInfo = callAIService(enhancedMethodInfo)

                        // If the second inference attempt was successful (has a structure), use it
                        if (enhancedInferredReturnInfo.structure != null) {
                            inferredReturnInfo = enhancedInferredReturnInfo
                        } else {
                            // Otherwise, keep the best inference so far but preserve the additional classes needed
                            inferredReturnInfo = bestInferenceResult
                            logger.warn("Second inference attempt failed to produce a structure, using best inference from first attempt")
                        }
                    } catch (e: Exception) {
                        // If the second inference attempt fails, use the best inference so far
                        logger.traceError("Error in second inference attempt", e)
                        inferredReturnInfo = bestInferenceResult
                    }
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
        val prompt = formatPrompt(methodInfo)

        // Maximum number of retry attempts
        val maxRetries = 3
        var currentRetry = 0
        var lastException: Exception? = null

        while (currentRetry < maxRetries) {
            try {
                // Call the AI API with the system message and prompt
                val aiResponse = aiService.sendPrompt(AIMessages.METHOD_RETURN_TYPE_INFERENCE_MESSAGE, prompt)

                // Check if the response is valid before parsing
                if (aiResponse.isBlank()) {
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
     */
    private fun formatPrompt(methodInfo: MethodInfo): String {
        val referencedClassesSection = if (methodInfo.referencedClasses.isNotEmpty()) {
            """
            Referenced Classes:
            ${
                methodInfo.referencedClasses.joinToString("\n\n") { classInfo ->
                    """
                Class: ${classInfo.name}
                Fields:
                ${classInfo.fields.joinToString("\n") { "- ${it.name}: ${it.type} ${it.annotations.joinToString(" ") { "@$it" }}" }}
                
                Methods:
                ${
                        classInfo.methods.joinToString("\n") { method ->
                            "- ${method.name}(${method.parameters.joinToString(", ")}): ${method.returnType}"
                        }
                    }
                """.trimIndent()
                }
            }
            """
        } else {
            ""
        }

        val additionalRequestedClassesSection = if (methodInfo.additionalRequestedClasses.isNotEmpty()) {
            psiInferenceCollector.formatClassInfoForPrompt(methodInfo.additionalRequestedClasses)
        } else {
            ""
        }

        return """
            I need to infer the actual return type structure for a Java/Kotlin method.
            
            Class: ${methodInfo.className}
            Method: ${methodInfo.methodName}
            Declared Return Type: ${methodInfo.returnTypeName}
            
            Method Annotations:
            ${methodInfo.methodAnnotations.joinToString("\n") { "- $it" }}
            
            Parameters:
            ${
            methodInfo.parameters.joinToString("\n") { param ->
                "- ${param.name}: ${param.type} ${param.annotations.joinToString(" ") { "@$it" }}"
            }
        }
            
            Class Fields:
            ${methodInfo.classContext.joinToString("\n") { "- ${it.name}: ${it.type}" }}
            
            $referencedClassesSection
            
            $additionalRequestedClassesSection
            
            Method Code:
            ```
            ${methodInfo.methodCode}
            ```
            
            Please analyze this method and provide the complete return type structure. 
            For example, if the method returns Result<UserInfo>, I need the full structure including generic types.
            
            If you need information about additional classes to accurately infer the return type, 
            include them in the "additionalClassesNeeded" field of your JSON response. 
            Even when requesting additional classes, please provide your best inference of the structure 
            based on the information available to you. This will be used as a fallback if the additional 
            classes cannot be provided.
            
            Respond with a JSON object that represents the return type structure:
            {
              "type": "The fully qualified class name of the return type",
              "structure": {
                // A representation of the object structure with field names and their types
                // For example, for Result<UserInfo> it might be:
                "code": "int",
                "message": "string",
                "data": {
                  "id": "long",
                  "name": "string",
                  "age": "int"
                  // other fields of UserInfo
                }
              },
              "additionalClassesNeeded": [
                // If you need more information about specific classes to provide a more accurate inference,
                // include their fully qualified names here
                "com.example.ClassName1",
                "com.example.ClassName2"
              ]
            }
        """.trimIndent()
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
            } else {
                // Fallback to the old text-based extraction if not found in JSON
                extractAdditionalClassesNeeded(aiResponse)
            }

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
     * Extracts the list of additional classes needed from the AI response text
     * This is a fallback method for backward compatibility
     */
    private fun extractAdditionalClassesNeeded(aiResponse: String): List<String> {
        val marker = "ADDITIONAL_CLASSES_NEEDED:"
        val index = aiResponse.indexOf(marker)
        if (index == -1) {
            return emptyList()
        }

        val classesSection = aiResponse.substring(index + marker.length)
        val lines = classesSection.split("\n")

        return lines
            .filter { it.trim().startsWith("-") }
            .map { it.trim().removePrefix("-").trim() }
            .filter { it.isNotEmpty() }
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
            val resolvedType = psiResolver.resolveClassOrType(returnTypeInfo.type, psiMethod)

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
        return PsiClassUtils.fullNameOfMethod(psiMethod)
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