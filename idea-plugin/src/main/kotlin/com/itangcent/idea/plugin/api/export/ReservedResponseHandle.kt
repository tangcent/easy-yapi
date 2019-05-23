package com.itangcent.idea.plugin.api.export

import com.intellij.util.containers.stream
import org.apache.http.HttpResponse
import org.apache.http.client.ResponseHandler
import kotlin.streams.toList

class ReservedResponseHandle<T> : ResponseHandler<ReservedResult<T>> {

    private var delegate: ResponseHandler<T>

    constructor(delegate: ResponseHandler<T>) {
        this.delegate = delegate
    }

    override fun handleResponse(httpResponse: HttpResponse?): ReservedResult<T> {
        val result = delegate.handleResponse(httpResponse)
        return ReservedResult(result, httpResponse)
    }
}

class ReservedResult<T> {

    private var result: T

    private var httpResponse: HttpResponse? = null

    constructor(result: T, httpResponse: HttpResponse?) {
        this.result = result
        this.httpResponse = httpResponse
    }

    fun result(): T {
        return this.result
    }

    fun status(): Int? {
        return httpResponse?.statusLine?.statusCode
    }

    fun header(headerName: String): String? {
        return httpResponse?.getLastHeader(headerName)
                ?.value
    }

    fun headers(headerName: String): List<String> {
        return httpResponse?.getHeaders(headerName)
                .stream()
                .map { it.value }
                .filter { it != null }
                .map { it!! }
                .toList()
    }
}