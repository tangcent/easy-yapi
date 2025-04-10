package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.inject.Inject
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.trySet
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.idea.plugin.rule.SuvRuleContext
import com.itangcent.idea.plugin.settings.helper.YapiSettingsHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.extend.asHashMap
import com.itangcent.intellij.extend.asMap
import com.itangcent.intellij.extend.sub
import com.itangcent.intellij.logger.Logger
import com.itangcent.suv.http.HttpClientProvider
import org.apache.commons.lang3.StringUtils
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

abstract class AbstractYapiApiHelper : YapiApiHelper {

    @Inject
    protected lateinit var yapiSettingsHelper: YapiSettingsHelper

    @Inject
    protected lateinit var logger: Logger

    @Inject
    protected lateinit var ruleComputer: RuleComputer

    @Inject
    protected lateinit var httpClientProvide: HttpClientProvider

    @Volatile
    var init: Boolean = false

    private var projectIdCache: HashMap<String, String> = HashMap()//token->id

    var projectInfoCache: HashMap<String, JsonElement> = HashMap()//id->info

    protected var cacheLock: ReadWriteLock = ReentrantReadWriteLock()

    open fun getProjectWeb(module: String): String? {
        val token = yapiSettingsHelper.getPrivateToken(module, false) ?: return null
        val projectId = getProjectIdByToken(token) ?: return null
        return "${yapiSettingsHelper.getServer()}/project/$projectId/interface/api"
    }

    protected open fun findErrorMsg(res: String?): String? {
        if (res == null) return "no response"
        if (StringUtils.isNotBlank(res) && res.contains("errmsg")) {
            val returnObj = GsonUtils.parseToJsonTree(res)
            val errMsg = returnObj
                .sub("errmsg")
                ?.asString
            if (StringUtils.isNotBlank(errMsg) && !errMsg!!.contains("成功")) {
                return errMsg
            }
        }
        return null
    }

    override fun getProjectIdByToken(token: String): String? {
        if (yapiSettingsHelper.loginMode()) {
            return token
        }
        var projectId = cacheLock.readLock().withLock { projectIdCache[token] }
        if (projectId != null) return projectId
        try {
            projectId = getProjectInfo(token, null)
                ?.sub("data")
                ?.sub("_id")
                ?.asString
        } catch (e: IllegalStateException) {
            logger.error("invalid token:$token")
        }
        if (projectId != null) {
            cacheLock.writeLock().withLock {
                projectIdCache[token] = projectId
            }
        }
        return projectId
    }

    override fun getProjectInfo(token: String, projectId: String?): JsonObject? {
        if (projectId != null) {
            val cachedProjectInfo = cacheLock.readLock().withLock { projectInfoCache[projectId] }
            if (cachedProjectInfo != null) {
                if (cachedProjectInfo == NULL_PROJECT) {
                    return null
                }
                return cachedProjectInfo as? JsonObject
            }
        }

        var url = "${yapiSettingsHelper.getServer(false)}$GET_PROJECT_URL?token=$token"

        var rawProjectId = projectId
        if (yapiSettingsHelper.loginMode() && rawProjectId == null) {
            rawProjectId = getProjectIdByToken(token)
        }
        if (rawProjectId != null) {
            url = "$url&id=$rawProjectId"
        }

        val ret = getByApi(url, false) ?: return null
        var projectInfo: JsonObject? = null
        try {
            projectInfo = GsonUtils.parseToJsonTree(ret) as? JsonObject
        } catch (e: Exception) {
            logger.error("error to parse project [$projectId] info:$ret")
        }

        if (projectId != null && projectInfo != null) {
            if (projectInfo.has("errcode")) {
                if (projectInfo.get("errcode").asInt == 40011) {
                    logger.warn("project:$projectId may be deleted.")
                    cacheLock.writeLock().withLock {
                        projectInfoCache[projectId] = NULL_PROJECT
                    }
                    return null
                }
            }
        }
        return projectInfo
    }

    override fun getProjectInfo(token: String): JsonObject? {
        val projectId = getProjectIdByToken(token) ?: return null
        return getProjectInfo(token, projectId)
    }

