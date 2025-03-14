package com.itangcent.idea.plugin.api.ai

/**
 * Contains messages used by AI services
 */
object AIMessages {
    /**
     * Default system message for code analysis
     */
    const val DEFAULT_CODE_ANALYSIS_MESSAGE =
        "You are a helpful code analysis assistant. Analyze the provided code and provide insights."
        
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
        "Respond with a JSON structure that represents the complete return type and its fields."
} 