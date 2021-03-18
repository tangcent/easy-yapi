package com.itangcent.idea.plugin.api.export

import org.apache.http.HttpEntity
import org.apache.http.HttpResponse
import org.apache.http.client.HttpResponseException
import org.apache.http.client.ResponseHandler
import org.apache.http.util.consume
import org.apache.http.util.readString
import java.io.IOException

class StringResponseHandler : ResponseHandler<String> {

    @Throws(IOException::class)
    fun handleEntity(entity: HttpEntity): String {
        return entity.readString()
    }

    @Throws(HttpResponseException::class, IOException::class)
    override fun handleResponse(response: HttpResponse): String? {
        val statusLine = response.statusLine
        val entity = response.entity

        return try {
            if (entity == null) null else this.handleEntity(entity)
        } catch (e: Exception) {
            if (statusLine.statusCode >= 300) {
                entity.consume()
                throw HttpResponseException(statusLine.statusCode, statusLine.reasonPhrase)
            }
            "empty response"
        }
    }

    companion object {
        val DEFAULT_RESPONSE_HANDLER = StringResponseHandler()
    }
}
