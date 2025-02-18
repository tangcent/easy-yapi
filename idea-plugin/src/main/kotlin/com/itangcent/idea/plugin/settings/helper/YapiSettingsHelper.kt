package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.ui.Messages
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.YapiExportMode
import com.itangcent.idea.plugin.settings.update
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.AutoClear
import com.itangcent.intellij.logger.Logger
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * Utility class providing access setting about yapi through [SettingBinder]&[ConfigReader].
 */
@Singleton
class YapiSettingsHelper {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var settingBinder: SettingBinder

    @Inject
    private lateinit var configReader: ConfigReader

    @Inject(optional = true)
    private val yapiTokenChecker: YapiTokenChecker? = null

    @Inject
    private lateinit var messagesHelper: MessagesHelper

    @Volatile
    private var server: String? = null

    protected var cacheLock: ReadWriteLock = ReentrantReadWriteLock()

    //region server----------------------------------------------------

    fun hasServer(): Boolean {
        return getServer().notNullOrEmpty()
    }

    fun getServer(dumb: Boolean = true): String? {
        if (server.notNullOrBlank()) return server
        configReader.first("yapi.server")?.trim()?.removeSuffix("/")
            ?.takeIf { it.notNullOrBlank() }
            ?.let {
                server = it
                return server
            }
        settingBinder.read().yapiServer?.trim()?.removeSuffix("/")
            ?.takeIf { it.notNullOrBlank() }
            ?.let {
                server = it
                return server
            }
        if (!dumb) {
            val yapiServer =
                messagesHelper.showInputDialog(
                    "Input server of yapi",
                    "Server Of Yapi", Messages.getInformationIcon()
                )
                    ?.removeSuffix("/")
            if (yapiServer.isNullOrBlank()) return null
            server = yapiServer
            settingBinder.update {
                this.yapiServer = yapiServer
            }
            return yapiServer
        }
        return null
    }

    //endregion server----------------------------------------------------

    //region tokens----------------------------------------------

    /**
     * Tokens in setting.
     * Map<module,<token,state>>
     * state: null->unchecked,true->valid, false->invalid
     */
    @AutoClear
    private var tokenMap: HashMap<String, Pair<String, Boolean?>>? = null

    @AutoClear
    private var tryInputTokenOfModule = HashSet<String>()

    fun getPrivateToken(module: String, dumb: Boolean = true): String? {

        configReader.first("yapi.token.$module")?.let { return it }

        cacheLock.readLock().withLock {
            if (tokenMap != null) {
                tokenMap!![module]?.checked()?.let { return it }
            }
        }

        cacheLock.writeLock().withLock {
            checkInit()
            tokenMap!![module]?.checked()?.let { return it }
            if (!dumb && tryInputTokenOfModule.add(module)) {
                val modulePrivateToken = inputNewToken(module)
                if (modulePrivateToken.notNullOrBlank()
                    && yapiTokenChecker?.checkToken(modulePrivateToken!!) != false
                ) {
                    setToken(module, modulePrivateToken!!)
                    return modulePrivateToken
                }
            }
        }
        return null
    }

    private fun inputNewToken(module: String): String? {
        val inputTitle = if (loginMode()) "ProjectId" else "Private Token"
        return messagesHelper.showInputDialog(
            "Input $inputTitle Of Module:$module",
            "Yapi $inputTitle", Messages.getInformationIcon()
        )
    }

    fun inputNewToken(): String? {
        val inputTitle = if (loginMode()) "ProjectId" else "Private Token"
        return messagesHelper.showInputDialog(
            "Input $inputTitle",
            "Yapi $inputTitle", Messages.getInformationIcon()
        )
    }

    private fun Pair<String, Boolean?>.checked(): String? {
        return when (this.second) {
            null -> {
                val status = yapiTokenChecker?.checkToken(this.first) ?: true
                updateTokenStatus(this.first, status)
                if (!status) {
                    logger.warn("token:${this.first} may be invalid.")
                    if (!settingBinder.read().loginMode && this.first.length != 64) {
                        logger.info("Please switch to loginModel if the version of yapi is before 1.6.0")
                        logger.info("For more details see: http://easyyapi.com/documents/login_mode_yapi.html")
                    }
                }
                return if (status) this.first else null
            }

            true -> {
                this.first
            }

            false -> {
                null
            }
        }
    }

    /**
     * disable this token temporarily
     */
    fun disableTemp(token: String) {
        cacheLock.writeLock().withLock {
            checkInit()
            updateTokenStatus(token, false)
        }
    }

    private fun updateTokenStatus(token: String, status: Boolean) {
        tokenMap!!.entries.forEach {
            if (it.value.first == token) {
                it.setValue(it.value.first to status)
            }
        }
    }

    private fun checkInit() {
        if (tokenMap.isNullOrEmpty()) {
            initToken()
        }
    }

    private fun initToken() {
        tokenMap = HashMap()
        val settings = settingBinder.read()
        if (settings.yapiTokens != null) {
            val properties = Properties()
            properties.load(settings.yapiTokens!!.byteInputStream())
            properties.forEach { t, u -> tokenMap!![t.toString()] = u.toString() to null }
        }
    }

    private fun updateTokens(handle: (Properties) -> Unit) {
        cacheLock.writeLock().withLock {
            val settings = settingBinder.read()
            val properties = Properties()
            if (settings.yapiTokens != null) {
                properties.load(settings.yapiTokens!!.byteInputStream())
            }
            handle(properties)

            settings.yapiTokens = ByteArrayOutputStream().also { properties.store(it, "") }.toString()
            settingBinder.save(settings)
            if (tokenMap == null) {
                tokenMap = HashMap()
            } else {
                tokenMap!!.clear()
            }
            properties.forEach { t, u -> tokenMap!![t.toString()] = u.toString() to null }
        }
    }

    fun setToken(module: String, token: String) {
        updateTokens { properties ->
            properties[module] = token
        }
        tokenMap?.put(module, token to null)
    }

    fun removeTokenByModule(module: String) {
        updateTokens { properties ->
            properties.remove(module)
        }
        tokenMap?.remove(module)
    }

    fun removeToken(token: String) {
        updateTokens { properties ->
            val removedKeys = properties.entries
                .filter { it.value == token }
                .map { it.key }
                .toList()
            removedKeys.forEach {
                properties.remove(it)
                tokenMap?.remove(it)
            }
        }
    }

    fun readTokens(): HashMap<String, String> {
        checkInit()
        return HashMap(tokenMap!!.mapValues { it.value.first })
    }

    fun rawToken(token: String): String {
        if (loginMode()) {
            return ""
        }
        return token
    }

    //endregion  tokens----------------------------------------------

    fun enableUrlTemplating(): Boolean {
        return settingBinder.read().enableUrlTemplating
    }

    fun loginMode(): Boolean {
        return settingBinder.read().loginMode
    }

    fun exportMode(): YapiExportMode {
        return YapiExportMode.valueOf(settingBinder.read().yapiExportMode)
    }

//    fun overwrite()

    fun switchNotice(): Boolean {
        return settingBinder.read().switchNotice
    }

    fun yapiReqBodyJson5(): Boolean {
        return settingBinder.read().yapiReqBodyJson5
    }

    fun yapiResBodyJson5(): Boolean {
        return settingBinder.read().yapiResBodyJson5
    }
}

/**
 * Performs checks on each {@code token} of yapi.
 */
interface YapiTokenChecker {

    /**
     * @return return true if the token is valid.
     */
    fun checkToken(token: String): Boolean
}