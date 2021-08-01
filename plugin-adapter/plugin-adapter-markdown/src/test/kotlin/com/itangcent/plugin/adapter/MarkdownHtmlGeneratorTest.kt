package com.itangcent.plugin.adapter

import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

/**
 * Test case of [MarkdownHtmlGenerator]
 */
internal class MarkdownHtmlGeneratorTest {

    @Test
    fun render() {

        assertNull(MarkdownHtmlGenerator().render(""))
        assertNull(MarkdownHtmlGenerator().render("\t"))

        @Language("Markdown")
        val md =
            "# Header\n\n\n## Title\n\n- first\n- second\n\n---\n\n## Second paragraph\n\n1. first\n2. second\n3. [tangcent](https://itangcent.com)\n\n---\n\n> note: this is a demo\n\n```java\nprivate class Model {\n    private String str;\n\n    public String getStr() {\n        return this.str;\n    }\n}\n```\n\n\n---\n\n|  headerA   | headerB  |\n|  ----  | ----  |\n| cellA  | cellB |\n| cellX  | cellY |"
        val html = MarkdownHtmlGenerator().render(md)
        kotlin.test.assertEquals(
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