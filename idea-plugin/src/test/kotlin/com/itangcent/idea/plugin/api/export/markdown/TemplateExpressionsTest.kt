package com.itangcent.idea.plugin.api.export.markdown

import com.itangcent.common.model.Header
import com.itangcent.common.model.Request
import com.itangcent.common.model.Response
import com.itangcent.mock.BaseContextTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [TemplateExpression]
 */
class TemplateExpressionsTest : BaseContextTest() {

    @Test
    fun testPlainText() {
        val expression = PlainText("Hello World")
        val request = Request()
        val result = StringBuilder()
        expression.eval(request, result)
        assertEquals("Hello World", result.toString())
    }

    @Test
    fun testRequestProperty() {
        val request = Request().apply {
            name = "Test API"
            method = "GET"
        }
        val result = StringBuilder()

        // Test existing property
        RequestProperty("name").eval(request, result)
        assertEquals("Test API", result.toString())

        // Test non-existing property
        result.clear()
        RequestProperty("nonExisting").eval(request, result)
        assertEquals("", result.toString())
    }

    @Test
    fun testResponseProperty() {
        val request = Request().apply {
            response = mutableListOf(Response().apply {
                code = 200
                body = "Success"
            })
        }
        val result = StringBuilder()

        // Test existing property
        ResponseProperty("code").eval(request, result)
        assertEquals("200", result.toString())

        // Test non-existing property
        result.clear()
        ResponseProperty("nonExisting").eval(request, result)
        assertEquals("", result.toString())
    }

    @Test
    fun testMDTableExpression() {
        val rows = listOf(
            Header("content-type", "application/json"),
            Header("Authorization", "Bearer token123")
        )
        val titles = listOf(
            "name" to "Header Name",
            "value" to "Header Value"
        )
        val expression = MDTableExpression(rows, titles)
        val request = Request()
        val result = StringBuilder()

        expression.eval(request, result)
        val expected = """
            | Header Name | Header Value |
            |------|------|
            | content-type | application/json |
            | Authorization | Bearer token123 |
        """.trimIndent()
        assertEquals(expected, result.toString().trim())
    }

    @Test
    fun testConditionalExpression() {
        val request = Request().apply {
            method = "POST"
        }
        val result = StringBuilder()

        // Test true condition
        val condition = SinglePropertyCondition("method")
        val expression = ConditionalExpression(condition, PlainText("POST Request"))
        expression.eval(request, result)
        assertEquals("POST Request", result.toString())

        // Test false condition
        result.clear()
        val falseCondition = SinglePropertyCondition("nonExisting")
        val falseExpression = ConditionalExpression(falseCondition, PlainText("Should not appear"))
        falseExpression.eval(request, result)
        assertEquals("", result.toString())
    }

    @Test
    fun testParseTemplate() {
        val template = "Hello \${name}, your method is \${method}"
        val request = Request().apply {
            name = "John"
            method = "GET"
        }
        val result = StringBuilder()

        val expression = TemplateExpression.parseTemplate(template)
        expression.eval(request, result)
        assertEquals("Hello John, your method is GET", result.toString())
    }

    @Test
    fun testParseTemplateWithCondition() {
        val template = "\${if method}This is a \${method} request\${end}"

        val request = Request().apply {
            method = "POST"
        }
        val result = StringBuilder()

        val expression = TemplateExpression.parseTemplate(template)
        expression.eval(request, result)
        assertEquals("This is a POST request", result.toString().trim())
    }

    @Test
    fun testMDObjectExpression() {
        val value = mapOf(
            "name" to "Test Object",
            "age" to 42,
            "@comment" to mapOf<String, String>(
                "name" to "This is a name",
                "age" to "This is an age"
            )
        )
        val titles = mapOf(
            "name" to "Name",
            "type" to "Type"
        )
        val expression = MDObjectExpression(value, titles)
        val request = Request()
        val result = StringBuilder()

        expression.eval(request, result)
        val expected = """
| Name | Type |
|------|------|
| name | string |
| age | integer |
        """.trimIndent()
        assertEquals(expected, result.toString().trim())
    }

    @Test
    fun testMDHelperTable() {
        val request = Request()
        val result = StringBuilder()

        // Test table helper
        val tableExpression = MD.table(
            listOf(
                Header("content-type", "application/json"),
                Header("Authorization", "Bearer token123"),
            )
        ).title(
            mapOf(
                "name" to "Header Name",
                "value" to "Header Value"
            )
        )
        tableExpression.eval(request, result)
        val expectedTable = """
| Header Name | Header Value |
|------|------|
| content-type | application/json |
| Authorization | Bearer token123 |
        """.trimIndent()
        assertEquals(expectedTable, result.toString().trim())
    }

