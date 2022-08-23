package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.inject.ImplementedBy
import com.itangcent.intellij.extend.sub


@ImplementedBy(DefaultYapiApiHelper::class)
interface YapiApiHelper {

    //apis--------------------------------------------------------------

    fun findApi(token: String, catId: String, apiName: String): String?

    fun findApis(token: String, catId: String): ArrayList<Any?>?

    fun listApis(token: String, catId: String, limit: Int?): JsonArray?

    fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean

    fun getApiWeb(module: String, cartName: String, apiName: String): String?

    //carts--------------------------------------------------------------

    fun findCart(token: String, name: String): String?

    fun findCarts(project_id: String, token: String): ArrayList<Any?>?

    fun addCart(privateToken: String, name: String, desc: String): Boolean

    fun addCart(projectId: String, token: String, name: String, desc: String): Boolean

    fun getCartWeb(projectId: String, catId: String): String?

    fun findCartWeb(module: String, cartName: String): String?

    //projects--------------------------------------------------------------

    fun getProjectIdByToken(token: String): String?

    fun getProjectInfo(token: String, projectId: String?): JsonObject?

    fun getProjectInfo(token: String): JsonObject?

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