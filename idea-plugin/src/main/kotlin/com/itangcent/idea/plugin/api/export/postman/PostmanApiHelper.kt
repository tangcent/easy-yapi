package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.StringResponseHandler
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.acquireGreedy
import com.itangcent.intellij.extend.asMap
import com.itangcent.intellij.extend.rx.Throttle
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.setting.SettingManager
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.client.HttpClient
import org.apache.http.client.ResponseHandler
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients
import java.util.concurrent.TimeUnit


/**
 * Postman API: https://docs.api.getpostman.com
 *The Postman API allows you to programmatically
 * access data stored in Postman account with ease.
 *
 * Rate Limits:https://docs.api.getpostman.com/#rate-limits
 * API access rate limits are applied at a per-key basis in unit time.
 * Access to the API using a key is limited to 60 requests per minute
 */
class PostmanApiHelper {

    @Inject
    private val settingManager: SettingManager? = null

    @Inject
    private val logger: Logger? = null

    @Inject
    private val httpClient: HttpClient? = null

    private val apiThrottle: Throttle = ThrottleHelper().build("postman_api")

    fun hasPrivateToken(missingNotification: (() -> Unit)? = MISSING_NOTIFICATION): Boolean {
        return if (getPrivateToken() != null) {
            true
        } else {
            missingNotification?.invoke()
            false
        }
    }

    private fun getPrivateToken(): String? {
        val setting = settingManager!!.getSetting(POSTMANHOST) ?: return null
        return if (StringUtils.isNotBlank(setting.privateToken)) {
            setting.privateToken
        } else {
            null
        }
    }

    private fun beforeRequest() {
        apiThrottle.acquireGreedy(LIMIT_PERIOD_PRE_REQUEST)
    }

    fun importApiInfo(apiInfo: HashMap<String, Any?>, responseHandler: ResponseHandler<String> = Companion.default_response_handler): Boolean {

        val httpClient = HttpClients.createDefault()

        val httpPost = HttpPost(COLLECTION)
        val collection: HashMap<String, Any?> = HashMap()
        collection["collection"] = apiInfo

        val requestEntity = StringEntity(GsonUtils.toJson(collection),
                ContentType.APPLICATION_JSON)

        httpPost.setHeader("x-api-key", getPrivateToken())
        httpPost.entity = requestEntity

        try {
            beforeRequest()
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

    fun updateCollection(collectionId: String, apiInfo: HashMap<String, Any?>, responseHandler: ResponseHandler<String> = Companion.default_response_handler): Boolean {

        val httpPost = HttpPut("$COLLECTION/$collectionId")
        val collection: HashMap<String, Any?> = HashMap()
        collection["collection"] = apiInfo

        val requestEntity = StringEntity(GsonUtils.toJson(collection),
                ContentType.APPLICATION_JSON)

        httpPost.setHeader("x-api-key", getPrivateToken())
        httpPost.entity = requestEntity

        try {
            beforeRequest()
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

    fun getAllCollection(responseHandler: ResponseHandler<String> = Companion.default_response_handler): ArrayList<HashMap<String, Any?>>? {
        val httpGet = HttpGet(COLLECTION)
        httpGet.setHeader("x-api-key", getPrivateToken())

        try {
            beforeRequest()
            val returnValue = httpClient!!.execute(httpGet, responseHandler)
            if (StringUtils.isNotBlank(returnValue) && returnValue.contains("collections")) {
                val returnObj = GsonUtils.parseToJsonTree(returnValue)
                val collections = returnObj?.asJsonObject?.get("collections")
                        ?.asJsonArray ?: return null
                val collectionList: ArrayList<HashMap<String, Any?>> = ArrayList()
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

    fun getCollectionInfo(collectionId: String, responseHandler: ResponseHandler<String> = Companion.default_response_handler): HashMap<String, Any?>? {
        val httpGet = HttpGet("$COLLECTION/$collectionId")
        httpGet.setHeader("x-api-key", getPrivateToken())
        try {
            beforeRequest()
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
        private val MISSING_NOTIFICATION: () -> Unit = {
            val logger = ActionContext.getContext()!!.instance(Logger::class)
            logger.info("PrivateToken of postman not be setting")
            logger.info("To enable automatically import to postman you could set privateToken" +
                    " of host [https://api.getpostman.com] in \"File -> Other Setting -> EasyApiSetting\"")
            logger.info("If you do not have a privateToken of postman, you can easily generate one by heading over to the" +
                    " Postman Integrations Dashboard [https://go.postman.co/integrations/services/pm_pro_api].")
        }
        private val default_response_handler = StringResponseHandler()
        //the postman rate limit is 60 per/s
        //Just to be on the safe side,limit to 30 per/s
        private val LIMIT_PERIOD_PRE_REQUEST = TimeUnit.MINUTES.toMillis(1) / 30
    }
}
