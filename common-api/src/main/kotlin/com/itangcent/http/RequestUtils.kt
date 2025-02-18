package com.itangcent.http

import com.itangcent.common.constant.Attrs
import com.itangcent.common.utils.*
import java.net.URL

object RequestUtils {

    fun parseRawBody(body: Any, copy: Boolean = true): String {
        if (body is String) {
            return body
        }
        return GsonUtils.prettyJsonWithNulls(toRawBody(body, copy))
    }

    fun toRawBody(body: Any?, copy: Boolean): Any? {
        if (body == null) {
            return null
        }
        if (body is Map<*, *>) {
            val mutableBody = body.filterKeys {
                it !is String || !it.startsWith(Attrs.PREFIX)
            }.mutable(copy)
            for (mutableEntry in mutableBody) {
                mutableEntry.value?.let {
                    val rawValue = toRawBody(it, copy)
                    if (rawValue != it) {
                        mutableEntry.setValue(rawValue)
                    }
                }
            }
            return mutableBody
        }
        if (body is List<*>) {
            return body.map { toRawBody(it, copy) }
        }
        if (body is Array<*>) {
            return body.mapToTypedArray { toRawBody(it, copy) }
        }
        return body
    }

    fun concatPath(pathPre: String?, pathAfter: String?): String? {
        if (pathPre.isNullOrBlank()) return pathAfter
        if (pathAfter.isNullOrBlank()) return pathPre
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

        fun query(name: String, value: String) {
            this.query = this.query.append("$name=$value", "&")
        }

        fun url(): String {
            val sb = StringBuilder()
                .append(this.protocol ?: "http")
                .append("://")
                .append(host!!.removeSuffix("?/"))
            if (path.notNullOrBlank()) {
                sb.append("/")
                sb.append(path!!.removePrefix("/").removeSuffix("?"))
            }
            if (query.notNullOrBlank()) {
                sb.append("?")
                sb.append(query!!.removePrefix("?"))
            }
            return sb.toString()
        }

    }
}