    @Test
    fun testMDHelperJson() {
        val request = Request()
        val result = StringBuilder()
        val value = mapOf(
            "name" to "Test Object",
            "age" to 42,
            "@comment" to mapOf<String, String>(
                "name" to "This is a name",
                "age" to "This is an age"
            )
        )

        // Test json helper
        val tableExpression = MD.json(value)
        tableExpression.eval(request, result)
        val expectedTable = """
{
  "name": "Test Object",
  "age": 42
}
        """.trimIndent()
        assertEquals(expectedTable, result.toString().trim())
    }

    @Test
    fun testMDHelperJson5() {
        val request = Request()
        val result = StringBuilder()
        val value = mapOf(
            "name" to "Test Object",
            "age" to 42,
            "@comment" to mapOf<String, String>(
                "name" to "This is a name",
                "age" to "This is an age"
            )
        )

        // Test json5 helper
        val json5Expression = MD.json5(value)
        json5Expression.eval(request, result)

        // Verify output contains proper JSON5 formatting with comments
        val expectedJson5 = """
{
    "name": "Test Object", //This is a name
    "age": 42 //This is an age
}
        """.trimIndent()
        assertEquals(expectedJson5, result.toString().trim())
    }

    @Test
    fun testPropertyAccessPatterns() {
        val request = Request().apply {
            name = "Test API"
            method = "GET"
            response = mutableListOf(Response().apply {
                code = 200
                body = "Success"
            })
        }
        val result = StringBuilder()

        // Test simple property access ($xx)
        val simpleExpression = TemplateExpression.parseTemplate("\$name")
        result.clear()
        simpleExpression.eval(request, result)
        assertEquals("Test API", result.toString())

        // Test nested property access ($xx.xx)
        val nestedExpression = TemplateExpression.parseTemplate("\$response.code")
        result.clear()
        nestedExpression.eval(request, result)
        assertEquals("200", result.toString())

        // Test special response property access ($response.xx)
        val responseExpression = TemplateExpression.parseTemplate("\$response.body")
        result.clear()
        responseExpression.eval(request, result)
        assertEquals("Success", result.toString())
    }

    @Test
    fun testContextAttributeAssignment() {
        val request = Request()
        val result = StringBuilder()

        // Test that we can set context attributes using ${@xx=yy} syntax
        val expression = TemplateExpression.parseTemplate(
            "\${@text.boolean.true=YES}\n" +
                    "\${@text.boolean.false=NO}\n" +
                    "\${text.boolean.true} \${text.boolean.false}"
        )
        expression.eval(request, result)

        assertEquals("YES NO", result.toString().trim())

        // Test with different values
        result.clear()
        val expression2 = TemplateExpression.parseTemplate(
            "\${@text.boolean.true=真}\n" +
                    "\${@text.boolean.false=假}\n" +
                    "\${text.boolean.true} \${text.boolean.false}"
        )
        expression2.eval(request, result)

        assertEquals("真 假", result.toString().trim())
    }

    @Test
    fun testBooleanFormattingInTable() {
        val request = Request()
        request.headers = mutableListOf(
            Header().apply {
                name = "content-type"
                value = "application/json"
                required = true
            },
            Header().apply {
                name = "Authorization"
                value = "Bearer token123"
                required = false
            }
        )
        val result = StringBuilder()

        // Test default boolean formatting (should be "YES" and "NO")
        val expression = TemplateExpression.parseTemplate(
            "\${md.table(headers).title([name:\"Name\", value:\"Value\", required:\"Required\"])}"
        )
        expression.eval(request, result)

        assertEquals(
            """
            | Name | Value | Required |
            |------|------|------|
            | content-type | application/json | true |
            | Authorization | Bearer token123 | false |
        """.trimIndent(), result.toString().trim()
        )

        // Test custom boolean formatting
        result.clear()
        val customExpression = TemplateExpression.parseTemplate(
            "\${@text.boolean.true=YES}\n" +
                    "\${@text.boolean.false=NO}\n" +
                    "\${md.table(headers).title([name:\"Name\", value:\"Value\", required:\"Required\"])}"
        )
        customExpression.eval(request, result)

        assertEquals(
            """
            | Name | Value | Required |
            |------|------|------|
            | content-type | application/json | YES |
            | Authorization | Bearer token123 | NO |
        """.trimIndent(), result.toString().trim()
        )
    }
} 