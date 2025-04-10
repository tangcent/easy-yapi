package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonArray
import com.google.inject.Inject
import com.itangcent.common.logger.traceError
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.intellij.extend.toInt
import com.itangcent.intellij.file.LocalFileRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * cache:
 * projectToken -> projectId
 */
open class CachedYapiApiHelper : DefaultYapiApiHelper() {

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    private val dbBeanBinderFactory: DbBeanBinderFactory<String> by lazy {
        DbBeanBinderFactory(localFileRepository!!.getOrCreateFile(".api.yapi.v1.1.db").path)
        { "" }
    }

    /**
     * Cache for cart data, projectId -> carts
     */
    private val cartCache = ConcurrentHashMap<String, List<Any?>>()

    /**
     * Cache for API data, "$token-$catId" -> APIs
     */
    private val apiCache = ConcurrentHashMap<String, JsonArray>()

    override fun getProjectIdByToken(token: String): String? {
        try {
            val tokenBeanBinder =
                dbBeanBinderFactory.getBeanBinder("yapi:token-${yapiSettingsHelper.loginMode().toInt()}:$token")
            val projectIdInCache = tokenBeanBinder.read()
            if (projectIdInCache.isNotBlank()) {
                return projectIdInCache
            }
            val projectIdByApi = super.getProjectIdByToken(token)
            if (!projectIdByApi.isNullOrBlank()) {
                tokenBeanBinder.save(projectIdByApi)
            }
            return projectIdByApi
        } catch (e: Exception) {
            logger.traceError("failed getProjectIdByToken by cache", e)
            return super.getProjectIdByToken(token)
        }
    }

    override fun findCarts(projectId: String, token: String): List<Any?>? {
        return cartCache.computeIfAbsent(projectId) {
            return@computeIfAbsent super.findCarts(projectId, token) ?: ArrayList<Any?>()
        }
    }

    override fun listApis(token: String, catId: String, limit: Int?): JsonArray? {
        return apiCache.computeIfAbsent("$token-$catId") {
            return@computeIfAbsent super.listApis(token, catId, limit) ?: JsonArray()
        }
    }

    override fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean {
        val result = super.saveApiInfo(apiInfo)

        // Clear API cache when API info is updated
        if (result && apiInfo.containsKey("token") && apiInfo.containsKey("catid")) {
            val token = apiInfo["token"] as String
            val catId = apiInfo["catid"].toString()
            apiCache.remove("$token-$catId")
        }

        return result
    }

    override fun addCart(
        projectId: String,
        token: String,
        name: String,
        desc: String
    ): Boolean {
        val result = super.addCart(projectId, token, name, desc)

        // Clear cart cache when new cart is added
        if (result) {
            cartCache.remove(projectId)
        }

        return result
    }
}