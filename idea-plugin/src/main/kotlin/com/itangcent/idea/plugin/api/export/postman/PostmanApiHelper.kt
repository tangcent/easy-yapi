package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.StringResponseHandler
import com.itangcent.intellij.extend.asMap
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.setting.SettingManager
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients


class PostmanApiHelper {

    @Inject
    private val settingManager: SettingManager? = null

    @Inject
    private val logger: Logger? = null

    @Inject
    private val httpClient: HttpClient? = null

    fun hasPrivateToken(): Boolean {
        return getPrivateToken() != null
    }

    private fun getPrivateToken(): String? {
        val setting = settingManager!!.getSetting(POSTMANHOST) ?: return null
        return if (StringUtils.isNotBlank(setting.privateToken)) {
            setting.privateToken
        } else {
            null
        }
    }

    fun importApiInfo(apiInfo: HashMap<String, Any?>): Boolean {

        val httpClient = HttpClients.createDefault()

        val httpPost = HttpPost(COLLECTION)
        val collection: HashMap<String, Any?> = HashMap()
        collection["collection"] = apiInfo

        val requestEntity = StringEntity(GsonUtils.toJson(collection),
                ContentType.APPLICATION_JSON)

        httpPost.setHeader("x-api-key", getPrivateToken())
        httpPost.entity = requestEntity
        val responseHandler = StringResponseHandler()

        try {
            val returnValue = httpClient.execute(httpPost, responseHandler)
            if (StringUtils.isNotBlank(returnValue) && returnValue.contains("collection")) {
                val returnObj = GsonUtils.parseToJsonTree(returnValue)
                val collectionName = returnObj?.asJsonObject?.get("collection")
                        ?.asJsonObject?.get("name")?.asString
                if (StringUtils.isNotBlank(collectionName)) {
                    logger!!.info("Imported as collection:$collectionName")
                    return true
                }
            }

            if (returnValue != null
                    && returnValue.contains("AuthenticationError")
                    && returnValue.contains("Invalid API Key")) {
                logger!!.error("Authentication failed!")
                return false
            }

            logger!!.error("Response:$returnValue")
            return false
        } catch (e: Throwable) {
            logger!!.error("Post failed:" + ExceptionUtils.getStackTrace(e))
            return false
        }
    }

    fun updateCollection(collectionId: String, apiInfo: HashMap<String, Any?>): Boolean {

        val httpPost = HttpPut("$COLLECTION/$collectionId")
        val collection: HashMap<String, Any?> = HashMap()
        collection["collection"] = apiInfo

        val requestEntity = StringEntity(GsonUtils.toJson(collection),
                ContentType.APPLICATION_JSON)

        httpPost.setHeader("x-api-key", getPrivateToken())
        httpPost.entity = requestEntity
        val responseHandler = StringResponseHandler()

        try {
            val returnValue = httpClient!!.execute(httpPost, responseHandler)
            if (StringUtils.isNotBlank(returnValue) && returnValue.contains("collection")) {
                val returnObj = GsonUtils.parseToJsonTree(returnValue)
                val collectionName = returnObj?.asJsonObject?.get("collection")
                        ?.asJsonObject?.get("name")?.asString
                if (StringUtils.isNotBlank(collectionName)) {
                    logger!!.info("Imported as collection:$collectionName")
                    return true
                }
            }

            if (returnValue != null
                    && returnValue.contains("AuthenticationError")
                    && returnValue.contains("Invalid API Key")) {
                logger!!.error("Authentication failed!")
                return false
            }

            logger!!.error("Response:$returnValue")
            return false
        } catch (e: Throwable) {
            logger!!.error("Post failed:" + ExceptionUtils.getStackTrace(e))
            return false
        }
    }

    fun getAllCollection(): ArrayList<Map<String, Any?>>? {
        val httpGet = HttpGet(COLLECTION)
        httpGet.setHeader("x-api-key", getPrivateToken())
        val responseHandler = StringResponseHandler()

        try {
            val returnValue = httpClient!!.execute(httpGet, responseHandler)
            if (StringUtils.isNotBlank(returnValue) && returnValue.contains("collections")) {
                val returnObj = GsonUtils.parseToJsonTree(returnValue)
                val collections = returnObj?.asJsonObject?.get("collections")
                        ?.asJsonArray ?: return null
                val collectionList: ArrayList<Map<String, Any?>> = ArrayList()
                collections.forEach { collectionList.add(it.asMap()) }
                return collectionList
            }

            if (returnValue != null
                    && returnValue.contains("AuthenticationError")
                    && returnValue.contains("Invalid API Key")) {
                logger!!.error("Authentication failed!")
                return null
            }

            logger!!.error("Response:$returnValue")
            return null
        } catch (e: Throwable) {
            logger!!.error("Load collections failed:" + ExceptionUtils.getStackTrace(e))
            return null
        }
    }

    fun getCollectionInfo(collectionId: String): HashMap<String, Any?>? {
        val httpGet = HttpGet("$COLLECTION/$collectionId")
        httpGet.setHeader("x-api-key", getPrivateToken())
        val responseHandler = StringResponseHandler()

        try {
            val returnValue = httpClient!!.execute(httpGet, responseHandler)
            if (StringUtils.isNotBlank(returnValue) && returnValue.contains("collection")) {
                val returnObj = GsonUtils.parseToJsonTree(returnValue)
                return returnObj?.asJsonObject?.get("collection")
                        ?.asMap()
            }

            if (returnValue != null
                    && returnValue.contains("AuthenticationError")
                    && returnValue.contains("Invalid API Key")) {
                logger!!.error("Authentication failed!")
                return null
            }

            logger!!.error("Response:$returnValue")
            return null
        } catch (e: Throwable) {
            logger!!.error("Load collection info failed:" + ExceptionUtils.getStackTrace(e))
            return null
        }
    }

    companion object {
        val POSTMANHOST = "https://api.getpostman.com"
        val IMPOREDAPI = "$POSTMANHOST/import/exported"
        val COLLECTION = "$POSTMANHOST/collections"
    }
}
