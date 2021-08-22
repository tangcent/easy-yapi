package com.itangcent.cache

import com.google.inject.ImplementedBy
import com.itangcent.http.Cookie

@ImplementedBy(DefaultHttpContextCacheHelper::class)
interface HttpContextCacheHelper {

    fun getHosts(): List<String>

    fun addHost(host: String)

    fun getCookies(): List<Cookie>

    fun addCookies(cookies: List<Cookie>)

    fun selectHost(message: String? = null): String
}