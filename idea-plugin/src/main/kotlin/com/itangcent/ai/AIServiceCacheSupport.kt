package com.itangcent.ai

import com.google.inject.matcher.Matchers
import com.itangcent.cache.CacheIndicator
import com.itangcent.common.logger.Log
import com.itangcent.common.spi.SetupAble
import com.itangcent.idea.plugin.settings.helper.AISettingsHelper
import com.itangcent.intellij.context.ActionContext
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation

/**
 * SetupAble class that binds the AIServiceCacheInterceptor to AIService implementations
 */
class AIServiceCacheSupport : SetupAble {
    /**
     * Initializes and binds the interceptor to AIService implementations
     */
    override fun init() {
        ActionContext.addDefaultInject { builder ->
            builder.bindInterceptor(
                Matchers.subclassesOf(AIService::class.java),
                Matchers.any(),
                AIServiceCacheInterceptor
            )
        }
    }
}

/**
 * MethodInterceptor that adds caching functionality to AIService implementations
 */
object AIServiceCacheInterceptor : MethodInterceptor, Log() {

    private val aiEnableCache: Boolean
        get() {
            return ActionContext.getContext()
                ?.instance(AISettingsHelper::class)
                ?.aiEnableCache == true
        }

    private val useCache: Boolean
        get() {
            return ActionContext.getContext()
                ?.instance(CacheIndicator::class)
                ?.useCache == true
        }

    /**
     * Intercepts method calls to AIService implementations and adds caching functionality
     */
    override fun invoke(invocation: MethodInvocation): Any? {
        // Only intercept sendPrompt methods
        val methodName = invocation.method.name
        if (methodName != "sendPrompt") {
            return invocation.proceed()
        }

        // Check if caching is enabled
        if (!aiEnableCache) {
            // If caching is disabled, proceed with the original method
            return invocation.proceed()
        }

        // Extract parameters based on method signature
        val args = invocation.arguments
        val systemMessage: String
        val userPrompt: String

        when (args.size) {
            1 -> {
                // Single parameter version: sendPrompt(prompt: String)
                systemMessage = ""
                userPrompt = args[0] as String
            }

            2 -> {
                // Two parameter version: sendPrompt(systemMessage: String, userPrompt: String)
                systemMessage = args[0] as String
                userPrompt = args[1] as String
            }

            else -> {
                // Unknown method signature, proceed with original method
                return invocation.proceed()
            }
        }

        val aiServiceCache: AIServiceCache = ActionContext.getContext()!!.instance(AIServiceCache::class)

        if (useCache) {
            // Check if we have a cached response
            val cachedResponse = aiServiceCache.getCachedResponse(systemMessage, userPrompt)
            if (!cachedResponse.isNullOrEmpty()) {
                return cachedResponse
            }
        }

        // No cached response, call the actual service
        val response = invocation.proceed() as String

        // Cache the response
        aiServiceCache.cacheResponse(systemMessage, userPrompt, response)

        return response
    }
} 