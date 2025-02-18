package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.suv.http.HttpClientProvider
import com.itangcent.test.HttpClientProviderMockBuilder
import org.apache.http.entity.ContentType
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test case of [RemoteMarkdownRender]
 */
internal open class RemoteMarkdownRenderTest : AdvancedContextTest() {

    @Inject
    protected lateinit var markdownRender: MarkdownRender

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(MarkdownRender::class) { it.with(RemoteMarkdownRender::class) }
    }

    override fun customConfig(): String {
        return "markdown.render.server=http://www.itangcent.com/render"
    }
}

internal class NoConfigRemoteMarkdownRenderTest : RemoteMarkdownRenderTest() {

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(HttpClientProvider::class) {
            it.toInstance(
                HttpClientProviderMockBuilder.builder()
                    .url("http://www.itangcent.com/render")
                    .response(
                        content = "<h1>Header</h1>",
                        contentType = ContentType.TEXT_PLAIN
                    ).build()
            )
        }
    }

    override fun customConfig(): String {
        return ""
    }

    @Test
    fun testRender() {
        assertNull(markdownRender.render(""))
        assertNull(markdownRender.render("\t"))

        @Language("Markdown")
        val md = "# Header"
        val html = markdownRender.render(md)
        assertNull(html)
    }
}

internal class SuccessRemoteMarkdownRenderTest : RemoteMarkdownRenderTest() {

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(HttpClientProvider::class) {
            it.toInstance(
                HttpClientProviderMockBuilder.builder()
                    .url("http://www.itangcent.com/render")
                    .response(
                        content = "<h1>Header</h1>",
                        contentType = ContentType.TEXT_PLAIN
                    ).build()
            )
        }
    }

    @Test
    fun testRender() {
        assertNull(markdownRender.render(""))
        assertNull(markdownRender.render("\t"))

        @Language("Markdown")
        val md = "# Header"
        val html = markdownRender.render(md)
        assertEquals(
            "<h1>Header</h1>", html
        )
    }
}

internal class BadResponseRemoteMarkdownRenderTest : RemoteMarkdownRenderTest() {

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(HttpClientProvider::class) {
            it.toInstance(
                HttpClientProviderMockBuilder.builder()
                    .url("http://www.itangcent.com/render")
                    .response(responseCode = 404).build()
            )
        }
    }

    @Test
    fun testRender() {
        assertNull(markdownRender.render(""))
        assertNull(markdownRender.render("\t"))

        @Language("Markdown")
        val md = "# Header"
        assertNull(markdownRender.render(md))
    }
}

internal class FailedRemoteMarkdownRenderTest : RemoteMarkdownRenderTest() {

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(HttpClientProvider::class) {
            it.toInstance(
                HttpClientProviderMockBuilder.builder()
                    .url("http://www.itangcent.com/render")
                    .failed(IllegalArgumentException())
                    .build()
            )
        }
    }

    @Test
    fun testRender() {
        assertNull(markdownRender.render(""))
        assertNull(markdownRender.render("\t"))

        @Language("Markdown")
        val md = "# Header"
        assertNull(markdownRender.render(md))
    }
}

internal class OutburstRemoteMarkdownRenderTest : RemoteMarkdownRenderTest() {

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(HttpClientProvider::class) {
            it.toInstance(
                HttpClientProviderMockBuilder.builder()
                    .url("http://www.itangcent.com/render")
                    .response(
                        content = "<h1>Header</h1>",
                        contentType = ContentType.TEXT_PLAIN,
                        elapse = 3000
                    )
                    .currentTotalLimit(4, IllegalArgumentException("limited!"))
                    .build()
            )
        }
    }

    @Test
    fun testRender() {
        assertNull(markdownRender.render(""))
        assertNull(markdownRender.render("\t"))

        val countDownLatch = CountDownLatch(7)
        val cyclicBarrier = CyclicBarrier(7)
        val failed = AtomicInteger()

        @Language("Markdown")
        val md = "# Header"
        for (i in 1..7) {
            Thread {
                try {
                    cyclicBarrier.await()
                    val html = markdownRender.render(md)
                    if (html == null) {
                        failed.incrementAndGet()
                    } else {
                        assertEquals(
                            "<h1>Header</h1>", html
                        )
                    }
                } finally {
                    countDownLatch.countDown()
                }
            }.start()
        }
        countDownLatch.await()
        assertEquals(3, failed.get())
    }
}

internal class SafeRemoteMarkdownRenderTest : RemoteMarkdownRenderTest() {

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(HttpClientProvider::class) {
            it.toInstance(
                HttpClientProviderMockBuilder.builder()
                    .url("http://www.itangcent.com/render")
                    .response(
                        content = "<h1>Header</h1>",
                        contentType = ContentType.TEXT_PLAIN,
                        elapse = 3000
                    )
                    .currentTotalLimit(7, IllegalArgumentException("limited!"))
                    .build()
            )
        }
    }

    @Test
    fun testRender() {
        assertNull(markdownRender.render(""))
        assertNull(markdownRender.render("\t"))

        val countDownLatch = CountDownLatch(10)
        for (i in 0..10) {
            Thread {
                try {
                    @Language("Markdown")
                    val md = "# Header"
                    val html = markdownRender.render(md)
                    assertEquals(
                        "<h1>Header</h1>", html
                    )
                } finally {
                    countDownLatch.countDown()
                }
            }.start()
        }
        countDownLatch.await()
    }
}