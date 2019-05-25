package com.itangcent.idea.plugin.api.export.yapi

import com.itangcent.common.utils.GsonUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.NameValuePair
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import java.nio.charset.Charset
import java.util.HashMap
import kotlin.collections.ArrayList
import kotlin.collections.set
import kotlin.concurrent.withLock

class YapiApiHelper : AbstractYapiApiHelper() {

    //$projectId$cartName -> $cartId
    private var cartIdCache: HashMap<String, String> = HashMap()

    fun getCartWeb(module: String, cartName: String): String? {
        val token = getPrivateToken(module)
        val projectId = getProjectIdByToken(token!!) ?: return null
        val catId = findCat(token, cartName) ?: return null
        return "$server/project/$projectId/interface/api/cat_$catId"
    }

    fun getApiWeb(module: String, cartName: String, apiName: String): String? {
        val token = getPrivateToken(module)
        val projectId = getProjectIdByToken(token!!) ?: return null
        val catId = findCat(token, cartName) ?: return null
        val apiId = findApi(token, catId, apiName)
        return "$server/project/$projectId/interface/api/$apiId"
    }

    fun findCat(token: String, name: String): String? {
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

    @Suppress("UNCHECKED_CAST")
    fun importApiInfo(module: String, apiInfo: HashMap<String, Any?>): Boolean {
        val token: String = getPrivateToken(module) ?: return false
        val projectId = getProjectIdByToken(token) ?: return false
        val name = apiInfo["name"] as String
        val desc = apiInfo["desc"] as String
        var catId = findCat(token, name)
        if (catId == null) {
            cacheLock.writeLock().withLock {
                catId = findCat(token, name)
                if (catId == null) {
                    if (!addCart(projectId, token, name, desc)) {
                        return false
                    }
                    catId = findCat(token, name)
                }
            }

        }
        if (catId != null) {
            val apis = apiInfo["list"] as List<HashMap<String, Any?>>
            for (api in apis) {
                api["token"] = token
                api["catid"] = catId
                saveApiInfo(api)
            }
            return true
        }
        return false
    }

    fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean {

        val httpClient = HttpClients.createDefault()

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

    fun addCart(module: String, name: String, desc: String): Boolean {
        val privateToken = getPrivateToken(module) ?: return false
        val projectId = getProjectIdByToken(privateToken) ?: return false
        return addCart(projectId, privateToken, name, desc)
    }

    fun addCart(projectId: String, token: String, name: String, desc: String): Boolean {
        val httpClient = HttpClients.createDefault()
        val httpPost = HttpPost(server + ADDCART)

        val requestParams: ArrayList<NameValuePair> = ArrayList()

        requestParams.add(BasicNameValuePair("desc", desc))
        requestParams.add(BasicNameValuePair("project_id", projectId))
        requestParams.add(BasicNameValuePair("name", name))
        requestParams.add(BasicNameValuePair("token", token))

        val requestEntity = StringEntity(URLEncodedUtils.format(requestParams,
                Charset.defaultCharset()),
                ContentType.APPLICATION_FORM_URLENCODED)
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

    private fun findApi(token: String, catId: String, apiName: String): String? {
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

    companion object {
        var ADDCART = "/api/interface/add_cat"
        val GETCATMENU = "/api/interface/getCatMenu"
        val SAVEAPI = "/api/interface/save"
        val GETCAT = "/api/interface/list_cat"
    }
}