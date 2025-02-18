package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.AdvancedContextTest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test case of [ConfigurableShellFileMarkdownRender]
 */
internal open class ConfigurableShellFileMarkdownRenderTest : AdvancedContextTest() {

    @Inject
    internal lateinit var markdownRender: MarkdownRender

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(MarkdownRender::class) { it.with(ConfigurableShellFileMarkdownRender::class) }
    }

    internal open class NoConfigConfigurableShellFileMarkdownRenderTest : ConfigurableShellFileMarkdownRenderTest() {

        override fun customConfig(): String {
            return ""
        }

        @Test
        fun testRender() {
            assertNull(markdownRender.render(""))
            assertNull(markdownRender.render("\t"))

            @Language("Markdown")
            val md =
                    "# Header\n\n\n## Title\n\n- first\n- second\n\n---\n\n## Second paragraph\n\n1. first\n2. second\n3. [tangcent](https://itangcent.com)\n\n---\n\n> note: this is a demo\n\n```java\nprivate class Model {\n    private String str;\n\n    public String getStr() {\n        return this.str;\n    }\n}\n```\n\n\n---\n\n|  headerA   | headerB  |\n|  ----  | ----  |\n| cellA  | cellB |\n| cellX  | cellY |"
            assertNull(markdownRender.render(md))
        }
    }

    internal open class NopConfigConfigurableShellFileMarkdownRenderTest : ConfigurableShellFileMarkdownRenderTest() {

        override fun customConfig(): String {
            return "markdown.render.shell=```\n" +
                    "echo #fileName #target\n" +
                    "```\n" +
                    "markdown.render.timeout=3000"
        }

        @Test
        fun testRender() {
            assertNull(markdownRender.render(""))
            assertNull(markdownRender.render("\t"))

            @Language("Markdown")
            val md =
                    "# Header\n\n\n## Title\n\n- first\n- second\n\n---\n\n## Second paragraph\n\n1. first\n2. second\n3. [tangcent](https://itangcent.com)\n\n---\n\n> note: this is a demo\n\n```java\nprivate class Model {\n    private String str;\n\n    public String getStr() {\n        return this.str;\n    }\n}\n```\n\n\n---\n\n|  headerA   | headerB  |\n|  ----  | ----  |\n| cellA  | cellB |\n| cellX  | cellY |"
            assertNull(markdownRender.render(md))
        }
    }

    @EnabledOnOs(value = [OS.LINUX, OS.MAC], disabledReason = "Only for Linux and Mac")
    internal open class SuccessConfigurableShellFileMarkdownRenderTest : ConfigurableShellFileMarkdownRenderTest() {

        override fun customConfig(): String {
            return "markdown.render.shell=```\n" +
                    "cp #fileName #target\n" +
                    "```\n" +
                    "markdown.render.timeout=3000"
        }

        @Test
        fun testRender() {
            assertNull(markdownRender.render(""))
            assertNull(markdownRender.render("\t"))

            @Language("Markdown")
            val md =
                    "# Header\n\n\n## Title\n\n- first\n- second\n\n---\n\n## Second paragraph\n\n1. first\n2. second\n3. [tangcent](https://itangcent.com)\n\n---\n\n> note: this is a demo\n\n```java\nprivate class Model {\n    private String str;\n\n    public String getStr() {\n        return this.str;\n    }\n}\n```\n\n\n---\n\n|  headerA   | headerB  |\n|  ----  | ----  |\n| cellA  | cellB |\n| cellX  | cellY |"
            val html = markdownRender.render(md)
            assertEquals(md, html)
        }
    }

    internal open class FailedConfigurableShellFileMarkdownRenderTest : ConfigurableShellFileMarkdownRenderTest() {

        override fun customConfig(): String {
            return "markdown.render.shell=```\n" +
                    "exit 1\n" +
                    "```\n" +
                    "markdown.render.timeout=3000"
        }

        @Test
        fun testRender() {
            assertNull(markdownRender.render(""))
            assertNull(markdownRender.render("\t"))

            @Language("Markdown")
            val md =
                    "# Header\n\n\n## Title\n\n- first\n- second\n\n---\n\n## Second paragraph\n\n1. first\n2. second\n3. [tangcent](https://itangcent.com)\n\n---\n\n> note: this is a demo\n\n```java\nprivate class Model {\n    private String str;\n\n    public String getStr() {\n        return this.str;\n    }\n}\n```\n\n\n---\n\n|  headerA   | headerB  |\n|  ----  | ----  |\n| cellA  | cellB |\n| cellX  | cellY |"
            assertNull(markdownRender.render(md))
        }
    }
}