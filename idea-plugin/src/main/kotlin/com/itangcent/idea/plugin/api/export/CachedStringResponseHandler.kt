package com.itangcent.idea.plugin.api.export

import org.apache.http.HttpResponse
import org.apache.http.client.ResponseHandler

class CachedResponseHandler<T> : ResponseHandler<T> {

    private val responseHandler: ResponseHandler<T>

    private var cacheResponse: T? = null

    constructor(responseHandler: ResponseHandler<T>) {
        this.responseHandler = responseHandler
    }

    override fun handleResponse(httpResponse: HttpResponse?): T {
        val response = responseHandler.handleResponse(httpResponse)
        cacheResponse = response
        return response
    }
}
