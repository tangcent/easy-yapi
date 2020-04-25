package com.itangcent.common.model

import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.http.RequestUtils

/**
 * Represents a url path.
 */
interface URL {

    fun url(): String?

    fun urls(): List<String>

    fun single(): Boolean

    fun contract(url: URL): URL

    /**
     * Returns a new [URL] of applying the given [transform] function
     * to each url.
     */
    fun map(transform: (String?) -> String?): URL

    companion object {
        private val NULL_URL: NullURL = NullURL()

        fun of(url: String?): URL {
            return if (url == null) {
                NULL_URL
            } else {
                SingleURL(url)
            }
        }

        fun of(vararg urls: String?): URL {
            return when {
                urls.isNullOrEmpty() -> NULL_URL
                urls.size == 1 -> of(urls[0])
                else -> MultiURL(urls.filterNotNull())
            }
        }

        fun of(urls: List<String>?): URL {
            return when {
                urls.isNullOrEmpty() -> NULL_URL
                urls.size == 1 -> of(urls.first())
                else -> MultiURL(urls)
            }
        }

        fun nil(): URL {
            return NULL_URL
        }
    }
}

/**
 * This url is null.
 * Create it by [URL.nil]
 */
private class NullURL : URL {

    override fun url(): String? {
        return null
    }

    override fun urls(): List<String> {
        return emptyList()
    }

    override fun single(): Boolean {
        return true
    }

    override fun contract(url: URL): URL {
        return url
    }

    override fun map(transform: (String?) -> String?): URL {
        return URL.of(transform(null))
    }

    override fun toString(): String {
        return ""
    }
}

/**
 * This url is single.It contain only one url.
 * Create it by [URL.of]
 */
private class SingleURL(private val url: String) : URL {
    override fun url(): String {
        return this.url
    }

    override fun urls(): List<String> {
        return listOf(this.url)
    }

    override fun single(): Boolean {
        return true
    }

    override fun contract(url: URL): URL {
        return when {
            url == URL.nil() -> this
            url.single() -> URL.of(RequestUtils.contractPath(this.url, url.url()))
            else -> URL.of(url.urls().mapNotNull { RequestUtils.contractPath(this.url, it) })
        }
    }

    override fun map(transform: (String?) -> String?): URL {
        return URL.of(transform(this.url))
    }

    override fun toString(): String {
        return url
    }
}

/**
 * Represents a multiPath url.It contain more than one url.
 * Create it by [URL.of]
 */
private class MultiURL(private val urls: List<String>) : URL {
    override fun url(): String? {
        return this.urls.first { it.notNullOrEmpty() }
    }

    override fun urls(): List<String> {
        return this.urls
    }

    override fun single(): Boolean {
        return false
    }

    override fun contract(url: URL): URL {
        return when {
            url == URL.nil() -> this
            url.single() -> URL.of(this.urls().mapNotNull { RequestUtils.contractPath(it, url.url()) })
            else -> URL.of(this.urls().flatMap { prefixPath ->
                url.urls().mapNotNull { RequestUtils.contractPath(prefixPath, it) }
            })
        }
    }

    override fun map(transform: (String?) -> String?): URL {
        return URL.of(this.urls().mapNotNull { transform(it) })
    }

    override fun toString(): String {
        return urls.joinToString(",")
    }
}