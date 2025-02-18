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
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test case of [AdaptiveMarkdownRender]
 */
internal abstract class AdaptiveMarkdownRenderTest : AdvancedContextTest() {

    @Inject
    protected lateinit var markdownRender: MarkdownRender

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(MarkdownRender::class) { it.with(AdaptiveMarkdownRender::class) }
    }

    @Test
    fun testRender() {
        val countDownLatch = CountDownLatch(10)
        val executorService = Executors.newFixedThreadPool(5)
        for (i in 0..10) {
            executorService.submit {
                try {
                    doTestRender()
                } finally {
                    countDownLatch.countDown()
                }
            }
        }
        countDownLatch.await()
    }

    abstract fun doTestRender()

    class AdaptiveBundledMarkdownRenderTest : AdaptiveMarkdownRenderTest() {

        override fun doTestRender() {
            assertNull(markdownRender.render(""))
            assertNull(markdownRender.render("\t"))

            @Language("Markdown")
            val md =
                    "# Header\n\n\n## Title\n\n- first\n- second\n\n---\n\n## Second paragraph\n\n1. first\n2. second\n3. [tangcent](https://itangcent.com)\n\n---\n\n> note: this is a demo\n\n```java\nprivate class Model {\n    private String str;\n\n    public String getStr() {\n        return this.str;\n    }\n}\n```\n\n\n---\n\n|  headerA   | headerB  |\n|  ----  | ----  |\n| cellA  | cellB |\n| cellX  | cellY |"
            val html = markdownRender.render(md)
            assertEquals("<h1>Header</h1><h2>Title</h2><ul><li>first</li><li>second</li></ul><hr /><h2>Second paragraph</h2><ol><li>first</li><li>second</li><li><a href=\"https://itangcent.com\">tangcent</a></li></ol><hr /><blockquote><p>note: this is a demo</p></blockquote><pre><code class=\"language-java\">private class Model {\n" +
                    "    private String str;\n" +
                    "\n" +
                    "    public String getStr() {\n" +
                    "        return this.str;\n" +
                    "    }\n" +
                    "}\n" +
                    "</code></pre><hr /><p>|  headerA   | headerB  |\n" +
                    "|  ----  | ----  |\n" +
                    "| cellA  | cellB |\n" +
                    "| cellX  | cellY |</p>", html)
        }
    }

    class AdaptiveShellOnlyMarkdownRenderTest : AdaptiveMarkdownRenderTest() {

        override fun customConfig(): String {
            return "markdown.render.shell=```\n" +
                    "cp #fileName #target\n" +
                    "```\n" +
                    "markdown.render.timeout=3000"
        }

        override fun doTestRender() {
            assertNull(markdownRender.render(""))
            assertNull(markdownRender.render("\t"))

            @Language("Markdown")
            val md =
                    "# Header\n\n\n## Title\n\n- first\n- second\n\n---\n\n## Second paragraph\n\n1. first\n2. second\n3. [tangcent](https://itangcent.com)\n\n---\n\n> note: this is a demo\n\n```java\nprivate class Model {\n    private String str;\n\n    public String getStr() {\n        return this.str;\n    }\n}\n```\n\n\n---\n\n|  headerA   | headerB  |\n|  ----  | ----  |\n| cellA  | cellB |\n| cellX  | cellY |"
            val html = markdownRender.render(md)
            assertEquals(md, html)
        }
    }

    class AdaptiveRemoteOnlyMarkdownRenderTest : AdaptiveMarkdownRenderTest() {

        override fun customConfig(): String {
            return "markdown.render.server=http://www.itangcent.com/render"
        }

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)
            builder.bind(HttpClientProvider::class) {
                it.toInstance(HttpClientProviderMockBuilder.builder()
                        .url("http://www.itangcent.com/render")
                        .response(content = "<h1>Header</h1>",
                                contentType = ContentType.TEXT_PLAIN).build())
            }
        }

        override fun doTestRender() {
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

    class UnavailableRemoteMarkdownRenderTest : AdaptiveMarkdownRenderTest() {

        override fun customConfig(): String {
            return "markdown.render.server=http://www.itangcent.com/render"
        }

        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)
            builder.bind(HttpClientProvider::class) {
                it.toInstance(HttpClientProviderMockBuilder.builder()
                        .url("http://www.itangcent.com/render")
                        .failed(IllegalArgumentException())
                        .build())
            }
        }

        override fun doTestRender() {
            assertNull(markdownRender.render(""))
            assertNull(markdownRender.render("\t"))

            assertEquals("<h1>header</h1>", markdownRender.render("# header"))
            @Language("Markdown")
            val md =
                    "# Header\n\n\n## Title\n\n- first\n- second\n\n---\n\n## Second paragraph\n\n1. first\n2. second\n3. [tangcent](https://itangcent.com)\n\n---\n\n> note: this is a demo\n\n```java\nprivate class Model {\n    private String str;\n\n    public String getStr() {\n        return this.str;\n    }\n}\n```\n\n\n---\n\n|  headerA   | headerB  |\n|  ----  | ----  |\n| cellA  | cellB |\n| cellX  | cellY |"
            val html = markdownRender.render(md)
            assertEquals(
                    "<h1>Header</h1><h2>Title</h2><ul><li>first</li><li>second</li></ul><hr /><h2>Second paragraph</h2><ol><li>first</li><li>second</li><li><a href=\"https://itangcent.com\">tangcent</a></li></ol><hr /><blockquote><p>note: this is a demo</p></blockquote><pre><code class=\"language-java\">private class Model {\n" +
                            "    private String str;\n" +
                            "\n" +
                            "    public String getStr() {\n" +
                            "        return this.str;\n" +
                            "    }\n" +
                            "}\n" +
                            "</code></pre><hr /><p>|  headerA   | headerB  |\n" +
                            "|  ----  | ----  |\n" +
                            "| cellA  | cellB |\n" +
                            "| cellX  | cellY |</p>", html
            )
        }
    }

}