package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.traceError
import com.itangcent.http.contentType
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.logger.Logger
import com.itangcent.suv.http.HttpClientProvider
import org.apache.http.entity.ContentType
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger

/**
 * render `markdown` from `http`
 *
 * config example:
 * markdown.render.server=http://fake.com/render
 *
 * see https://github.com/easyyapi/yapi-markdown-render
 */
@Singleton
open class RemoteMarkdownRender : MarkdownRender {

    @Inject
    protected val httpClientProvide: HttpClientProvider? = null

    @Inject
    protected val configReader: ConfigReader? = null

    @Inject
    protected val logger: Logger? = null

    private val semaphore = Semaphore(3)

    private val cnt = AtomicInteger(0)

    override fun render(markdown: String): String? {
        return try {
            if (cnt.incrementAndGet() > 2) {
                semaphore.acquire()
                try {
                    doRender(markdown)
                } finally {
                    semaphore.release()
                }
            } else {
                doRender(markdown)
            }
        } finally {
            cnt.decrementAndGet()
        }
    }

    protected fun doRender(markdown: String): String? {

        val server = configReader!!.first("markdown.render.server") ?: return null
        try {
            val httpResponse = httpClientProvide!!.getHttpClient().post(server)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body(markdown)
                    .call()
            if (httpResponse.code() == 200) {
                return httpResponse.string()
            }
            logger!!.warn(" try render markdown with $server,but response code is ${httpResponse.code()}, response is:${httpResponse.string()}")
        } catch (e: Exception) {
            logger!!.traceError(" try render markdown with $server failed", e)
        }

        return null
    }
}