package com.itangcent.common.http

import org.apache.http.HttpResponse
import org.apache.http.client.ResponseHandler
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils

class UltimateResponseHandler : ResponseHandler<com.itangcent.common.http.HttpResponse> {

    override fun handleResponse(response: HttpResponse?): com.itangcent.common.http.HttpResponse? {

        if (response == null) {
            return null
        }
        val statusLine = response.statusLine
        val code = statusLine.statusCode
        val headers: ArrayList<Pair<String, String>> = ArrayList()
        for (header in response.allHeaders) {
            for (element in header.elements) {
                headers.add(element.name to element.value)
            }
        }

        val entity = response.entity

        return BasicHttpResponse(code, headers, EntityUtils.toByteArray(entity),
                ContentType.getOrDefault(entity))
    }
}
