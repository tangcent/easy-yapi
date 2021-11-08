package com.itangcent.cache

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.intellij.openapi.ui.Messages
import com.itangcent.http.BasicCookie
import com.itangcent.http.Cookie
import com.itangcent.http.json
import com.itangcent.idea.swing.MessagesHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.file.BeanBinder
import com.itangcent.intellij.file.FileBeanBinder
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.file.lazy

@Singleton
class DefaultHttpContextCacheHelper : HttpContextCacheHelper {

    @Inject
    @Named("projectCacheRepository")
    private val projectCacheRepository: LocalFileRepository? = null

    @Inject(optional = true)
    @Named("host.history.max")
    private var maxHostHistory: Int = 10

    @Inject(optional = true)
    @Named("host.default")
    private var defaultHost: String = "http://localhost:8080"

    private val httpContextCacheBinder: BeanBinder<HttpContextCache> by lazy {
        FileBeanBinder(
            projectCacheRepository!!.getOrCreateFile(".http_content_cache"),
            HttpContextCache::class
        ).lazy()
    }

    //hosts-------

    override fun getHosts(): List<String> {
        val httpContextCache = httpContextCacheBinder.tryRead()
        return getHosts(httpContextCache)
    }

    override fun addHost(host: String) {
        val httpContextCache = httpContextCacheBinder.read()
        val hosts = getHosts(httpContextCache).toMutableList()

        if (hosts.contains(host)) {
            if (hosts.indexOf(host) != 0) {
                //move to first
                hosts.remove(host)
                hosts.add(0, host)//always add to first
            }
        } else {
            while (hosts.size >= maxHostHistory) {
                hosts.removeAt(hosts.size - 1)//remove the last host
            }
            hosts.add(0, host)//always add to first
        }

        httpContextCache.hosts = hosts
        httpContextCacheBinder.save(httpContextCache)
    }

    private fun getHosts(httpContextCache: HttpContextCache?): List<String> {
        val hosts = httpContextCache?.hosts
        if (hosts.isNullOrEmpty()) {
            return listOf(defaultHost)
        }
        return hosts
    }

    //cookies

    override fun getCookies(): List<Cookie> {
        return httpContextCacheBinder.read().cookies?.map {
            BasicCookie.fromJson(it)
        } ?: emptyList()
    }

    override fun addCookies(cookies: List<Cookie>) {
        val httpContextCache = httpContextCacheBinder.read()
        val cachedCookies = httpContextCache.cookies?.toMutableList()?.let { HashSet(it) } ?: HashSet()
        cookies.forEach { cachedCookies.add(it.json()) }
        httpContextCache.cookies = ArrayList(cachedCookies)
        httpContextCacheBinder.save(httpContextCache)
    }

    override fun selectHost(message: String?): String {
        val messagesHelper = ActionContext.getContext()!!.instance(MessagesHelper::class)
        val hosts = getHosts()
        val selectedHost = messagesHelper.showEditableChooseDialog(
            message,
            "Host",
            Messages.getInformationIcon(),
            hosts.toTypedArray(),
            hosts.first()
        ) ?: return defaultHost
        addHost(selectedHost)
        return selectedHost
    }

    class HttpContextCache {
        var cookies: List<String>? = null
        var hosts: List<String>? = null
    }
}