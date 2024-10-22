package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.inject.ImplementedBy
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.logger
import com.itangcent.intellij.extend.sub


@ImplementedBy(DefaultYapiApiHelper::class)
interface YapiApiHelper {

    //apis--------------------------------------------------------------

    fun getApiInfo(token: String, id: String): JsonObject?

    fun findApi(token: String, catId: String, apiName: String): String?

    fun findApis(token: String, catId: String): List<Any?>?

    fun listApis(token: String, catId: String, limit: Int?): JsonArray?

    fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean

    fun getApiWeb(module: String, cartName: String, apiName: String): String?

    //carts--------------------------------------------------------------

    fun findCart(token: String, name: String): String?

    fun findCarts(projectId: String, token: String): List<Any?>?

    fun addCart(privateToken: String, name: String, desc: String): Boolean

    fun addCart(projectId: String, token: String, name: String, desc: String): Boolean

    fun getCartWeb(projectId: String, catId: String): String?

    fun findCartWeb(module: String, cartName: String): String?

    //projects--------------------------------------------------------------

    fun getProjectIdByToken(token: String): String?

    fun getProjectInfo(token: String, projectId: String?): JsonObject?

    fun getProjectInfo(token: String): JsonObject?

    fun copyApi(from: Map<String, String>, target: Map<String, String>)

}

fun YapiApiHelper.listApis(token: String, catId: String): JsonArray? {
    return this.listApis(token, catId, null)
}

fun YapiApiHelper.existed(apiInfo: HashMap<String, Any?>): Boolean {
    return this.findExistApi(apiInfo) != null
}

fun YapiApiHelper.findExistApi(apiInfo: HashMap<String, Any?>): JsonObject? {
    val path = apiInfo["path"] as? String ?: return null
    val method = apiInfo["method"] as? String ?: return null
    val token = apiInfo["token"] as? String ?: return null
    return this.findExistApi(token, path, method)
}

fun YapiApiHelper.findExistApi(token: String, path: String, method: String): JsonObject? {
    val projectId: String = this.getProjectIdByToken(token) ?: return null
    val carts = this.findCarts(projectId, token) ?: return null
    for (cart in carts) {
        val catId = (cart as? Map<*, *>)?.get("_id")?.toString() ?: continue
        val api = this.listApis(token, catId, null)?.find { api ->
            api.sub("path")?.asString == path && api.sub("method")?.asString == method
        }
        if (api != null) {
            return api as JsonObject
        }
    }
    return null
}

fun YapiApiHelper.getCartForFolder(folder: Folder, privateToken: String): CartInfo? {

    val name: String = folder.name ?: "anonymous"

    var cartId: String?

    //try find existed cart.
    try {
        cartId = findCart(privateToken, name)
    } catch (e: Exception) {
        ActionContext.getContext()?.logger()
            ?.traceError("error to find cart [$name]", e)
        return null
    }

    //create new cart.
    if (cartId == null) {
        if (addCart(privateToken, name, folder.attr ?: "")) {
            cartId = findCart(privateToken, name)
        } else {
            //failed
            return null
        }
    }

    val cartInfo = CartInfo()
    cartInfo.cartId = cartId
    cartInfo.cartName = name
    cartInfo.privateToken = privateToken

    return cartInfo
}