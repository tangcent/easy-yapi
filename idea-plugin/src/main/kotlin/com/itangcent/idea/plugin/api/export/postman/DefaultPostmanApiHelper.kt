package com.itangcent.idea.plugin.api.export.postman

import com.google.gson.internal.LazilyParsedNumber
import com.google.inject.Inject
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.*
import com.itangcent.http.HttpRequest
import com.itangcent.http.HttpResponse
import com.itangcent.http.RawContentType
import com.itangcent.http.contentType
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.idea.utils.GsonExUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.acquireGreedy
import com.itangcent.intellij.extend.asJsonElement
import com.itangcent.intellij.extend.asMap
import com.itangcent.intellij.extend.rx.throttle
import com.itangcent.intellij.extend.sub
import com.itangcent.intellij.logger.Logger
import com.itangcent.suv.http.HttpClientProvider
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

    /**
     * Handles error responses from the Postman API.
     * Logs the error and provides appropriate messages.
     */
    private fun HttpResponse.onError() {
        val returnValue = string()
        if (returnValue.isNullOrBlank()) {
            logger.error("No Response For: ${request().url()}")
            return
        }
        if (returnValue.contains("AuthenticationError")
            && returnValue.contains("Invalid API Key")
        ) {
            logger.error("Authentication failed!")
            return
        }

        if (
            (returnValue.contains("WLError")
                    || returnValue.contains("attribute is invalid"))
            && postmanSettingsHelper.buildExample()
        ) {
            logger.error("Please try after turning off [build example] at Preferences(Settings) > Other Settings > EasyApi > Postman > build example")
            return
        }

        val returnObj = returnValue.asJsonElement()
        val errorObj = returnObj.sub("error")
        val errorName = errorObj.sub("name")?.asString
        val errorMessage = errorObj.sub("message")?.asString

        if (code() == 429) {
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

    protected open val httpClient by lazy { httpClientProvider!!.getHttpClient() }

    /**
     * Creates a collection in Postman.
     * @return collection id
     */
    override fun createCollection(collection: HashMap<String, Any?>, workspaceId: String?): Map<String, Any?>? {
        LOG.info("create collection in workspace $workspaceId to postman")
        val request = httpClient
            .post(COLLECTION)
            .contentType(RawContentType.APPLICATION_JSON)
            .body(linkedMapOf("collection" to collection))

        workspaceId?.let { request.query("workspace", it) }

        try {
            request.callPostman().use { response ->
                val returnValue = response.string()
                if (returnValue?.contains("collection") == true) {
                    val returnObj = returnValue.asJsonElement()
                    val collectionInfo = returnObj.sub("collection")?.asMap()
                    if (collectionInfo.notNullOrEmpty()) {
                        return collectionInfo
                    }
                }

                response.onError()

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Post to $COLLECTION failed", e)
            return null
        }
    }

    /**
     * Updates a collection in Postman.
     * @return true if successful, false otherwise.
     */
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

    /**
     * Attempts to fix the collection data by ensuring proper types for certain fields.
     */
    @Suppress("UNCHECKED_CAST")
    private fun tryFixCollection(apiInfo: MutableMap<String, Any?>) {
        if (apiInfo.containsKey("item")) {
            val items = apiInfo["item"] as? List<*>? ?: return
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

    /**
     * Performs the actual update of the collection in Postman.
     * @return true if successful, false otherwise.
     */
    private fun doUpdateCollection(collectionId: String, apiInfo: Map<String, Any?>): Boolean {

        val request = httpClient.put("$COLLECTION/$collectionId")
            .contentType(RawContentType.APPLICATION_JSON)
            .body(GsonUtils.toJson(linkedMapOf("collection" to apiInfo)).apply { GsonExUtils.resolveGsonLazily(this) })

        try {
            request.callPostman().use { response ->
                val returnValue = response.string()
                if (returnValue?.contains("collection") == true) {
                    val returnObj = returnValue.asJsonElement()
                    val collectionName = returnObj
                        .sub("collection")
                        .sub("name")
                        ?.asString
                    if (collectionName.notNullOrBlank()) {
                        return true
                    }
                }

                response.onError()
                return false
            }
        } catch (e: Throwable) {
            logger.traceError("Post to $COLLECTION failed", e)

            return false
        }
    }

    /**
     * Retrieves all collections from Postman.
     * @return list of collections if successful, null otherwise.
     */
    override fun getAllCollection(): List<Map<String, Any?>>? {
        LOG.info("read all collection from postman")
        val request = httpClient.get(COLLECTION)

        try {
            request.callPostman().use { response ->
                val returnValue = response.string()
                if (returnValue?.contains("collections") == true) {
                    val returnObj = returnValue.asJsonElement()
                    val collections = returnObj.sub("collections")
                        ?.asJsonArray ?: return null
                    val collectionList: ArrayList<Map<String, Any?>> = ArrayList()
                    collections.forEach { collectionList.add(it.asMap()) }
                    return collectionList
                }

                response.onError()

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load collections failed", e)

            return null
        }
    }

    /**
     * Retrieves all collections in a specific workspace from Postman.
     * @return list of collections if successful, null otherwise.
     */
    override fun getCollectionByWorkspace(workspaceId: String): List<Map<String, Any?>>? {
        LOG.info("read collection in workspace [$workspaceId] from postman")
        val request = httpClient.get("$WORKSPACE/$workspaceId")

        try {
            request.callPostman().use { response ->
                val returnValue = response.string()
                if (returnValue?.contains("workspace") == true) {
                    val returnObj = returnValue.asJsonElement()
                    val collections = returnObj
                        .sub("workspace")
                        .sub("collections")
                        ?.asJsonArray ?: return arrayListOf()
                    val collectionList = arrayListOf<Map<String, Any?>>()
                    collections.forEach { collectionList.add(it.asMap()) }
                    return collectionList
                }

                response.onError()

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load collections of workspace $workspaceId failed", e)
            return null
        }
    }

    /**
     * Retrieves information about a specific collection from Postman.
     * @return collection information if successful, null otherwise.
     */
    override fun getCollectionInfo(collectionId: String): Map<String, Any?>? {
        LOG.info("read collection of $collectionId from postman")
        val request = httpClient.get("$COLLECTION/$collectionId")
        try {
            request.callPostman().use { response ->
                val returnValue = response.string()

                if (returnValue?.contains("collection") == true) {
                    val returnObj = returnValue.asJsonElement()
                    return returnObj
                        .sub("collection")
                        ?.asMap()
                }

                response.onError()

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load collection info  failed", e)

            return null
        }
    }

    /**
     * Retrieves all workspaces from Postman.
     * @return list of workspaces if successful, null otherwise.
     */
    override fun getAllWorkspaces(): List<PostmanWorkspace>? {
        if (postmanSettingsHelper.getPrivateToken() == null) {
            return null
        }
        LOG.info("read allWorkspaces from postman")
        val request = httpClient.get(WORKSPACE)

        try {
            request.callPostman().use { response ->
                val returnValue = response.string()
                if (returnValue?.contains("workspaces") == true) {
                    val returnObj = returnValue.asJsonElement()
                    val workspaces = returnObj.sub("workspaces")
                        ?.asJsonArray ?: return null
                    return workspaces
                        .map { it.asMap() }
                        .map { it.parsePostmanWorkspace() }
                }

                response.onError()

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load workspaces failed", e)

            return null
        }
    }

    /**
     * Retrieves information about a specific workspace from Postman.
     * @return workspace information if successful, null otherwise.
     */
    override fun getWorkspaceInfo(workspaceId: String): PostmanWorkspace? {
        LOG.info("read workspaceInfo of $workspaceId from postman")
        val request = httpClient.get("$WORKSPACE/$workspaceId")

        try {
            request.callPostman().use { response ->
                val returnValue = response.string()
                if (returnValue?.contains("workspace") == true) {
                    val returnObj = returnValue.asJsonElement()
                    return returnObj.sub("workspace")
                        ?.asMap()
                        ?.parsePostmanWorkspace()
                }

                response.onError()

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("Load workspace $workspaceId failed", e)
            return null
        }
    }

    /**
     * parse a map into a PostmanWorkspace object.
     */
    private fun Map<String, Any?>.parsePostmanWorkspace() = PostmanWorkspace(
        id = this["id"] as String,
        name = this["name"] as String,
        type = this["type"] as String
    )

    /**
     * Deletes a collection in Postman.
     * @return collection information if successful, null otherwise.
     */
    override fun deleteCollectionInfo(collectionId: String): Map<String, Any?>? {
        LOG.info("delete collection $collectionId from postman")
        val request = httpClient.delete("$COLLECTION/$collectionId")
        try {
            request.callPostman().use { response ->
                val returnValue = response.string()

                if (returnValue?.contains("collection") == true) {
                    val returnObj = returnValue.asJsonElement()
                    return returnObj
                        .sub("collection")
                        ?.asMap()
                }

                response.onError()

                return null
            }
        } catch (e: Throwable) {
            logger.traceError("delete collection failed", e)
            return null
        }
    }

    private val apiThrottle = throttle()

    private val semaphore = Semaphore(5)

    /**
     * Executes a Postman API call with throttling and concurrency control.
     */
    private fun HttpRequest.callPostman(): HttpResponse {
        //throttling API requests to avoid hitting rate limits.
        apiThrottle.acquireGreedy(LIMIT_PERIOD_PRE_REQUEST)

        semaphore.acquire()
        try {
            return header("x-api-key", postmanSettingsHelper.getPrivateToken())
                .call()
        } finally {
            semaphore.release()
        }
    }

    companion object : Log() {
        private const val POSTMAN_HOST = "https://api.getpostman.com"

        //const val IMPOREDAPI = "$POSTMANHOST/import/exported"
        const val COLLECTION = "$POSTMAN_HOST/collections"
        const val WORKSPACE = "$POSTMAN_HOST/workspaces"

        // The Postman rate limit is 60 per second.
        // To be safe, limit it to 30 per second.
        private val LIMIT_PERIOD_PRE_REQUEST = TimeUnit.MINUTES.toMillis(1) / 30

        private const val LIMIT_ERROR_MSG = "API access rate limits are applied at a per-key basis in unit time.\n" +
                "Access to the API using a key is limited to 60 requests per minute.\n" +
                "And for free,The requests made to the Postman API was limited 1000.\n" +
                "You can see usage overview at:https://web.postman.co/usage"
    }

}