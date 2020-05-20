package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonElement
import com.google.inject.Singleton
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.KV
import com.itangcent.http.contentType
import com.itangcent.intellij.extend.asJsonElement
import com.itangcent.intellij.extend.asList
import com.itangcent.intellij.extend.sub
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.entity.ContentType
import java.util.*
import kotlin.collections.set
import kotlin.concurrent.withLock

@Singleton
open class DefaultYapiApiHelper : AbstractYapiApiHelper(), YapiApiHelper {

    //$projectId$cartName -> $cartId
    private var cartIdCache: HashMap<String, String> = HashMap()

    override fun findCartWeb(module: String, cartName: String): String? {
        val token = getPrivateToken(module)
        val projectId = getProjectIdByToken(token!!) ?: return null
        val catId = findCat(token, cartName) ?: return null
        return getCartWeb(projectId, catId)
    }

    override fun getCartWeb(projectId: String, catId: String): String? {
        return "$server/project/$projectId/interface/api/cat_$catId"
    }

    override fun getApiWeb(module: String, cartName: String, apiName: String): String? {
        val token = getPrivateToken(module)
        val projectId = getProjectIdByToken(token!!) ?: return null
        val catId = findCat(token, cartName) ?: return null
        val apiId = findApi(token, catId, apiName)
        return "$server/project/$projectId/interface/api/$apiId"
    }

    override fun findCat(token: String, name: String): String? {
        val projectId: String? = getProjectIdByToken(token) ?: return null
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
            logger!!.traceError("error to find cat. projectId:$projectId, info: ${projectInfo?.toString()}", e)
        }
        return null
    }

    override fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean {

        try {
            val returnValue = httpClientProvide!!.getHttpClient()
                    .post(server + SAVE_API)
                    .contentType(ContentType.APPLICATION_JSON)
                    .body(apiInfo)
                    .call()
                    .string()
            val errMsg = findErrorMsg(returnValue)
            if (StringUtils.isNotBlank(errMsg)) {
                logger!!.info("Post failed:$errMsg")
                logger.info("api info:${GsonUtils.toJson(apiInfo)}")
                return false
            }
            return true
        } catch (e: Throwable) {
            logger!!.error("Post failed:" + ExceptionUtils.getStackTrace(e))
            return false
        }
    }

    override fun addCart(privateToken: String, name: String, desc: String): Boolean {
        val projectId = getProjectIdByToken(privateToken) ?: return false
        return addCart(projectId, privateToken, name, desc)
    }

    override fun addCart(projectId: String, token: String, name: String, desc: String): Boolean {
        try {
            val returnValue = httpClientProvide!!.getHttpClient()
                    .post(server + ADD_CART)
                    .contentType(ContentType.APPLICATION_JSON)
                    .body(KV.create<Any?, Any?>()
                            .set("desc", desc)
                            .set("project_id", projectId)
                            .set("name", name)
                            .set("token", token))
                    .call()
                    .string()

            val errMsg = findErrorMsg(returnValue)
            if (StringUtils.isNotBlank(errMsg)) {
                logger!!.info("Post failed:$errMsg")
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
                logger!!.info("Add new cart:$server/project/$projectId/interface/api/cat_$addCartId")
            } else {
                logger!!.info("Add cart failed,response is:$returnValue")
            }
            cacheLock.writeLock().withLock { projectInfoCache.remove(projectId) }
            return true
        } catch (e: Throwable) {
            logger!!.error("Post failed:" + ExceptionUtils.getStackTrace(e))
            return false
        }
    }

    override fun findApi(token: String, catId: String, apiName: String): String? {
        val url = "$server$GET_CAT?token=$token&catid=$catId&limit=1000"
        return GsonUtils.parseToJsonTree(getByApi(url))
                ?.sub("data")
                ?.sub("list")
                ?.asJsonArray?.firstOrNull { api ->
                    api.sub("title")
                            ?.asString == apiName
                }?.sub("_id")?.asString
    }

    override fun findApis(token: String, catId: String): ArrayList<Any?>? {
        val url = "$server$GET_CAT?token=$token&catid=$catId&limit=1000"
        return GsonUtils.parseToJsonTree(getByApi(url))
                ?.sub("data")
                ?.sub("list")
                ?.asList()
    }

    override fun findCarts(project_id: String, token: String): ArrayList<Any?>? {
        val url = "$server$GET_CAT_MENU?project_id=$project_id&token=$token"
        return GsonUtils.parseToJsonTree(getByApi(url))
                ?.sub("data")
                ?.asList()
    }

    companion object {
        const val ADD_CART = "/api/interface/add_cat"
        const val GET_CAT_MENU = "/api/interface/getCatMenu"
        const val SAVE_API = "/api/interface/save"
        const val GET_CAT = "/api/interface/list_cat"
    }
}