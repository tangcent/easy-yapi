package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.asInt
import com.itangcent.common.utils.asMap
import com.itangcent.http.contentType
import com.itangcent.idea.plugin.utils.LocalStorage
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.asJsonElement
import com.itangcent.intellij.extend.asList
import com.itangcent.intellij.extend.asMutableList
import com.itangcent.intellij.extend.sub
import com.itangcent.spi.SpiCompositeLoader
import org.apache.commons.lang3.StringUtils
import org.apache.http.entity.ContentType
import kotlin.collections.set
import kotlin.concurrent.withLock

@Singleton
open class DefaultYapiApiHelper : AbstractYapiApiHelper(), YapiApiHelper {

    //$projectId$cartName -> $cartId
    private var cartIdCache: HashMap<String, String> = HashMap()

    @Inject
    protected lateinit var localStorage: LocalStorage

    @Inject
    internal lateinit var messagesHelper: MessagesHelper

    @Inject
    internal lateinit var actionContext: ActionContext

    override fun getApiInfo(token: String, id: String): JsonObject? {
        val url = "${yapiSettingsHelper.getServer()}$GET_INTERFACE?token=$token&id=$id"
        return GsonUtils.parseToJsonTree(getByApi(url))
            ?.sub("data")?.asJsonObject
    }

    override fun findApi(token: String, catId: String, apiName: String): String? {
        return listApis(token, catId)
            ?.firstOrNull { api ->
                api.sub("title")
                    ?.asString == apiName
            }?.sub("_id")?.asString
    }

    override fun findApis(token: String, catId: String): List<Any?>? {
        return listApis(token, catId)
            ?.asMutableList()
    }

    override fun listApis(token: String, catId: String, limit: Int?): JsonArray? {
        var apiLimit = limit ?: localStorage.get("__internal__", "yapi.api.limit").asInt() ?: 1000
        val url = "${yapiSettingsHelper.getServer()}$GET_CAT?token=$token&catid=$catId&limit=$apiLimit"
        val jsonArray = GsonUtils.parseToJsonTree(getByApi(url))
            ?.sub("data")
            ?.sub("list")
            ?.asJsonArray
        if (jsonArray?.size() == apiLimit && apiLimit < 5000) {
            apiLimit = (apiLimit * 1.4).toInt()
            localStorage.set("__internal__", "yapi.api.limit", apiLimit)
            return listApis(token, catId, apiLimit)
        }
        return jsonArray
    }

    private val saveInterceptor: YapiSaveInterceptor by lazy {
        SpiCompositeLoader.loadComposite()
    }

    override fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean {
        if (saveInterceptor.beforeSaveApi(this, apiInfo) == false) {
            return false
        }

        if (yapiSettingsHelper.loginMode() && apiInfo.containsKey("token")) {
            apiInfo["project_id"] = apiInfo["token"]
            apiInfo.remove("token")
        }

        try {
            val returnValue = httpClientProvide!!.getHttpClient()
                .post(yapiSettingsHelper.getServer(false) + SAVE_API)
                .contentType(ContentType.APPLICATION_JSON)
                .body(apiInfo)
                .call()
                .use { it.string() }
                ?.trim()
            val errMsg = findErrorMsg(returnValue)
            if (StringUtils.isNotBlank(errMsg)) {
                logger.info("save apiInfo failed:$errMsg")
                logger.info("api info:${GsonUtils.toJson(apiInfo)}")
                return false
            }
            GsonUtils.parseToJsonTree(returnValue)
                .sub("data")
                ?.asList()
                ?.firstOrNull()
                ?.asMap()
                ?.get("_id")
                ?.toString()?.let {
                    apiInfo["_id"] = it
                    LOG.debug("save api: $it")
                }
            return true
        } catch (e: Throwable) {
            logger.traceError("save apiInfo failed", e)
            return false
        }
    }

