package com.itangcent.idea.utils

import com.google.inject.Inject
import com.itangcent.testFramework.ContextLightCodeInsightFixtureTestCase

/**
 * Test case for [FormatterHelper]
 *
 * @author tangcent
 * @date 2024/03/31
 */
class FormatterHelperTest : ContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var formatterHelper: FormatterHelper

    fun testFormatJson() {
        assertEquals(
            """
                {
                  "name": "tangcent",
                  "age": 18
                }
            """.trimIndent(),
            formatterHelper.formatJson(
                """
                    {"name": "tangcent",                        "age": 18                    }
                """.trimIndent()
            )
        )
    }

    fun testFormatJsonWithInvalidInput() {
        assertEquals(
            "invalid json",
            formatterHelper.formatJson("invalid json")
        )
    }

    fun testFormatHtml() {
        assertEquals(
            """
                <html>
                <head><title>Example</title></head>
                <body><p>Hello, world!</p></body>
                </html>
            """.trimIndent(),
            formatterHelper.formatHtml(
                """
                    <html><head><title>Example</title></head><body><p>Hello, world!</p></body></html>
                """.trimIndent()
            )
        )
    }

    fun testFormatHtmlWithInvalidInput() {
        assertEquals(
            """
                <html>
                <head><title>Missing closing tags
            """.trimIndent(),
            formatterHelper.formatHtml("<html><head><title>Missing closing tags")
        )
    }

    fun testFormatXml() {
        assertEquals(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <note>
                    <to>Tove</to>
                    <from>Jani</from>
                    <heading>Reminder</heading>
                    <body>Don't forget me this weekend!</body>
                </note>
            """.trimIndent(),
            formatterHelper.formatXml(
                """
                    <?xml version="1.0" encoding="UTF-8"?><note><to>Tove</to><from>Jani</from><heading>Reminder</heading><body>Don't forget me this weekend!</body></note>
                """.trimIndent()
            )
        )
    }

    fun testFormatXmlWithInvalidInput() {
        assertEquals(
            """
                <?xml version="1.0" encoding="UTF-8"?>
                <note><to>Tove<from>Jani
                </note>
            """.trimIndent(),
            formatterHelper.formatXml("<?xml version=\"1.0\" encoding=\"UTF-8\"?><note><to>Tove<from>Jani</note>")
        )
    }

    fun testFormatUnknownType() {
        assertEquals(
            "unknown type",
            formatterHelper.formatText("unknown type", "unknown")
        )
    }
}