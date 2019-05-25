package com.itangcent.idea.plugin.api.export.yapi

import com.google.gson.JsonElement
import com.google.inject.Inject
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.ReservedResponseHandle
import com.itangcent.idea.plugin.api.export.StringResponseHandler
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.logger.Logger
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.collections.HashMap
import kotlin.concurrent.withLock

open class AbstractYapiApiHelper {
    @Inject
    private val settingBinder: SettingBinder? = null

    @Inject
    protected val logger: Logger? = null

    @Inject
    private val configReader: ConfigReader? = null

    var server: String? = null

    private var projectIdCache: HashMap<String, String> = HashMap()//token->id

    var projectInfoCache: HashMap<String, JsonElement> = HashMap()//id->info

    protected var cacheLock: ReadWriteLock = ReentrantReadWriteLock()

    fun hasPrivateToken(module: String): Boolean {
        return getPrivateToken(module) != null
    }

    fun findServer(): String? {
        if (!server.isNullOrBlank()) return server
        server = configReader!!.read("server")?.removeSuffix("/")
        if (!server.isNullOrBlank()) return server
        server = settingBinder!!.read().yapiServer?.removeSuffix("/")
        return server
    }

    fun setYapiServer(yapiServer: String) {
        val settings = settingBinder!!.read()
        settings.yapiServer = yapiServer
        settingBinder.save(settings)
    }


    fun getProjectWeb(module: String): String? {
        val token = getPrivateToken(module)
        val projectId = getProjectIdByToken(token!!) ?: return null
        return "$server/project/$projectId/interface/api"
    }

    protected fun findErrorMsg(res: String?): String? {
        if (res == null) return "no response"
        if (StringUtils.isNotBlank(res) && res.contains("errmsg")) {
            val returnObj = GsonUtils.parseToJsonTree(res)
            val errMsg = returnObj
                    ?.asJsonObject
                    ?.get("errmsg")
                    ?.asString
            if (StringUtils.isNotBlank(errMsg) && !errMsg!!.contains("成功")) {
                return errMsg
            }
        }
        return null
    }

    fun getProjectIdByToken(token: String): String? {
        var projectId = cacheLock.readLock().withLock { projectIdCache[token] }
        if (projectId != null) return projectId
        try {
            projectId = getProjectInfo(token, null)?.asJsonObject
                    ?.get("data")
                    ?.asJsonObject
                    ?.get("_id")
                    ?.asString
        } catch (e: IllegalStateException) {
            logger!!.error("invalid token:$token")

        }
        if (projectId != null) {
            cacheLock.writeLock().withLock {
                projectIdCache[token] = projectId
            }
        }
        return projectId
    }

    fun getProjectInfo(token: String, projectId: String?): JsonElement? {
        if (projectId != null) {
            val cachedProjectInfo = cacheLock.readLock().withLock { projectInfoCache[projectId] }
            if (cachedProjectInfo != null) return cachedProjectInfo
        }

        var url = "$server$GETPROJECT?token=$token"
        if (projectId != null) {
            url = "$url&id=$projectId"
        }

        val ret = getByApi(url) ?: return null
        val projectInfo = GsonUtils.parseToJsonTree(ret)
        if (projectId != null && projectInfo != null) {
            cacheLock.writeLock().withLock { projectInfoCache[projectId] = projectInfo }
        }
        return projectInfo
    }

    fun getByApi(url: String): String? {
        return try {
            val httpClient = HttpClients.createDefault()
            val httpGet = HttpGet(url)
            val responseHandler = reservedResponseHandle()

            httpClient.execute(httpGet, responseHandler).result()
        } catch (e: Exception) {
            null
        }
    }

    protected fun reservedResponseHandle(): ReservedResponseHandle<String> {
        return responseHandler
    }

    private var tokenMap: HashMap<String, String>? = null

    fun getPrivateToken(module: String): String? {

        cacheLock.readLock().withLock {
            if (tokenMap != null) {
                return tokenMap!![module]
            }
        }

        cacheLock.writeLock().withLock {
            if (tokenMap == null) {
                initToken()
            }
            return tokenMap!![module]
        }
    }

    private fun initToken() {
        tokenMap = HashMap()
        val settings = settingBinder!!.read()
        if (settings.yapiTokens != null) {
            val properties = Properties()
            properties.load(settings.yapiTokens!!.byteInputStream())
            properties.forEach { t, u -> tokenMap!![t.toString()] = u.toString() }
        }
    }

    fun setToken(module: String, token: String) {

        cacheLock.writeLock().withLock {
            val settings = settingBinder!!.read()
            val properties = Properties()
            if (settings.yapiTokens != null) {
                properties.load(settings.yapiTokens!!.byteInputStream())
            }
            properties[module] = token
            val byteOutputStream = ByteArrayOutputStream()
            properties.store(byteOutputStream, "#")
            settings.yapiTokens = byteOutputStream.toString()
            settingBinder.save(settings)
            if (tokenMap == null) {
                tokenMap = HashMap()
            }
            properties.forEach { t, u -> tokenMap!![t.toString()] = u.toString() }
        }
    }

    companion object {
        var GETPROJECT = "/api/project/get"

        private val responseHandler = ReservedResponseHandle(StringResponseHandler())
    }
}