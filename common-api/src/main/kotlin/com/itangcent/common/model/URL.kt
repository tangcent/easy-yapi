package com.itangcent.common.model

import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.http.RequestUtils
import java.util.*

/**
 * Represents a url path.
 */
interface URL {

    /**
     * One url in [URL]
     */
    fun url(): String?

    /**
     * All url in [URL] as list.
     */
    fun urls(): List<String>

    /**
     * Return true if only one url be contained at this [URL].
     */
    fun single(): Boolean

    /**
     * Concat the special url to this url.
     *
     * {""} concat {/b} ==> {/b}
     * {"/a"} concat {"/b"} ==> {"/a/b"}
     */
    fun concat(url: URL): URL

    /**
     * Union the special url
     * {""} union {"/b"} ==> {"","/b"}
     * {"/a"} union {"/b"} ==> {"/a","/b"}
     */
    fun union(url: URL): URL

    /**
     * Returns a new [URL] of applying the given [transform] function
     * to each url.
     *
     * @param transform function to transform url.
     */
    fun map(transform: (String) -> String?): URL

    /**
     * Returns a new [URL] of applying the given [transform] function
     * to each url.
     *
     * @param transform function to transform url.
     */
    fun flatMap(transform: (String) -> URL?): URL

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

    override fun concat(url: URL): URL {
        return url
    }

    override fun union(url: URL): URL {
        return url
    }

    override fun map(transform: (String) -> String?): URL {
        return URL.of(transform(""))
    }

    override fun flatMap(transform: (String) -> URL?): URL {
        return transform("") ?: URL.nil()
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

    override fun concat(url: URL): URL {
        return url.map { RequestUtils.concatPath(this.url, it) }
    }

    override fun union(url: URL): URL {
        val urls = LinkedList<String>()
        urls.add(this.url)
        urls.addAll(url.urls())
        return URL.of(urls)
    }

    override fun map(transform: (String) -> String?): URL {
        return URL.of(transform(this.url))
    }

    override fun flatMap(transform: (String) -> URL?): URL {
        return transform(url) ?: URL.nil()
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

    override fun concat(url: URL): URL {
        return this.flatMap { URL.of(it).concat(url) }
    }

    override fun map(transform: (String) -> String?): URL {
        return URL.of(this.urls().mapNotNull(transform))
    }

    override fun flatMap(transform: (String) -> URL?): URL {
        return this.urls.mapNotNull(transform).reduce { a, b -> a.union(b) }
    }

    override fun union(url: URL): URL {
        val urls = LinkedList<String>()
        urls.addAll(this.urls)
        urls.addAll(url.urls())
        return URL.of(urls)
    }

    override fun toString(): String {
        return urls.joinToString(",")
    }
}