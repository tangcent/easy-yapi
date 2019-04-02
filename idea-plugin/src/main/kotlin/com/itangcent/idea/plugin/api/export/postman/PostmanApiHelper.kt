package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.TMResponseHandler
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

//        val httpGet = HttpGet(CREATECOLLECTION)
//        val responseHandler = TMResponseHandler()
//        httpGet.setHeader("x-api-key", getPrivateToken())
//        try {
//            val returnValue = httpClient.execute(httpGet, responseHandler) //调接口获取返回值时，必须用此方法
//            logger!!.info("response:$returnValue")
//        } catch (e: Throwable) {
//            logger!!.info("post failed:" + ExceptionUtils.getStackTrace(e))
//            return false
//        }
//        return false

        val httpPost = HttpPost(CREATECOLLECTION)
        val collection: HashMap<String, Any?> = HashMap()
        collection["collection"] = apiInfo
        //第三步：给httpPost设置JSON格式的参数
        val requestEntity = StringEntity(GsonUtils.toJson(collection),
                ContentType.APPLICATION_JSON)
//        requestEntity.setContentEncoding("UTF-8")
//        httpPost.setHeader("Content-Type", "application/json")
        httpPost.setHeader("x-api-key", getPrivateToken())
        httpPost.entity = requestEntity
        val responseHandler = TMResponseHandler()
        //第四步：发送HttpPost请求，获取返回值
        try {
            val returnValue = httpClient.execute(httpPost, responseHandler) //调接口获取返回值时，必须用此方法
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

//
//    fun importApiInfo(apiInfo: HashMap<String, Any?>): Boolean {
//
//        val httpClient = HttpClients.createDefault()
//
//        val httpPost = HttpPost(CREATECOLLECTION)
//
//        //自己生一个boundary
//        val boundary = "--------------------" + RandomStringUtils.randomAlphanumeric(24)
//
//        logger!!.info("boundary:[$boundary]")
//
//        val multipartEntity = MultipartEntityBuilder.create()
//                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
//                .setBoundary(boundary)
//                .setCharset(Charset.defaultCharset())
//                .addTextBody("type", "file")
//                .addBinaryBody("input", apiInfo.toByteArray(Charset.defaultCharset()))
//                .build()
//
//        httpPost.setHeader("Content-Type", "multipart/form-data; boundary=$boundary")
//        httpPost.setHeader("x-api-key", getPrivateToken())
//        httpPost.entity = multipartEntity
//        val responseHandler = BasicResponseHandler()
//        //第四步：发送HttpPost请求，获取返回值
//
//        logger!!.info("start post")
//        try {
//            val returnValue = httpClient.execute(httpPost, responseHandler)
//            logger!!.info("response:$returnValue")
//        } catch (e: Throwable) {
//            logger!!.info("post failed:" + ExceptionUtils.getStackTrace(e))
//            return false
//        }
//        return false
//    }

//    fun importApiInfo(apiInfo: String): Boolean {
//        val httpClient = HttpClients.createDefault()
//
//        val httpPost = HttpPost(imporedApi)
//
//        //第三步：给httpPost设置JSON格式的参数
//        val requestEntity = StringEntity(apiInfo, "utf-8")
//        requestEntity.setContentEncoding("UTF-8")
//        httpPost.setHeader("Content-type", "application/json")
//        httpPost.setHeader("x-api-key", getPrivateToken())
//        httpPost.entity = requestEntity
//        val responseHandler = BasicResponseHandler()
//        //第四步：发送HttpPost请求，获取返回值
//        val returnValue = httpClient.execute(httpPost, responseHandler) //调接口获取返回值时，必须用此方法
//        logger!!.info("response:$returnValue")
//        return false
//    }

    companion object {
        val POSTMANHOST = "https://api.getpostman.com"
        val IMPOREDAPI = "${POSTMANHOST}/import/exported"
        val CREATECOLLECTION = "${POSTMANHOST}/collections"
    }
}