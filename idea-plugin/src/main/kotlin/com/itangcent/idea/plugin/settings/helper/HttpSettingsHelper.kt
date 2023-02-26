package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.openapi.ui.Messages
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.update
import com.itangcent.idea.plugin.utils.RegexUtils
import com.itangcent.idea.swing.MessagesHelper
import java.net.URL
import java.util.concurrent.TimeUnit

@Singleton
class HttpSettingsHelper {

    @Inject
    private lateinit var settingBinder: SettingBinder

    @Inject
    private lateinit var messagesHelper: MessagesHelper

    //region trustHosts----------------------------------------------------

    fun checkTrustUrl(url: String, dumb: Boolean = true): Boolean {
        val trustHosts = settingBinder.read().trustHosts
        var ret: Boolean? = null
        for (trustHost in trustHosts) {
            if (trustHost.startsWith("!")) {
                if (url.startsWith(trustHost.removePrefix("!"))) {
                    return false
                }
            } else if (url.startsWith(trustHost)) {
                ret = true
            }
        }
        if (ret == true) {
            return true
        }
        return checkTrustHost(resolveHost(url), dumb)
    }

    fun checkTrustHost(host: String, dumb: Boolean = true): Boolean {
        val settings = settingBinder.read()
        val trustHosts = settings.trustHosts

        //check forbidden first
        if (trustHosts.contains("!$host")) {
            return false
        }
        if (settings.yapiServer == host
                || trustHosts.contains(host)) {
            return true
        }
        if (!dumb) {
            val trustRet = messagesHelper.showYesNoDialog("Do you trust [$host]?",
                    "Trust Host", Messages.getQuestionIcon())

            return if (trustRet == Messages.YES) {
                addTrustHost(host)
                true
            } else {
                addTrustHost("!$host")
                false
            }
        }
        return false
    }

    fun addTrustHost(host: String) {
        settingBinder.update { settings ->
            if (!settings.trustHosts.contains(host)) {
                settings.trustHosts = settings.trustHosts + host
            }
        }
    }

    fun resolveHost(url: String): String {
        try {
            val settings = settingBinder.read()

            //check if it belongs yapiServer
            settings.yapiServer?.let { yapiServer ->
                if (url.startsWith(yapiServer)) {
                    return yapiServer
                }
            }

            HOST_RESOLVERS.forEach { resolver ->
                resolver(url)?.let { return it.removeSuffix("/") }
            }
            return URL(url).let { "${it.protocol}://${it.host}" }.removeSuffix("/")
        } catch (e: Exception) {
            return url
        }
    }

    //endregion trustHosts----------------------------------------------------

    fun httpTimeOut(timeUnit: TimeUnit): Int {
        //unit of httpTimeOut is second
        if (timeUnit == TimeUnit.SECONDS) {
            return settingBinder.read().httpTimeOut
        }
        return timeUnit.convert(settingBinder.read().httpTimeOut.toLong(), TimeUnit.SECONDS).toInt()
    }

    companion object {
        val HOST_RESOLVERS: Array<(String) -> String?> = arrayOf({
            if (it.startsWith("https://raw.githubusercontent.com")) {
                val url = if (it.endsWith("/")) it else "$it/"
                return@arrayOf RegexUtils.extract("https://raw.githubusercontent.com/(.*?)/.*",
                        url, "https://raw.githubusercontent.com/$1")
            }
            return@arrayOf null
        })
    }
}