package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.KV
import com.itangcent.intellij.extend.asList
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import java.util.*
import kotlin.collections.set
import kotlin.concurrent.withLock

open class YapiApiHelper : AbstractYapiApiHelper() {

    //$projectId$cartName -> $cartId
    private var cartIdCache: HashMap<String, String> = HashMap()

    open fun findCartWeb(module: String, cartName: String): String? {
        val token = getPrivateToken(module)
        val projectId = getProjectIdByToken(token!!) ?: return null
        val catId = findCat(token, cartName) ?: return null
        return getCartWeb(projectId, catId)
    }

    open fun getCartWeb(projectId: String, catId: String): String? {
        return "$server/project/$projectId/interface/api/cat_$catId"
    }

    open fun getApiWeb(module: String, cartName: String, apiName: String): String? {
        val token = getPrivateToken(module)
        val projectId = getProjectIdByToken(token!!) ?: return null
        val catId = findCat(token, cartName) ?: return null
        val apiId = findApi(token, catId, apiName)
        return "$server/project/$projectId/interface/api/$apiId"
    }

    open fun findCat(token: String, name: String): String? {
        val projectId: String? = getProjectIdByToken(token) ?: return null
        val key = "$projectId$name"
        var cachedCartId = cacheLock.readLock().withLock { cartIdCache[key] }
        if (cachedCartId != null) return cachedCartId
        val cats = getProjectInfo(token, projectId)
                ?.asJsonObject
                ?.get("data")
                ?.asJsonObject
                ?.get("cat")
                ?.asJsonArray
        cats?.forEach { cat ->
            if (cat.asJsonObject
                            .get("name")
                            .asString == name) {
                cachedCartId = cat.asJsonObject
                        .get("_id")
                        .asString
                if (cachedCartId != null) {
                    cacheLock.writeLock().withLock {
                        cartIdCache[key] = cachedCartId!!
                    }
                }
                return cachedCartId
            }
        }
        return null
    }

    open fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean {

        val httpClient = httpClientProvide!!.getHttpClient()

        val httpPost = HttpPost(server + SAVEAPI)

        val requestEntity = StringEntity(GsonUtils.toJson(apiInfo),
                ContentType.APPLICATION_JSON)
        httpPost.entity = requestEntity
        val responseHandler = reservedResponseHandle()

        try {
            val returnValue = httpClient.execute(httpPost, responseHandler).result()
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

    open fun addCart(privateToken: String, name: String, desc: String): Boolean {
        val projectId = getProjectIdByToken(privateToken) ?: return false
        return addCart(projectId, privateToken, name, desc)
    }

    open fun addCart(projectId: String, token: String, name: String, desc: String): Boolean {

        val httpClient = httpClientProvide!!.getHttpClient()

        val httpPost = HttpPost(server + ADDCART)

        val requestEntity = StringEntity(GsonUtils.toJson(KV.create<Any?, Any?>()
                .set("desc", desc)
                .set("project_id", projectId)
                .set("name", name)
                .set("token", token)
        ),
                ContentType.APPLICATION_JSON)

        httpPost.entity = requestEntity

        val responseHandler = reservedResponseHandle()

        try {
            val returnValue = httpClient.execute(httpPost, responseHandler).result()
            val errMsg = findErrorMsg(returnValue)
            if (StringUtils.isNotBlank(errMsg)) {
                logger!!.info("Post failed:$errMsg")
                return false
            }
            val resObj = GsonUtils.parseToJsonTree(returnValue)
            val addCartId: String? = resObj?.asJsonObject
                    ?.get("data")
                    ?.asJsonObject
                    ?.get("_id")
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

    protected open fun findApi(token: String, catId: String, apiName: String): String? {
        val url = "$server$GETCAT?token=$token&catid=$catId&limit=1000"
        return GsonUtils.parseToJsonTree(getByApi(url))
                ?.asJsonObject
                ?.get("data")
                ?.asJsonObject
                ?.get("list")
                ?.asJsonArray?.firstOrNull { api ->
            api.asJsonObject.get("title")
                    .asString == apiName
        }?.asJsonObject?.get("_id")?.asString
    }

    open fun findApis(token: String, catId: String): ArrayList<Any?>? {
        val url = "$server$GETCAT?token=$token&catid=$catId&limit=1000"
        return GsonUtils.parseToJsonTree(getByApi(url))
                ?.asJsonObject
                ?.get("data")
                ?.asJsonObject
                ?.get("list")
                ?.asList()
    }

    open fun findCarts(project_id: String, token: String): ArrayList<Any?>? {
        val url = "$server$GETCATMENU?project_id=$project_id&token=$token"
        return GsonUtils.parseToJsonTree(getByApi(url))
                ?.asJsonObject
                ?.get("data")
                ?.asList()
    }

    companion object {
        var ADDCART = "/api/interface/add_cat"
        const val GETCATMENU = "/api/interface/getCatMenu"
        const val SAVEAPI = "/api/interface/save"
        const val GETCAT = "/api/interface/list_cat"
    }
}