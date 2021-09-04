package com.itangcent.idea.plugin.api.export.curl

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.cache.HttpContextCacheHelper
import com.itangcent.common.model.Request
import com.itangcent.common.model.getContentType
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.http.RequestUtils

/**
 * see https://curl.se/docs/manual.html
 */
@Singleton
class CurlFormatter {

    @Inject
    private lateinit var httpContextCacheHelper: HttpContextCacheHelper

    fun parseRequests(requests: List<Request>): String {
        val host = httpContextCacheHelper.selectHost("Select Host For Curl")
        val sb = StringBuilder()
        for (request in requests) {
            if (sb.isNotEmpty()) {
                sb.append("\n\n---\n\n")
            }
            val apiName = request.name ?: (request.method + ":" + request.path?.url())
            sb.append("## $apiName\n")
            sb.append("```bash\n")
            parseRequest(request, host, sb)
            sb.append("\n```")
        }
        return sb.toString()
    }

    fun parseRequest(request: Request): String {
        val host = httpContextCacheHelper.selectHost("Select Host For Curl")
        return StringBuilder().also {
            parseRequest(request, host, it)
        }.toString()
    }

    private fun parseRequest(
        request: Request,
        host: String,
        sb: StringBuilder
    ) {
        sb.append("curl")
        sb.append(" -X ").append(request.method)
        request.headers?.forEach { header ->
            sb.append(" -H '")
            sb.append(header.name)
                .append(": ")
            header.value?.escape()?.let { sb.append(it) }
            sb.append("'")
        }
        val contentType = request.getContentType()
        if (contentType != null) {
            when {
                contentType.contains("application/json") -> {
                    request.body?.let { body ->
                        sb.append(" -d '")
                        sb.append(RequestUtils.parseRawBody(body).escape())
                        sb.append("'")
                    }
                }
                contentType.contains("application/x-www-form-urlencoded") -> {
                    if (request.formParams.notNullOrEmpty()) {
                        sb.append(" -d '")
                        request.formParams?.forEachIndexed { index, param ->
                            if (index > 0) {
                                sb.append('&')
                            }
                            sb.append(param.name)
                                .append("=")
                            param.value?.escape()?.let { sb.append(it) }
                        }
                        sb.append("'")
                    }
                }
                contentType.contains("multipart/form-data") -> {
                    request.formParams?.forEach { param ->
                        sb.append(" -F '")
                        sb.append(param.name)
                            .append("=")
                        param.value?.escape()?.let { sb.append(it) }
                        sb.append("'")
                    }
                }
            }
        }
        sb.append(" ")


        val urlBuild = RequestUtils.UrlBuild().host(host)
            .path(request.path?.url())
        if (request.querys.notNullOrEmpty()) {
            val query = StringBuilder()
                .append("?")
            request.querys!!.forEach { param ->
                if (query.lastOrNull() != '?') {
                    query.append("\\&")
                }
                query.append(param.name).append("=")
                param.value?.let { query.append(it) }
            }
            urlBuild.query(query.toString())
        }
        sb.append(urlBuild.url())
    }

    private fun String.escape(): String {
        return this.replace("\\", "\\\\").replace("'", "\\'")
    }
}