package com.itangcent.idea.plugin.api.export.core

import com.intellij.util.containers.stream
import org.apache.http.HttpResponse
import org.apache.http.client.ResponseHandler
import java.util.*
import kotlin.streams.toList

class ReservedResponseHandle<T>(private var delegate: ResponseHandler<T>) : ResponseHandler<ReservedResult<T>> {

    override fun handleResponse(httpResponse: HttpResponse?): ReservedResult<T> {
        val result = delegate.handleResponse(httpResponse)
        return ReservedResult(result, httpResponse)
    }

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun <T> wrap(delegate: ResponseHandler<T>): ReservedResponseHandle<T> {
            if (delegate is ReservedResponseHandle<*>) {
                return delegate as ReservedResponseHandle<T>
            }

            var reservedResponseHandle = weakHashMap[delegate]
            if (reservedResponseHandle != null) {
                return reservedResponseHandle as ReservedResponseHandle<T>
            }
            synchronized(weakHashMap)
            {
                reservedResponseHandle = weakHashMap[delegate] ?: ReservedResponseHandle(delegate)
                weakHashMap[delegate] = reservedResponseHandle
                return reservedResponseHandle as ReservedResponseHandle<T>
            }
        }

        private val weakHashMap = WeakHashMap<ResponseHandler<*>, ReservedResponseHandle<*>>()
    }
}

class ReservedResult<T>(
    private var result: T,
    private var httpResponse: HttpResponse?
) {

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

fun <T> ResponseHandler<T>.reserved(): ReservedResponseHandle<T> {
    return ReservedResponseHandle.wrap(this)
}