    override fun copyApi(from: Map<String, String>, target: Map<String, String>) {
        val fromToken = from.getToken() ?: throw IllegalArgumentException("no token be found in from")
        val targetToken = target.getToken() ?: throw IllegalArgumentException("no token be found in target")
        val targetCatId = target["catid"]
        listApis(from) { api ->
            val copyApi = HashMap(api)
            copyApi.remove("_id")
            if (targetCatId == null) {
                val fromCatId = api["catid"]!!
                val fromCartInfo = findCartById(fromToken, fromCatId.toString()) ?: return@listApis
                val cartName = fromCartInfo["name"] ?: return@listApis
                val cartInfo = getCartForFolder(
                    Folder(
                        name = cartName.toString(),
                        attr = api["desc"]?.toString() ?: ""
                    ), targetToken
                ) ?: return@listApis
                copyApi["catid"] = cartInfo.cartId
            } else {
                copyApi["catid"] = targetCatId
            }
            copyApi["token"] = targetToken
            copyApi["switch_notice"] = yapiSettingsHelper.switchNotice()
            saveApiInfo(copyApi)
        }
    }

    private fun Map<String, String>.getToken(): String? {
        val token = this["token"]
        if (token != null) {
            return token
        }

        val module = this["module"]
        if (module != null) {
            return yapiSettingsHelper.getPrivateToken(module, false).also {
                this.trySet("token", it)
            }
        }

        return null
    }

    private fun listApis(from: Map<String, String>, api: (Map<String, Any?>) -> Unit) {
        val fromToken = from.getToken() ?: throw IllegalArgumentException("no token be found in from")
        val id = from["id"]
        if (id != null) {
            getApiInfo(fromToken, id)?.asHashMap()?.let(api)
            return
        }

        val catId = from["catid"]
        if (catId != null) {
            listApis(fromToken, catId)?.map { it.asMap() }?.forEach(api)
            return
        }

        val projectId = getProjectIdByToken(fromToken) ?: throw IllegalArgumentException("invalid token $fromToken")
        val carts = findCarts(projectId, fromToken) ?: return
        for (cart in carts) {
            val cartId = (cart as? Map<*, *>)?.get("_id")?.toString() ?: continue
            listApis(fromToken, cartId)?.map { it.asMap() }?.forEach(api)
        }
    }

    open fun getByApi(url: String, dumb: Boolean = true): String? {
        initBeforeCallApi()
        var rawUrl = url
        if (yapiSettingsHelper.loginMode()) {
            //token is insignificant in loginMode
            if (rawUrl.contains("token=")) {
                rawUrl = TOKEN_REGEX.replace(url) { "" }
            }
        }
        return try {
            httpClientProvide.getHttpClient()
                .get(rawUrl)
                .call()
                .use { it.string() }
                ?.trim()
        } catch (e: SocketTimeoutException) {
            if (!dumb) {
                logger.trace("$rawUrl connect timeout")
                throw e
            }
            logger.error("$rawUrl connect timeout")
            null
        } catch (e: SocketException) {
            if (!dumb) {
                logger.trace("$rawUrl is unreachable (connect failed)")
                throw e
            }
            logger.error("$rawUrl is unreachable (connect failed)")
            null
        } catch (e: Exception) {
            if (!dumb) {
                logger.traceError("request $rawUrl failed", e)
                throw e
            }
            logger.traceError("request $rawUrl failed", e)
            null
        }
    }

    private fun initBeforeCallApi() {
        if (!init) {
            synchronized(this)
            {
                if (!init) {
                    ruleComputer.computer(
                        YapiClassExportRuleKeys.BEFORE_EXPORT, SuvRuleContext(),
                        null
                    )
                    init = true
                }
            }
        }
    }

    companion object {
        const val GET_PROJECT_URL = "/api/project/get"
        val NULL_PROJECT: JsonElement = JsonNull.INSTANCE
        val TOKEN_REGEX = Regex("(token=.*?)(?=&|$)")
    }
}

fun YapiApiHelper.findCartById(token: String, catId: String): Map<*, *>? {
    val projectId = getProjectIdByToken(token) ?: throw IllegalArgumentException("invalid token $token")
    val carts = findCarts(projectId, token) ?: throw IllegalArgumentException("invalid token $token")
    for (cart in carts) {
        val cartId = (cart as? Map<*, *>)?.get("_id")?.toString() ?: continue
        if (cartId == catId) {
            return cart
        }
    }
    return null
}