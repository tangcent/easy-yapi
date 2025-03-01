package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.BaseContextTest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test case of [BundledMarkdownRender]
 */
internal class BundledMarkdownRenderTest : BaseContextTest() {

    @Inject
    private lateinit var markdownRender: MarkdownRender

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(MarkdownRender::class) { it.with(BundledMarkdownRender::class) }
    }

    @Test
    fun testRender() {
        assertNull(markdownRender.render(""))
        assertNull(markdownRender.render("\t"))

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

    @Test
    fun testRenderNonEnglishContent() {
        @Language("Markdown")
        val mdNonEnglish =
            """
        # Überschrift
        ## Titel
        - Erster
        - Zweiter
        
        ---
        
        ## Второй абзац
        1. первый
        2. второй
        
        ---
        
        > 注意：这是一个演示
        
        ```java
        public class Beispiel {
            private String zeichen;
        
            public String getZeichen() {
                return this.zeichen;
            }
        }
        ```
        
        ---
        
        | заголовокA | заголовокB |
        | ------ | ------ |
        | ячейкаA | ячейкаB |
        """

        val htmlNonEnglish = markdownRender.render(mdNonEnglish)
        assertEquals(
            "<pre><code>    # Überschrift\n" +
                    "    ## Titel\n" +
                    "    - Erster\n" +
                    "    - Zweiter\n" +
                    "    \n" +
                    "    ---\n" +
                    "    \n" +
                    "    ## Второй абзац\n" +
                    "    1. первый\n" +
                    "    2. второй\n" +
                    "    \n" +
                    "    ---\n" +
                    "    \n" +
                    "    &gt; 注意：这是一个演示\n" +
                    "    \n" +
                    "    ```java\n" +
                    "    public class Beispiel {\n" +
                    "        private String zeichen;\n" +
                    "    \n" +
                    "        public String getZeichen() {\n" +
                    "            return this.zeichen;\n" +
                    "        }\n" +
                    "    }\n" +
                    "    ```\n" +
                    "    \n" +
                    "    ---\n" +
                    "    \n" +
                    "    | заголовокA | заголовокB |\n" +
                    "    | ------ | ------ |\n" +
                    "    | ячейкаA | ячейкаB |\n" +
                    "</code></pre>", htmlNonEnglish
        )
    }
}