    override fun getApiWeb(module: String, cartName: String, apiName: String): String? {
        val token = yapiSettingsHelper.getPrivateToken(module)
        val projectId = getProjectIdByToken(token!!) ?: return null
        val catId = findCart(token, cartName) ?: return null
        val apiId = findApi(token, catId, apiName) ?: return null
        return "${yapiSettingsHelper.getServer()}/project/$projectId/interface/api/$apiId"
    }

    override fun findCartWeb(module: String, cartName: String): String? {
        val token = yapiSettingsHelper.getPrivateToken(module)
        val projectId = getProjectIdByToken(token!!) ?: return null
        val catId = findCart(token, cartName) ?: return null
        return getCartWeb(projectId, catId)
    }

    override fun getCartWeb(projectId: String, catId: String): String? {
        return "${yapiSettingsHelper.getServer()}/project/$projectId/interface/api/cat_$catId"
    }

    override fun findCart(token: String, name: String): String? {
        val projectId: String = getProjectIdByToken(token) ?: return null
        val key = "$projectId$name"
        var cachedCartId = cacheLock.readLock().withLock { cartIdCache[key] }
        if (cachedCartId != null) return cachedCartId
        var projectInfo: JsonElement? = null
        try {
            projectInfo = getProjectInfo(token, projectId)
            val cats = projectInfo
                ?.sub("data")
                ?.sub("cat")
                ?.asJsonArray
            cats?.forEach { cat ->
                if (cat.sub("name")?.asString == name) {
                    cachedCartId = cat.sub("_id")!!
                        .asString
                    if (cachedCartId != null) {
                        cacheLock.writeLock().withLock {
                            cartIdCache[key] = cachedCartId!!
                        }
                    }
                    return cachedCartId
                }
            }
        } catch (e: Exception) {
            logger.traceError("error to find cat. projectId:$projectId, info: ${projectInfo?.toString()}", e)
        }
        return null
    }

    override fun addCart(privateToken: String, name: String, desc: String): Boolean {
        val projectId = getProjectIdByToken(privateToken) ?: return false
        return addCart(projectId, privateToken, name, desc)
    }

    override fun addCart(projectId: String, token: String, name: String, desc: String): Boolean {
        try {
            val returnValue = httpClientProvide!!.getHttpClient()
                .post(yapiSettingsHelper.getServer(false) + ADD_CART)
                .contentType(ContentType.APPLICATION_JSON)
                .body(
                    linkedMapOf(
                        "desc" to desc,
                        "project_id" to projectId,
                        "name" to name,
                        "token" to yapiSettingsHelper.rawToken(token)
                    )
                )
                .call()
                .use { it.string() }
                ?.trim()

            val errMsg = findErrorMsg(returnValue)
            if (StringUtils.isNotBlank(errMsg)) {
                logger.info("Post failed:$errMsg")
                return false
            }
            val resObj = returnValue?.asJsonElement()
            val addCartId: String? = resObj.sub("data")
                .sub("_id")
                ?.asString
            if (addCartId != null) {
                cacheLock.writeLock().withLock {
                    cartIdCache["$projectId$name"] = addCartId
                }
                logger.info("Add new cart:${yapiSettingsHelper.getServer()}/project/$projectId/interface/api/cat_$addCartId")
            } else {
                logger.info("Add cart failed,response is:$returnValue")
            }
            cacheLock.writeLock().withLock { projectInfoCache.remove(projectId) }
            return true
        } catch (e: Throwable) {
            logger.traceError("Post failed", e)
            return false
        }
    }

    override fun findCarts(projectId: String, token: String): List<Any?>? {
        val url = "${yapiSettingsHelper.getServer()}$GET_CAT_MENU?project_id=$projectId&token=$token"
        return GsonUtils.parseToJsonTree(getByApi(url))
            ?.sub("data")
            ?.asList()
    }

    companion object : Log() {
        const val GET_INTERFACE = "/api/interface/get"
        const val ADD_CART = "/api/interface/add_cat"
        const val GET_CAT_MENU = "/api/interface/getCatMenu"
        const val SAVE_API = "/api/interface/save"
        const val GET_CAT = "/api/interface/list_cat"
    }
}