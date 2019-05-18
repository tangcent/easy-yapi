package com.itangcent.idea.plugin.api.export.postman

import com.google.gson.internal.LazilyParsedNumber
import com.google.inject.Inject
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.ReservedResponseHandle
import com.itangcent.idea.plugin.api.export.ReservedResult
import com.itangcent.idea.plugin.api.export.StringResponseHandler
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.extend.acquireGreedy
import com.itangcent.intellij.extend.asHashMap
import com.itangcent.intellij.extend.asMap
import com.itangcent.intellij.extend.rx.Throttle
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.logger.Logger
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import java.util.concurrent.TimeUnit
import kotlin.streams.toList

/**
 * Postman API: https://docs.api.getpostman.com
 * The Postman API allows you to programmatically
 * access data stored in Postman account with ease.
 *
 * Rate Limits:https://docs.api.getpostman.com/#rate-limits
 * API access rate limits are applied at a per-key basis in unit time.
 * Access to the API using a key is limited to 60 requests per minute
 *
 * Usage Overview: https://web.postman.co/usage
 * For free,The requests made to the Postman API was limited 1000
 * User can upgrade to increase your resource limits.
 */
open class DefaultPostmanApiHelper : PostmanApiHelper {

    @Inject
    private val settingBinder: SettingBinder? = null

    @Inject
    private val logger: Logger? = null

    @Inject
    private val httpClient: HttpClient? = null

    private val apiThrottle: Throttle = ThrottleHelper().build("postman_api")

    override fun hasPrivateToken(): Boolean {
        return !getPrivateToken().isNullOrEmpty()
    }

    override fun getPrivateToken(): String? {
        return settingBinder!!.read().postmanToken?.trim()
    }

    override fun setPrivateToken(postmanPrivateToken: String) {
        val settings = settingBinder!!.read()
        settings.postmanToken = postmanPrivateToken
        settingBinder.save(settings)
    }

    private fun beforeRequest() {
        apiThrottle.acquireGreedy(LIMIT_PERIOD_PRE_REQUEST)
    }

    private fun onErrorResponse(result: ReservedResult<String>) {

        val returnValue = result.result()
        if (returnValue.contains("AuthenticationError")
                && returnValue.contains("Invalid API Key")) {
            logger!!.error("Authentication failed!")
            return
        }
        val returnObj = GsonUtils.parseToJsonTree(returnValue)
        val errorName = returnObj?.asJsonObject
                ?.get("error")
                ?.asJsonObject
                ?.get("name")
                ?.asString
        val errorMessage = returnObj?.asJsonObject
                ?.get("error")
                ?.asJsonObject
                ?.get("message")
                ?.asString

        if (result.status() == 429) {

            if (errorName == null) {
                logger!!.error("$errorMessage \n $LIMIT_ERROR_MSG")
                return
            } else {
                logger!!.error("[$errorName] $errorMessage \n $LIMIT_ERROR_MSG")
                return
            }
        }

        if (errorName != null) {
            logger!!.error("[$errorName] $errorMessage")
            return
        }

        logger!!.error("Error Response:$returnValue")
    }

    /**
     * @return collection id
     */
    override fun createCollection(collection: HashMap<String, Any?>): HashMap<String, Any?>? {

        val httpClient = HttpClients.createDefault()

        val httpPost = HttpPost(COLLECTION)

        val collectionWrap: HashMap<String, Any?> = HashMap()
        collectionWrap["collection"] = collection

        val requestEntity = StringEntity(GsonUtils.toJson(collectionWrap),
                ContentType.APPLICATION_JSON)

        httpPost.setHeader("x-api-key", getPrivateToken())
        httpPost.entity = requestEntity

        try {
            beforeRequest()
            val result = httpClient.execute(httpPost, reservedResponseHandle())
            val returnValue = result.result()
            if (StringUtils.isNotBlank(returnValue) && returnValue.contains("collection")) {
                val returnObj = GsonUtils.parseToJsonTree(returnValue)
                val collectionInfo = returnObj?.asJsonObject?.get("collection")?.asMap()
                if (!collectionInfo.isNullOrEmpty()) {
                    return collectionInfo
                }
            }

            onErrorResponse(result)

            return null
        } catch (e: Throwable) {
            logger!!.error("Post failed:" + ExceptionUtils.getStackTrace(e))
            return null
        }
    }

