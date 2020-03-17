package com.itangcent.http

import com.itangcent.annotation.script.ScriptTypeName

@ScriptTypeName("httpClient")
interface HttpClient {

    fun cookieStore(): CookieStore

    fun request(): HttpRequest

    fun get(): HttpRequest {
        return request().method("GET")
    }

    fun get(url: String): HttpRequest {
        return request().method("GET").url(url)
    }

    fun post(): HttpRequest {
        return request().method("POST")
    }

    fun post(url: String): HttpRequest {
        return request().method("POST").url(url)
    }

    fun put(): HttpRequest {
        return request().method("PUT")
    }

    fun put(url: String): HttpRequest {
        return request().method("PUT").url(url)
    }

    fun delete(): HttpRequest {
        return request().method("DELETE")
    }

    fun delete(url: String): HttpRequest {
        return request().method("DELETE").url(url)
    }

    fun options(): HttpRequest {
        return request().method("OPTIONS")
    }

    fun options(url: String): HttpRequest {
        return request().method("OPTIONS").url(url)
    }

    fun trace(): HttpRequest {
        return request().method("TRACE")
    }

    fun trace(url: String): HttpRequest {
        return request().method("TRACE").url(url)
    }

    fun patch(): HttpRequest {
        return request().method("PATCH")
    }

    fun patch(url: String): HttpRequest {
        return request().method("PATCH").url(url)
    }

    fun head(): HttpRequest {
        return request().method("HEAD")
    }

    fun head(url: String): HttpRequest {
        return request().method("HEAD").url(url)
    }

}

