package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.inject.ImplementedBy
import java.util.*
import kotlin.collections.HashMap


@ImplementedBy(DefaultYapiApiHelper::class)
interface YapiApiHelper {

    fun findCartWeb(module: String, cartName: String): String?

    fun getCartWeb(projectId: String, catId: String): String?

    fun getApiWeb(module: String, cartName: String, apiName: String): String?

    fun findCat(token: String, name: String): String?

    fun saveApiInfo(apiInfo: HashMap<String, Any?>): Boolean

    fun addCart(privateToken: String, name: String, desc: String): Boolean

    fun addCart(projectId: String, token: String, name: String, desc: String): Boolean

    fun findApi(token: String, catId: String, apiName: String): String?

    fun findApis(token: String, catId: String): ArrayList<Any?>?

    fun findCarts(project_id: String, token: String): ArrayList<Any?>?

    fun findServer(): String?

    fun setYapiServer(yapiServer: String)

    fun setToken(module: String, token: String)

    fun getProjectIdByToken(token: String): String?

    fun getProjectInfo(token: String, projectId: String?): JsonElement?

    fun getProjectInfo(token: String): JsonObject?

    fun readTokens(): HashMap<String, String>

    fun removeToken(token: String)

    fun removeTokenByModule(module: String)

    fun getPrivateToken(module: String): String?
}