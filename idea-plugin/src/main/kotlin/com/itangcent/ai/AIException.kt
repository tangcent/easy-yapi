package com.itangcent.ai

/**
 * Base exception for all AI-related errors
 */
open class AIException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Exception thrown when there's an issue with the AI service configuration
 */
class AIConfigurationException(message: String) : AIException(message)

/**
 * Exception thrown when there's an error in the AI service API response
 */
class AIApiException(message: String, cause: Throwable? = null) : AIException(message, cause) 