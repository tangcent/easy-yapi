package com.itangcent.idea.plugin.api.infer

import com.google.inject.Singleton

/**
 * Utility class for formatting information for AI prompts
 */
@Singleton
class AIPromptFormatter {

    companion object {
        /**
         * System message for method return type inference
         */
        const val METHOD_RETURN_TYPE_INFERENCE_MESSAGE =
            "You are a code analysis assistant specialized in inferring method return types. " +
            "Analyze the provided method code and context to determine the complete structure of the return value, " +
            "including generic types and nested objects. For example, if a method returns Result<UserInfo>, " +
            "you should identify both the Result structure and the UserInfo structure within it. " +
            "Pay close attention to the referenced classes provided in the prompt, as they contain " +
            "important information about the types used in the method. " +
            "If you need information about additional classes that are not provided in the prompt, " +
            "include them in the 'additionalClassesNeeded' field of your JSON response. " +
            "Even when requesting additional classes, always provide your best inference of the structure " +
            "based on the information available to you. This will be used as a fallback if the additional " +
            "classes cannot be provided. " +
            "Respond with a JSON object that follows this exact structure:\n" +
            "{\n" +
            "  \"type\": \"The fully qualified class name of the return type\",\n" +
            "  \"structure\": {\n" +
            "    // A representation of the object structure with field names and their types\n" +
            "    // For example, for Result<UserInfo> it might be:\n" +
            "    \"code\": \"int\",\n" +
            "    \"message\": \"string\",\n" +
            "    \"data\": {\n" +
            "      \"id\": \"long\",\n" +
            "      \"name\": \"string\",\n" +
            "      \"age\": \"int\"\n" +
            "      // other fields of UserInfo\n" +
            "    }\n" +
            "  },\n" +
            "  \"additionalClassesNeeded\": [\n" +
            "    // If you need more information about specific classes to provide a more accurate inference,\n" +
            "    // include their fully qualified names here\n" +
            "    \"com.example.ClassName1\",\n" +
            "    \"com.example.ClassName2\"\n" +
            "  ]\n" +
            "}"
    }

    /**
     * Creates a formatted string representation of referenced classes for inclusion in AI prompts
     *
     * @param classes List of ClassInfo objects representing referenced classes
     * @return A formatted string representation of the referenced classes
     */
    fun formatReferencedClassesForPrompt(classes: List<AIMethodInferHelper.ClassInfo>): String {
        if (classes.isEmpty()) {
            return ""
        }

        return """
            Referenced Classes:
            ${
            classes.joinToString("\n\n") { classInfo ->
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
        """.trimIndent()
    }

    /**
     * Formats the complete method information as a prompt for the AI
     *
     * @param methodInfo The method information to format
     * @return A formatted prompt string
     */
    fun formatMethodInfoPrompt(methodInfo: AIMethodInferHelper.MethodInfo): String {
        val referencedClassesSection = if (methodInfo.referencedClasses.isNotEmpty()) {
            formatReferencedClassesForPrompt(methodInfo.referencedClasses)
        } else {
            ""
        }

        val additionalRequestedClassesSection = if (methodInfo.additionalRequestedClasses.isNotEmpty()) {
            formatReferencedClassesForPrompt(methodInfo.additionalRequestedClasses)
        } else {
            ""
        }

        return """
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
            
            Please analyze this method and provide the complete return type structure in the required JSON format.
        """.trimIndent()
    }
} 