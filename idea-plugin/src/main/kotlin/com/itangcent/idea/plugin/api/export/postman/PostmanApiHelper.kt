package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.StringResponseHandler
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.setting.SettingManager
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClients


class PostmanApiHelper {

    @Inject
    private val settingManager: SettingManager? = null

    @Inject
    private val logger: Logger? = null

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

        val httpPost = HttpPost(CREATECOLLECTION)
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

    companion object {
        val POSTMANHOST = "https://api.getpostman.com"
        val IMPOREDAPI = "${POSTMANHOST}/import/exported"
        val CREATECOLLECTION = "${POSTMANHOST}/collections"
    }
}