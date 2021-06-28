package com.itangcent.idea.plugin.api.export.postman

import com.google.gson.internal.LazilyParsedNumber
import com.google.inject.Inject
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.KV
import com.itangcent.common.utils.asInt
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.http.HttpClient
import com.itangcent.http.HttpRequest
import com.itangcent.http.HttpResponse
import com.itangcent.http.contentType
import com.itangcent.idea.plugin.api.export.ReservedResponseHandle
import com.itangcent.idea.plugin.api.export.core.StringResponseHandler
import com.itangcent.idea.plugin.api.export.reserved
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.*
import com.itangcent.intellij.extend.rx.Throttle
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.suv.http.HttpClientProvider
import org.apache.http.entity.ContentType
import java.util.concurrent.Semaphore
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
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    @Inject
    private val logger: Logger? = null

    @Inject
    private val httpClientProvider: HttpClientProvider? = null

    @Inject
    protected lateinit var actionContext: ActionContext

    @Inject
    protected lateinit var moduleHelper: ModuleHelper

    private val apiThrottle: Throttle = ThrottleHelper().build("postman_api")

    protected open fun beforeRequest(request: HttpRequest) {
        apiThrottle.acquireGreedy(LIMIT_PERIOD_PRE_REQUEST)
    }

    private fun onErrorResponse(response: HttpResponse) {

        val returnValue = response.string()
        if (returnValue.isNullOrBlank()) {
            logger!!.error("No Response For:${response.request().url()}")
            return
        }
        if (returnValue.contains("AuthenticationError")
            && returnValue.contains("Invalid API Key")
        ) {
            logger!!.error("Authentication failed!")
            return
        }
        val returnObj = returnValue.asJsonElement()
        val errorName = returnObj
            .sub("error")
            .sub("name")
            ?.asString
        val errorMessage = returnObj?.asJsonObject
            .sub("error")
            .sub("message")
            ?.asString

        if (response.code() == 429) {

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

    protected open fun getHttpClient(): HttpClient {
        return httpClientProvider!!.getHttpClient()
    }

    /**
     * @return collection id
     */
    override fun createCollection(collection: HashMap<String, Any?>): HashMap<String, Any?>? {
        // get workspace
        val module = actionContext.callInReadUI { moduleHelper.findModuleByPath(ActionUtils.findCurrentPath()) }
        val workspace = module?.let { postmanSettingsHelper.getWorkspace(it, false) }
        val request = getHttpClient()
            .post(COLLECTION)
            .contentType(ContentType.APPLICATION_JSON)
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())
            .body(KV.by("collection", collection))

        if (workspace != null) {
            request.query("workspace", workspace)
        }

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
            logger!!.traceError("Post to $COLLECTION failed", e)
            return null
        }
    }

    override fun updateCollection(collectionId: String, collectionInfo: HashMap<String, Any?>): Boolean {
        if (doUpdateCollection(collectionId, collectionInfo)) {
            return true
        }
        try {
            logger!!.info("try fix collection.....")
            tryFixCollection(collectionInfo)
            if (doUpdateCollection(collectionId, collectionInfo)) {
                return true
            }
        } catch (e: Exception) {
            logger!!.traceError("fix collection failed", e)

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
                            when (responseCode) {
                                is Map<*, *> -> (response as MutableMap<String, Any?>)["code"] =
                                    responseCode["value"].asInt() ?: 200
                                is LazilyParsedNumber -> (response as MutableMap<String, Any?>)["code"] = responseCode.toInt()
                                is String -> (response as MutableMap<String, Any?>)["code"] = responseCode.toInt()
                            }
                        }
                    }
                    .toList()
            }
        }
    }

    private fun doUpdateCollection(collectionId: String, apiInfo: HashMap<String, Any?>): Boolean {

        val request = getHttpClient().put("$COLLECTION/$collectionId")
            .contentType(ContentType.APPLICATION_JSON)
            .header("x-api-key", postmanSettingsHelper.getPrivateToken())
            .body(KV.by("collection", apiInfo))

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
            logger!!.traceError("Post to $COLLECTION failed", e)

            return false
        }
    }

    override fun getAllCollection(): ArrayList<HashMap<String, Any?>>? {
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
                    val collectionList: ArrayList<HashMap<String, Any?>> = ArrayList()
                    collections.forEach { collectionList.add(it.asMap()) }
                    return collectionList
                }

                onErrorResponse(response)

                return null
            }
        } catch (e: Throwable) {
            logger!!.traceError("Load collections failed", e)

            return null
        }
    }

    override fun getCollectionInfo(collectionId: String): HashMap<String, Any?>? {
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
            logger!!.traceError("Load collection info  failed", e)

            return null
        }
    }

    override fun getAllWorkspaces(): List<PostmanWorkspace>? {
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
            logger!!.traceError("Load workspaces failed", e)

            return null
        }
    }

    override fun getWorkspaceInfo(workspaceId: String): PostmanWorkspace? {
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
            logger!!.traceError("Load workspace $workspaceId failed", e)
            return null
        }
    }

    override fun deleteCollectionInfo(collectionId: String): HashMap<String, Any?>? {
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
            logger!!.traceError("delete collection failed", e)
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

    protected fun reservedResponseHandle(): ReservedResponseHandle<String> {
        return StringResponseHandler.DEFAULT_RESPONSE_HANDLER.reserved()
    }

    companion object {
        const val POSTMANHOST = "https://api.getpostman.com"
        val IMPOREDAPI = "$POSTMANHOST/import/exported"
        const val COLLECTION = "$POSTMANHOST/collections"
        const val WORKSPACE = "$POSTMANHOST/workspaces"

        //the postman rate limit is 60 per/s
        //Just to be on the safe side,limit to 30 per/s
        private val LIMIT_PERIOD_PRE_REQUEST = TimeUnit.MINUTES.toMillis(1) / 30

        private const val LIMIT_ERROR_MSG = "API access rate limits are applied at a per-key basis in unit time.\n" +
                "Access to the API using a key is limited to 60 requests per minute.\n" +
                "And for free,The requests made to the Postman API was limited 1000.\n" +
                "You can see usage overview at:https://web.postman.co/usage"
    }

}

