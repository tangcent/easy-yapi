package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.traceError
import com.itangcent.idea.plugin.api.export.StringResponseHandler
import com.itangcent.idea.plugin.api.export.reserved
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.logger.Logger
import com.itangcent.suv.http.HttpClientProvider
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
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
        val httpPost = HttpPost(server)

        httpPost.entity = StringEntity(markdown, Charsets.UTF_8)

        try {
            val result = httpClientProvide!!.getHttpClient().execute(httpPost,
                    StringResponseHandler.DEFAULT_RESPONSE_HANDLER.reserved())
            if (result.status() == 200) {
                return result.result()
            }
            logger!!.warn(" try render markdown with $server,but response code is ${result.status()}, response is:${result.result()}")
        } catch (e: Exception) {
            logger!!.traceError(" try render markdown with $server failed", e)
        }

        return null
    }
}