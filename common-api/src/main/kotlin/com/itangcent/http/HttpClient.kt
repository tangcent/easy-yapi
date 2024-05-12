package com.itangcent.http

import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.constant.HttpMethod

/**
 * Defines an interface for an HTTP client capable of creating various types of HTTP requests.
 */
@ScriptTypeName("httpClient")
interface HttpClient {

    /**
     * Returns a CookieStore to manage cookies for HTTP transactions.
     */
    fun cookieStore(): CookieStore

    /**
     * Create a request.
     */
    fun request(): HttpRequest

    /**
     * Create a request with [HttpMethod.GET].
     */
    fun get(): HttpRequest {
        return request().method(HttpMethod.GET)
    }

    /**
     * Create a request with [HttpMethod.GET] and special url.
     */
    fun get(url: String): HttpRequest {
        return request().method(HttpMethod.GET).url(url)
    }

    /**
     * Create a request with [HttpMethod.POST].
     */
    fun post(): HttpRequest {
        return request().method(HttpMethod.POST)
    }

    /**
     * Create a request with [HttpMethod.POST] and special url.
     */
    fun post(url: String): HttpRequest {
        return request().method(HttpMethod.POST).url(url)
    }

    /**
     * Create a request with [HttpMethod.PUT].
     */
    fun put(): HttpRequest {
        return request().method(HttpMethod.PUT)
    }

    /**
     * Create a request with [HttpMethod.PUT] and special url.
     */
    fun put(url: String): HttpRequest {
        return request().method(HttpMethod.PUT).url(url)
    }

    /**
     * Create a request with [HttpMethod.DELETE].
     */
    fun delete(): HttpRequest {
        return request().method(HttpMethod.DELETE)
    }

    /**
     * Create a request with [HttpMethod.DELETE] and special url.
     */
    fun delete(url: String): HttpRequest {
        return request().method(HttpMethod.DELETE).url(url)
    }

    /**
     * Create a request with [HttpMethod.OPTIONS].
     */
    fun options(): HttpRequest {
        return request().method(HttpMethod.OPTIONS)
    }

    /**
     * Create a request with [HttpMethod.OPTIONS] and special url.
     */
    fun options(url: String): HttpRequest {
        return request().method(HttpMethod.OPTIONS).url(url)
    }

    /**
     * Create a request with [HttpMethod.TRACE].
     */
    fun trace(): HttpRequest {
        return request().method(HttpMethod.TRACE)
    }

    /**
     * Create a request with [HttpMethod.TRACE] and special url.
     */
    fun trace(url: String): HttpRequest {
        return request().method(HttpMethod.TRACE).url(url)
    }

    /**
     * Create a request with [HttpMethod.PATCH].
     */
    fun patch(): HttpRequest {
        return request().method(HttpMethod.PATCH)
    }

    /**
     * Create a request with [HttpMethod.PATCH] and special url.
     */
    fun patch(url: String): HttpRequest {
        return request().method(HttpMethod.PATCH).url(url)
    }

    /**
     * Create a request with [HttpMethod.HEAD].
     */
    fun head(): HttpRequest {
        return request().method(HttpMethod.HEAD)
    }

    /**
     * Create a request with [HttpMethod.HEAD] and special url.
     */
    fun head(url: String): HttpRequest {
        return request().method(HttpMethod.HEAD).url(url)
    }
}

