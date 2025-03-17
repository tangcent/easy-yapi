package com.itangcent.ai

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.Log
import com.itangcent.idea.plugin.api.cache.ProjectCacheRepository
import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.idea.sqlite.get
import com.itangcent.idea.sqlite.set
import com.itangcent.idea.utils.DigestUtils
import com.itangcent.intellij.context.ActionContext
import java.util.concurrent.TimeUnit

/**
 * Cache for AI service responses to avoid duplicate API calls
 */
@Singleton
class AIServiceCache {

    companion object : Log() {
        private val EXPIRED_TIME = TimeUnit.DAYS.toMillis(30)
    }

    @Inject
    private lateinit var projectCacheRepository: ProjectCacheRepository

    @Inject
    private lateinit var actionContext: ActionContext

    // Use SqliteDataResourceHelper instead of ConcurrentHashMap for persistent storage
    private val beanDAO: SqliteDataResourceHelper.ExpiredBeanDAO by lazy {
        val sqliteDataResourceHelper = actionContext.instance(SqliteDataResourceHelper::class)
        sqliteDataResourceHelper.getExpiredBeanDAO(
            projectCacheRepository.getOrCreateFile(".ai.service.cache.db").path, "AI_SERVICE_CACHE"
        )
    }

    /**
     * Get a cached response if available
     * @param systemMessage The system message
     * @param userPrompt The user prompt
     * @return The cached response or null if not found
     */
    fun getCachedResponse(systemMessage: String, userPrompt: String): String? {
        val key = createCacheKey(systemMessage, userPrompt)
        return beanDAO.get(key)
    }

    /**
     * Cache a response
     * @param systemMessage The system message
     * @param userPrompt The user prompt
     * @param response The AI response to cache
     */
    fun cacheResponse(systemMessage: String, userPrompt: String, response: String) {
        LOG.info("cache response: $userPrompt, $response")
        val key = createCacheKey(systemMessage, userPrompt)
        beanDAO.set(key, response, System.currentTimeMillis() + EXPIRED_TIME)
    }

    /**
     * Create a cache key from the system message and user prompt using MD5 hash
     */
    private fun createCacheKey(systemMessage: String, userPrompt: String): String {
        val input = "$systemMessage::$userPrompt"
        return DigestUtils.md5(input)
    }
} 