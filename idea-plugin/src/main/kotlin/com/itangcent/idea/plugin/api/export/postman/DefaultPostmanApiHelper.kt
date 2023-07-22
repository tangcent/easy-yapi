package com.itangcent.idea.plugin.api.export.postman

import com.google.gson.JsonObject
import com.google.gson.internal.LazilyParsedNumber
import com.google.inject.Inject
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.*
import com.itangcent.http.HttpClient
import com.itangcent.http.HttpRequest
import com.itangcent.http.HttpResponse
import com.itangcent.http.contentType
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.idea.utils.resolveGsonLazily
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.*
import com.itangcent.intellij.extend.rx.Throttle
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.suv.http.HttpClientProvider
import org.apache.http.entity.ContentType
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

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
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    @Inject
    private lateinit var logger: Logger

    @Inject
    private val httpClientProvider: HttpClientProvider? = null

    @Inject
    protected lateinit var actionContext: ActionContext

    private val apiThrottle: Throttle = ThrottleHelper().build("postman_api")

    protected open fun beforeRequest(request: HttpRequest) {
        apiThrottle.acquireGreedy(LIMIT_PERIOD_PRE_REQUEST)
    }

    private fun onErrorResponse(response: HttpResponse) {

        val returnValue = response.string()
        if (returnValue.isNullOrBlank()) {
            logger.error("No Response For: ${response.request().url()}")
            return
        }
        if (returnValue.contains("AuthenticationError")
            && returnValue.contains("Invalid API Key")
        ) {
            logger.error("Authentication failed!")
            return
        }

        if (returnValue.contains("WLError")
            || returnValue.contains("attribute is invalid")
        ) {
            logger.error("Please try after turning off [build example] at Preferences(Settings) > Other Settings > EasyApi > Postman > build example")
            return
        }

        val returnObj = returnValue.asJsonElement()
        val errorName = returnObj
            .sub("error")
            .sub("name")
            ?.asString
        val errorMessage = (returnObj as? JsonObject)
            .sub("error")
            .sub("message")
            ?.asString

        if (response.code() == 429) {

            if (errorName == null) {
                logger.error("$errorMessage \n $LIMIT_ERROR_MSG")
                return
            } else {
                logger.error("[$errorName] $errorMessage \n $LIMIT_ERROR_MSG")
                return
            }
        }

        if (errorName != null) {
            logger.error("[$errorName] $errorMessage")
            return
        }

        logger.error("Error Response:$returnValue")
    }

    protected open fun getHttpClient(): HttpClient {
        return httpClientProvider!!.getHttpClient()
    }

    /**
     * @return collection id
     */
    override fun createCollection(collection: HashMap<String, Any?>, workspaceId: String?): Map<String, Any?>? {
        LOG.info("create collection in workspace $workspaceId to postman")
        val request = getHttpClient()
            .post(COLLECTION)
            .contentType(ContentType.APPLICATION_JSON)
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())
            .body(KV.by("collection", collection))

        workspaceId?.let { request.query("workspace", it) }

        try {
            beforeRequest(request)
            call(request).use { response ->
                val returnValue = response.string()
                if (returnValue.notNullOrEmpty() && returnValue!!.contains("collection")) {
                    val returnObj = returnValue.asJsonElement()
                    val collectionInfo = returnObj.sub("collection")?.asMap()
                    if (collectionInfo.notNullOrEmpty()) {
                        return collectionInfo
                    }
                }

                onErrorResponse(response)

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Post to $COLLECTION failed", e)
            return null
        }
    }

    override fun updateCollection(collectionId: String, collectionInfo: Map<String, Any?>): Boolean {
        LOG.info("update collection $collectionId to postman")
        if (doUpdateCollection(collectionId, collectionInfo)) {
            return true
        }
        try {
            logger.info("try fix collection.....")
            collectionInfo.toMutableMap()
                .also { tryFixCollection(it) }
                .let {
                    if (doUpdateCollection(collectionId, it)) {
                        return true
                    }
                }
        } catch (e: Exception) {
            logger.traceError("fix collection failed", e)

        }
        return false
    }

    @Suppress("UNCHECKED_CAST")
    fun tryFixCollection(apiInfo: MutableMap<String, Any?>) {
        if (apiInfo.containsKey("item")) {
            val items = apiInfo["item"] as List<*>? ?: return
            apiInfo["item"] = items
                .asSequence()
                .map { it as Map<String, Any?> }
                .map { it.asHashMap() }
                .onEach { item ->
                    tryFixCollection(item)
                }
                .toList()
        } else {
            val responses = apiInfo["response"] as List<*>?
            if (responses != null) {
                apiInfo["response"] = responses
                    .asSequence()
                    .map { it as Map<String, Any?> }
                    .map { it.asHashMap() }
                    .onEach { response ->
                        val responseCode = response["code"]
                        if (responseCode != null) {
                            response["code"] = when (responseCode) {
                                is Map<*, *> -> responseCode["value"].asInt() ?: 200
                                is LazilyParsedNumber -> responseCode.toInt()
                                is String -> responseCode.toInt()
                                else -> 200
                            }
                        }
                    }
                    .toList()
            }
        }
    }

    private fun doUpdateCollection(collectionId: String, apiInfo: Map<String, Any?>): Boolean {

        val request = getHttpClient().put("$COLLECTION/$collectionId")
            .contentType(ContentType.APPLICATION_JSON)
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())
            .body(GsonUtils.toJson(KV.by("collection", apiInfo)).resolveGsonLazily())

        try {
            beforeRequest(request)

            call(request).use { response ->
                val returnValue = response.string()
                if (returnValue.notNullOrEmpty() && returnValue!!.contains("collection")) {
                    val returnObj = returnValue.asJsonElement()
                    val collectionName = returnObj
                        .sub("collection")
                        .sub("name")
                        ?.asString
                    if (collectionName.notNullOrBlank()) {
                        return true
                    }
                }

                onErrorResponse(response)
                return false
            }
        } catch (e: Throwable) {
            logger.traceError("Post to $COLLECTION failed", e)

            return false
        }
    }

    override fun getAllCollection(): List<Map<String, Any?>>? {
        LOG.info("read all collection from postman")
        val request = getHttpClient().get(COLLECTION)
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())

        try {
            beforeRequest(request)
            call(request).use { response ->
                val returnValue = response.string()
                if (returnValue.notNullOrEmpty() && returnValue!!.contains("collections")) {
                    val returnObj = returnValue.asJsonElement()
                    val collections = returnObj.sub("collections")
                        ?.asJsonArray ?: return null
                    val collectionList: ArrayList<Map<String, Any?>> = ArrayList()
                    collections.forEach { collectionList.add(it.asMap()) }
                    return collectionList
                }

                onErrorResponse(response)

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load collections failed", e)

            return null
        }
    }

    override fun getCollectionByWorkspace(workspaceId: String): List<Map<String, Any?>>? {
        LOG.info("read collection in workspace [$workspaceId] from postman")
        val request = getHttpClient().get("$WORKSPACE/$workspaceId")
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())

        try {
            beforeRequest(request)
            call(request).use { response ->
                val returnValue = response.string()
                if (returnValue.notNullOrEmpty() && returnValue!!.contains("workspace")) {
                    val returnObj = returnValue.asJsonElement()
                    val collections = returnObj
                        .sub("workspace")
                        .sub("collections")
                        ?.asJsonArray ?: return arrayListOf()
                    val collectionList = arrayListOf<Map<String, Any?>>()
                    collections.forEach { collectionList.add(it.asMap()) }
                    return collectionList
                }

                onErrorResponse(response)

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load collections of workspace $workspaceId failed", e)
            return null
        }
    }

    override fun getCollectionInfo(collectionId: String): Map<String, Any?>? {
        LOG.info("read collection of $collectionId from postman")
        val request = getHttpClient().get("$COLLECTION/$collectionId")
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())
        try {
            beforeRequest(request)
            call(request).use { response ->
                val returnValue = response.string()

                if (returnValue.notNullOrEmpty() && returnValue!!.contains("collection")) {
                    val returnObj = returnValue.asJsonElement()
                    return returnObj
                        .sub("collection")
                        ?.asMap()
                }

                onErrorResponse(response)

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load collection info  failed", e)

            return null
        }
    }

    override fun getAllWorkspaces(): List<PostmanWorkspace>? {
        if (postmanSettingsHelper.getPrivateToken() == null) {
            return null
        }
        LOG.info("read allWorkspaces from postman")
        val request = getHttpClient().get(WORKSPACE)
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())

        try {
            beforeRequest(request)
            call(request).use { response ->
                val returnValue = response.string()
                if (returnValue.notNullOrEmpty() && returnValue!!.contains("workspaces")) {
                    val returnObj = returnValue.asJsonElement()
                    val workspaces = returnObj.sub("workspaces")
                        ?.asJsonArray ?: return null
                    val workspaceList = mutableListOf<PostmanWorkspace>()
                    workspaces
                        .forEach {
                            val res = it.asMap()
                            workspaceList.add(
                                PostmanWorkspace(
                                    res["id"] as String,
                                    res["name"] as String,
                                    res["type"] as String
                                )
                            )
                        }
                    return workspaceList
                }

                onErrorResponse(response)

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load workspaces failed", e)

            return null
        }
    }

    override fun getWorkspaceInfo(workspaceId: String): PostmanWorkspace? {
        LOG.info("read workspaceInfo of $workspaceId from postman")
        val request = getHttpClient().get("$WORKSPACE/$workspaceId")
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())

        try {
            beforeRequest(request)
            call(request).use { response ->
                val returnValue = response.string()
                if (returnValue.notNullOrEmpty() && returnValue!!.contains("workspace")) {
                    val returnObj = returnValue.asJsonElement()
                    return returnObj.sub("workspace")
                        ?.asMap()
                        ?.let {
                            PostmanWorkspace(
                                it["id"] as String,
                                it["name"] as String,
                                it["type"] as String
                            )
                        }
                }

                onErrorResponse(response)

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load workspace $workspaceId failed", e)
            return null
        }
    }

    override fun deleteCollectionInfo(collectionId: String): Map<String, Any?>? {
        LOG.info("delete collection $collectionId from postman")
        val request = getHttpClient().delete("$COLLECTION/$collectionId")
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())
        try {
            beforeRequest(request)
            call(request).use { response ->
                val returnValue = response.string()

                if (returnValue.notNullOrEmpty() && returnValue!!.contains("collection")) {
                    val returnObj = returnValue.asJsonElement()
                    return returnObj
                        .sub("collection")
                        ?.asMap()
                }

                onErrorResponse(response)

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("delete collection failed", e)
            return null
        }
    }

    private val semaphore = Semaphore(5)

    protected fun call(request: HttpRequest): HttpResponse {
        semaphore.acquire()
        try {
            return request.call()
        } finally {
            semaphore.release()
        }
    }

    companion object : Log() {
        const val POSTMAN_HOST = "https://api.getpostman.com"

        //const val IMPOREDAPI = "$POSTMANHOST/import/exported"
        const val COLLECTION = "$POSTMAN_HOST/collections"
        const val WORKSPACE = "$POSTMAN_HOST/workspaces"

        //the postman rate limit is 60 per/s
        //Just to be on the safe side,limit to 30 per/s
        private val LIMIT_PERIOD_PRE_REQUEST = TimeUnit.MINUTES.toMillis(1) / 30

        private const val LIMIT_ERROR_MSG = "API access rate limits are applied at a per-key basis in unit time.\n" +
                "Access to the API using a key is limited to 60 requests per minute.\n" +
                "And for free,The requests made to the Postman API was limited 1000.\n" +
                "You can see usage overview at:https://web.postman.co/usage"
    }

}