    override fun updateCollection(collectionId: String, apiInfo: HashMap<String, Any?>): Boolean {
        if (doUpdateCollection(collectionId, apiInfo)) {
            return true
        }
        try {
            logger!!.info("try fix collection.....")
            tryFixCollection(apiInfo)
            if (doUpdateCollection(collectionId, apiInfo)) {
                return true
            }
        } catch (e: Exception) {
            logger!!.info("fix collection failed:" + ExceptionUtils.getStackTrace(e))
        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    fun tryFixCollection(apiInfo: HashMap<String, Any?>) {
        if (apiInfo.containsKey("item")) {
            val items = apiInfo["item"] as List<*>? ?: return
            apiInfo["item"] = items
                    .stream()
                    .map { it as Map<String, Any?> }
                    .map { it.asHashMap() }
                    .peek { item ->
                        tryFixCollection(item)
                    }
                    .toList()
        } else {
            val responses = apiInfo["response"] as List<*>?
            if (responses != null) {
                apiInfo["response"] = responses
                        .stream()
                        .map { it as Map<String, Any?> }
                        .map { it.asHashMap() }
                        .peek { response ->
                            val responseCode = response["code"]
                            if (responseCode != null) {
                                if (responseCode is Map<*, *>) {
                                    (response as MutableMap<String, Any?>)["code"] = responseCode["value"] ?: 200
                                } else if (responseCode is LazilyParsedNumber) {
                                    (response as MutableMap<String, Any?>)["code"] = responseCode.toInt()
                                }
                            }
                        }
                        .toList()
            }
        }
    }

    fun doUpdateCollection(collectionId: String, apiInfo: HashMap<String, Any?>): Boolean {

        val httpPut = HttpPut("$COLLECTION/$collectionId")
        val collection: HashMap<String, Any?> = HashMap()
        collection["collection"] = apiInfo

        val requestEntity = StringEntity(GsonUtils.toJson(collection),
                ContentType.APPLICATION_JSON)

        httpPut.setHeader("x-api-key", getPrivateToken())
        httpPut.entity = requestEntity

        try {
            beforeRequest()

            val result = httpClient!!.execute(httpPut, reservedResponseHandle())
            val returnValue = result.result()
            if (StringUtils.isNotBlank(returnValue) && returnValue.contains("collection")) {
                val returnObj = GsonUtils.parseToJsonTree(returnValue)
                val collectionName = returnObj?.asJsonObject?.get("collection")
                        ?.asJsonObject?.get("name")?.asString
                if (StringUtils.isNotBlank(collectionName)) {
                    return true
                }
            }

            onErrorResponse(result)
            return false
        } catch (e: Throwable) {
            logger!!.error("Post failed:" + ExceptionUtils.getStackTrace(e))
            return false
        }
    }

    override fun getAllCollection(): ArrayList<HashMap<String, Any?>>? {
        val httpGet = HttpGet(COLLECTION)
        httpGet.setHeader("x-api-key", getPrivateToken())

        try {
            beforeRequest()
            val result = httpClient!!.execute(httpGet, reservedResponseHandle())
            val returnValue = result.result()
            if (StringUtils.isNotBlank(returnValue) && returnValue.contains("collections")) {
                val returnObj = GsonUtils.parseToJsonTree(returnValue)
                val collections = returnObj?.asJsonObject?.get("collections")
                        ?.asJsonArray ?: return null
                val collectionList: ArrayList<HashMap<String, Any?>> = ArrayList()
                collections.forEach { collectionList.add(it.asMap()) }
                return collectionList
            }

            onErrorResponse(result)

            return null
        } catch (e: Throwable) {
            logger!!.error("Load collections failed:" + ExceptionUtils.getStackTrace(e))
            return null
        }
    }

    override fun getCollectionInfo(collectionId: String): HashMap<String, Any?>? {
        val httpGet = HttpGet("$COLLECTION/$collectionId")
        httpGet.setHeader("x-api-key", getPrivateToken())
        try {
            beforeRequest()
            val result = httpClient!!.execute(httpGet, reservedResponseHandle())
            val returnValue = result.result()

            if (StringUtils.isNotBlank(returnValue) && returnValue.contains("collection")) {
                val returnObj = GsonUtils.parseToJsonTree(returnValue)
                return returnObj?.asJsonObject?.get("collection")
                        ?.asMap()
            }

            onErrorResponse(result)

            return null
        } catch (e: Throwable) {
            logger!!.error("Load collection info failed:" + ExceptionUtils.getStackTrace(e))
            return null
        }
    }

    private fun reservedResponseHandle(): ReservedResponseHandle<String> {
        return responseHandler
    }

    companion object {
        const val POSTMANHOST = "https://api.getpostman.com"
        val IMPOREDAPI = "$POSTMANHOST/import/exported"
        const val COLLECTION = "$POSTMANHOST/collections"
        private val DEFAULT_RESPONSE_HANDLER = StringResponseHandler()
        //the postman rate limit is 60 per/s
        //Just to be on the safe side,limit to 30 per/s
        private val LIMIT_PERIOD_PRE_REQUEST = TimeUnit.MINUTES.toMillis(1) / 30

        private const val LIMIT_ERROR_MSG = "API access rate limits are applied at a per-key basis in unit time.\n" +
                "Access to the API using a key is limited to 60 requests per minute.\n" +
                "And for free,The requests made to the Postman API was limited 1000.\n" +
                "You can see usage overview at:https://web.postman.co/usage"

        private val responseHandler = ReservedResponseHandle(StringResponseHandler())
    }

}

