package com.itangcent.idea.plugin.utils

import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.GsonUtils
import java.net.URL

object RequestUtils {

    fun parseRawBody(body: Any): String {
        if (body is String) {
            return body
        }
        if (body is Map<*, *>) {
            if (body.containsKey(Attrs.COMMENT_ATTR)) {
                return GsonUtils.prettyJson(body.filterKeys { it != Attrs.COMMENT_ATTR })
            }
        }
        return GsonUtils.prettyJson(body)
    }

    fun contractPath(pathPre: String?, pathAfter: String?): String? {
        if (pathPre == null) return pathAfter
        if (pathAfter == null) return pathPre
        return pathPre.removeSuffix("/") + "/" + pathAfter.removePrefix("/")
    }

    fun addQuery(url: String, query: String?): String {
        if (query.isNullOrBlank()) return url
        return url.removeSuffix("?") + "?" + query.removePrefix("?")
    }

    fun addProtocolIfNeed(url: String, protocol: String): String {

        if (url.startsWith(protocol)) {
            return url
        }
        return try {
            URL(url)
            url
        } catch (e: Exception) {
            protocol.removeSuffix("://") + "://" + url
        }
    }

    class UrlBuild {
        private var protocol: String? = null

        //include port
        private var host: String? = null

        private var path: String? = null

        private var query: String? = null

        fun protocol(protocol: String?): UrlBuild {
            this.protocol = protocol
            return this
        }

        fun host(host: String?): UrlBuild {

            try {
                val parsedURL = URL(host)
                this.protocol = parsedURL.protocol
                this.host = host!!.removePrefix(this.protocol!! + "://")
            } catch (e: Exception) {
                this.host = host
            }
            return this
        }

        fun path(path: String?): UrlBuild {
            this.path = path
            return this
        }

        fun query(query: String?): UrlBuild {
            this.query = query
            return this
        }

        fun url(): String {
            val sb = StringBuilder()
                    .append(this.protocol ?: "http")
                    .append("://")
                    .append(host!!.removeSuffix("?/"))
            if (!path.isNullOrBlank()) {
                sb.append("/")
                sb.append(path!!.removePrefix("/").removeSuffix("?"))
            }
            if (!query.isNullOrBlank()) {
                sb.append("?")
                sb.append(query!!.removePrefix("?"))
            }
            return sb.toString()
        }

    }
}