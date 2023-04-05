package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.inject.ImplementedBy
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.sub
import com.itangcent.intellij.logger.Logger


@ImplementedBy(DefaultYapiApiHelper::class)
interface YapiApiHelper {

    //apis--------------------------------------------------------------

    fun getApiInfo(token: String, id: String): JsonObject?

    fun findApi(token: String, catId: String, apiName: String): String?

    fun findApis(token: String, catId: String): ArrayList<Any?>?

    fun listApis(token: String, catId: String, limit: Int?): JsonArray?

    fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean

    fun getApiWeb(module: String, cartName: String, apiName: String): String?

    //carts--------------------------------------------------------------

    fun findCart(token: String, name: String): String?

    fun findCarts(projectId: String, token: String): ArrayList<Any?>?

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
    val path = apiInfo["path"] ?: return false
    val method = apiInfo["method"] ?: return false
    val token = apiInfo["token"] as? String ?: return false
    val projectId: String = this.getProjectIdByToken(token) ?: return false
    val carts = this.findCarts(projectId, token) ?: return false
    for (cart in carts) {
        val cart_id = (cart as? Map<*, *>)?.get("_id")?.toString() ?: continue
        if (this.listApis(token, cart_id, null)?.any { api ->
                api.sub("path")?.asString == path && api.sub("method")?.asString == method
            } == true) {
            return true
        }
    }
    return false
}

fun YapiApiHelper.getCartForFolder(folder: Folder, privateToken: String): CartInfo? {

    val name: String = folder.name ?: "anonymous"

    var cartId: String?

    //try find existed cart.
    try {
        cartId = findCart(privateToken, name)
    } catch (e: Exception) {
        ActionContext.getContext()?.instance(Logger::class)
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